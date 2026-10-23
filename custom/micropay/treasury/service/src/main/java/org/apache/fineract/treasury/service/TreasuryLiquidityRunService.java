/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.treasury.service;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryRepricingData;
import org.apache.fineract.treasury.data.TreasuryReturnData;
import org.apache.fineract.treasury.data.TreasuryRunData;
import org.apache.fineract.treasury.data.TreasuryStressData;
import org.apache.fineract.treasury.domain.TreasuryAssumptionSet;
import org.apache.fineract.treasury.domain.TreasuryRuleSet;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunLine;
import org.apache.fineract.treasury.domain.TreasuryRunLineRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
import org.apache.fineract.treasury.domain.TreasuryScenario;
import org.apache.fineract.treasury.domain.TreasurySegregation;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.liquidity.Assumptions;
import org.apache.fineract.treasury.liquidity.Bucket;
import org.apache.fineract.treasury.liquidity.ConfigStatus;
import org.apache.fineract.treasury.liquidity.LadderBuilder;
import org.apache.fineract.treasury.liquidity.LadderContext;
import org.apache.fineract.treasury.liquidity.LadderFlow;
import org.apache.fineract.treasury.liquidity.LadderSource;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.liquidity.RunType;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator.RunRequest;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Liquidity runs, stored with the rule set and assumption set versions they used.
 *
 * <ul>
 * <li>LADDER: every {@link LadderSource}'s flows as of a date, in a currency, for an office or the whole institution,
 * bucketed with the rule set's buckets and the assumptions.</li>
 * <li>RATIOS: the rule set's ratios from a ladder run (the one given, else the scope's, else a new one) and the GL;
 * each ratio with its components, exclusions, haircuts and status, and the configuration used as a snapshot.</li>
 * <li>RETURN: the regulatory return's lines from a ratios run (given, the scope's, or a new one).</li>
 * <li>STRESS: an active scenario's shocks over a ladder run (given, the scope's, or a new one), with the capacity and
 * the survival horizon ({@link TreasuryStressRunService}); one per scenario in a scope.</li>
 * </ul>
 *
 * <p>
 * A run for a type, date, currency and scope replaces the earlier one of that type and what was computed from it,
 * unless a return of the scope has been SUBMITTED (with {@code ?command=submit}): the scope's runs are then locked. The
 * "Treasury Liquidity Run" job runs the ladder, the ratios and every active scenario of every organisation currency for
 * the business date. Each ladder and ratios run is followed by an evaluation of the scope's active BUCKET_GAP or RATIO
 * limits ({@link TreasuryLimitEngine}).
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryLiquidityRunService {

    static final String ENTITY = "run";
    static final String COMPLETED = "COMPLETED";
    static final String SUBMITTED = "SUBMITTED";
    private static final String UNIQUE_SCOPE = "uq_treasury_run_scope";

    private final TreasuryLiquidityDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryRuleSetService ruleSetService;
    private final TreasuryAssumptionSetService assumptionSetService;
    private final TreasuryRunRepository runRepository;
    private final TreasuryRunLineRepository lineRepository;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final List<LadderSource> sources;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AppUserRepositoryWrapper appUserRepository;
    private final PlatformTransactionManager transactionManager;
    private final TreasuryRatioRunService ratioRunService;
    private final TreasuryLimitEngine limitEngine;
    private final TreasuryStressRunService stressRunService;
    private final TreasuryScenarioService scenarioService;
    private final TreasuryCfpService cfpService;
    private final TreasuryRepricingRunService repricingRunService;

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final RunRequest request = withSourceScope(this.validator.validateRun(command.json()));
        final LocalDate asOf = request.asOfDate() == null ? DateUtils.getBusinessLocalDate() : request.asOfDate();
        if (DateUtils.isAfterBusinessDate(asOf)) {
            throw new TreasuryWorkflowException(ENTITY, "future.date", "The as-of date " + asOf + " is after the business date", asOf);
        }
        if (!this.referenceDataLookup.isOrganisationCurrency(request.currencyCode())) {
            throw new TreasuryWorkflowException(ENTITY, "currency.invalid", request.currencyCode() + " is not an organisation currency",
                    request.currencyCode());
        }
        if (request.officeId() != null && !this.referenceDataLookup.officeExists(request.officeId())) {
            throw new TreasuryWorkflowException(ENTITY, "office.not.found", "Office " + request.officeId() + " does not exist",
                    request.officeId());
        }
        final Long userId = this.actor.userId(command);
        final TreasuryRun run = switch (request.type()) {
            case LADDER -> ladder(asOf, request.currencyCode(), request.officeId(), ruleSet(request.ruleSetId()),
                    assumptionSet(request.assumptionSetId()), userId);
            case RATIOS -> ratios(ladderFor(request, asOf, userId), userId);
            case RETURN -> returnRun(ratiosFor(request, asOf, userId), userId);
            case STRESS -> stress(ladderFor(request, asOf, userId), activeScenario(request.scenarioId()), userId);
            case REPRICING -> repricing(request, asOf, userId);
        };
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("type", run.getRunType().name());
        changes.put("asOfDate", run.getAsOfDate());
        final TreasuryRuleSet ruleSet = this.ruleSetService.find(run.getRuleSetId());
        changes.put("ruleSet", ruleSet.getCode() + " v" + ruleSet.getVersion());
        changes.put("assumptionSetVersion", run.getAssumptionSetVersion());
        if (run.getSourceRunId() != null) {
            changes.put("sourceRunId", run.getSourceRunId());
        }
        if (run.getNotes() != null) {
            changes.put("notes", run.getNotes());
        }
        if (run.getRunType() == RunType.REPRICING) {
            changes.put("totalAssets", run.getTotalAssets());
            changes.put("shocks", request.shocksBp());
        }
        if (run.getRunType() == RunType.STRESS) {
            changes.put("scenario", run.getScenarioCode() + " v" + run.getScenarioVersion());
            changes.put("survivalDays", run.getSurvivalDays());
            changes.put("beyondHorizon", run.isSurvivalBeyondHorizon());
        }
        this.limitEngine.latestFor(run.getId()).ifPresent(e -> {
            changes.put("limitEvaluationId", e.getId());
            changes.put("limitBreaches", e.getBreachesCreated() + e.getBreachesEscalated() + e.getBreachesRepeated());
        });
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(run.getId()).with(changes).build();
    }

    /** With a source run and no currency, the source run's date, currency and office. */
    private RunRequest withSourceScope(final RunRequest request) {
        if (request.sourceRunId() == null || request.currencyCode() != null) {
            return request;
        }
        final TreasuryRun source = find(request.sourceRunId());
        return new RunRequest(request.type(), request.asOfDate() == null ? source.getAsOfDate() : request.asOfDate(),
                source.getCurrencyCode(), request.officeId() == null ? source.getOfficeId() : request.officeId(), request.ruleSetId(),
                request.assumptionSetId(), request.sourceRunId(), request.scenarioId(), request.shocksBp());
    }

    /** Submits a return: it and every run of its scope are then locked. Not by who made the return. */
    @Transactional
    public CommandProcessingResult submit(final Long runId, final JsonCommand command) {
        this.validator.validateActivate(command.json());
        final TreasuryRun run = find(runId);
        if (run.getRunType() != RunType.RETURN || !COMPLETED.equals(run.getStatus())) {
            throw new TreasuryWorkflowException(ENTITY, "not.submittable",
                    "Only a completed return can be submitted; run " + runId + " is a " + run.getStatus() + " " + run.getRunType(), runId);
        }
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(ENTITY, runId, "made", run.getCreatedBy(), "submit", userId);
        run.setStatus(SUBMITTED);
        run.setSubmittedBy(userId);
        run.setSubmittedOnDate(DateUtils.getBusinessLocalDate());
        save(run);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("status", SUBMITTED);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(runId).with(changes).build();
    }

    /** The job: every organisation currency, the whole institution, the business date; the ladder, then the ratios. */
    public int runForBusinessDate() {
        final LocalDate asOf = DateUtils.getBusinessLocalDate();
        final TransactionTemplate template = new TransactionTemplate(this.transactionManager);
        // Each scope in its own transaction, so that one that fails is rolled back alone and not the job's
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        final Long systemUserId = template.execute(status -> this.appUserRepository.fetchSystemUser().getId());
        int runs = 0;
        for (final String currency : this.referenceDataLookup.organisationCurrencies()) {
            try {
                template.execute(status -> {
                    final TreasuryRun ladder = ladder(asOf, currency, null, this.ruleSetService.defaultRuleSet(),
                            this.assumptionSetService.activeSet(), systemUserId);
                    if (this.ratioRunService.hasRatios(ladder.getRuleSetId())) {
                        ratios(ladder, systemUserId);
                    }
                    for (final TreasuryScenario scenario : this.scenarioService.active()) {
                        stress(ladder, scenario, systemUserId);
                    }
                    repricing(asOf, currency, null, this.ruleSetService.find(ladder.getRuleSetId()), this.assumptionSetService.activeSet(),
                            ladder, TreasuryRepricingRunService.DEFAULT_SHOCKS, systemUserId);
                    return ladder;
                });
                runs++;
            } catch (RuntimeException e) {
                log.error("Treasury liquidity run for {} on {} failed: {}", currency, asOf, e.getMessage());
            }
        }
        log.info("Treasury liquidity run for {}: {} currencies", asOf, runs);
        return runs;
    }

    TreasuryRun ladder(final LocalDate asOf, final String currency, final Long officeId, final TreasuryRuleSet ruleSet,
            final Optional<TreasuryAssumptionSet> assumptionSet, final Long userId) {
        final List<Bucket> buckets = this.ruleSetService.buckets(ruleSet.getId());
        final Assumptions assumptions = assumptionSet.map(s -> this.assumptionSetService.assumptions(s.getId())).orElse(Assumptions.NONE);
        final LadderContext context = new LadderContext(asOf, currency, officeId, buckets, assumptions,
                this.referenceDataLookup.decimalPlaces(currency));
        final List<LadderFlow> flows = new ArrayList<>();
        for (final LadderSource source : this.sources) {
            flows.addAll(source.flows(context));
        }
        final LadderBuilder.Ladder ladder = LadderBuilder.build(context, flows);
        replace(RunType.LADDER, asOf, currency, officeId);
        final List<String> notes = new ArrayList<>(context.warnings());
        if (assumptionSet.isEmpty()) {
            notes.add("No active assumption set: contractual flows only");
        }
        final TreasuryRun run = newRun(RunType.LADDER, asOf, currency, officeId, ruleSet.getId(), ruleSet.getVersion(),
                assumptionSet.map(TreasuryAssumptionSet::getId).orElse(null),
                assumptionSet.map(TreasuryAssumptionSet::getVersion).orElse(null), null, notes, userId);
        run.setBeyondHorizon(ladder.beyondHorizon());
        save(run);
        for (final LadderBuilder.Line line : ladder.lines()) {
            this.lineRepository.save(line(run, line.bucket(), line.source(), line.lineCode(), line.productId(), line.productName(),
                    line.inflow(), line.outflow(), line.contractual(), line.behavioural(), null, null));
        }
        for (final LadderBuilder.BucketTotal total : ladder.totals()) {
            this.lineRepository.save(line(run, total.bucket(), LadderSourceCode.TOTAL, "NET", null, null, total.inflow(), total.outflow(),
                    total.contractual(), total.behavioural(), total.net(), total.cumulativeGap()));
        }
        this.lineRepository.flush();
        this.limitEngine.afterRun(run, null, userId);
        return run;
    }

    TreasuryRun ratios(final TreasuryRun ladderRun, final Long userId) {
        replace(RunType.RATIOS, ladderRun.getAsOfDate(), ladderRun.getCurrencyCode(), ladderRun.getOfficeId());
        final TreasuryRun run = newRun(RunType.RATIOS, ladderRun.getAsOfDate(), ladderRun.getCurrencyCode(), ladderRun.getOfficeId(),
                ladderRun.getRuleSetId(), ladderRun.getRuleSetVersion(), ladderRun.getAssumptionSetId(),
                ladderRun.getAssumptionSetVersion(), ladderRun.getId(), List.of(), userId);
        save(run);
        this.ratioRunService.computeRatios(run, ladderRun, this.referenceDataLookup.decimalPlaces(run.getCurrencyCode()));
        save(run);
        this.limitEngine.afterRun(run, ladderRun, userId);
        return run;
    }

    TreasuryRun returnRun(final TreasuryRun ratiosRun, final Long userId) {
        replace(RunType.RETURN, ratiosRun.getAsOfDate(), ratiosRun.getCurrencyCode(), ratiosRun.getOfficeId());
        final TreasuryRun run = newRun(RunType.RETURN, ratiosRun.getAsOfDate(), ratiosRun.getCurrencyCode(), ratiosRun.getOfficeId(),
                ratiosRun.getRuleSetId(), ratiosRun.getRuleSetVersion(), ratiosRun.getAssumptionSetId(),
                ratiosRun.getAssumptionSetVersion(), ratiosRun.getId(), List.of(), userId);
        save(run);
        this.ratioRunService.buildReturn(run, ratiosRun);
        return run;
    }

    /**
     * A stress test of the ladder run with the scenario version, replacing the scope's earlier run of the scenario;
     * then the scope's SURVIVAL_DAYS limits and the contingency funding plan's survival triggers.
     */
    TreasuryRun stress(final TreasuryRun ladderRun, final TreasuryScenario scenario, final Long userId) {
        replace(RunType.STRESS, ladderRun.getAsOfDate(), ladderRun.getCurrencyCode(), ladderRun.getOfficeId(),
                r -> scenario.getCode().equals(r.getScenarioCode()));
        final TreasuryRun run = newRun(RunType.STRESS, ladderRun.getAsOfDate(), ladderRun.getCurrencyCode(), ladderRun.getOfficeId(),
                ladderRun.getRuleSetId(), ladderRun.getRuleSetVersion(), ladderRun.getAssumptionSetId(),
                ladderRun.getAssumptionSetVersion(), ladderRun.getId(), List.of(), userId);
        save(run);
        this.stressRunService.compute(run, ladderRun, scenario, this.referenceDataLookup.decimalPlaces(run.getCurrencyCode()));
        save(run);
        this.limitEngine.afterRun(run, ladderRun, userId);
        this.cfpService.stressRun(run, userId);
        return run;
    }

    /**
     * A repricing gap with the rule set and assumptions of the ladder run given, else of the request (or the defaults).
     */
    private TreasuryRun repricing(final RunRequest request, final LocalDate asOf, final Long userId) {
        final TreasuryRun ladder = request.sourceRunId() == null ? null : source(request.sourceRunId(), RunType.LADDER, request, asOf);
        if (ladder == null) {
            return repricing(asOf, request.currencyCode(), request.officeId(), ruleSet(request.ruleSetId()),
                    assumptionSet(request.assumptionSetId()), null, request.shocksBp(), userId);
        }
        return repricing(asOf, request.currencyCode(), request.officeId(), this.ruleSetService.find(ladder.getRuleSetId()),
                ladder.getAssumptionSetId() == null ? Optional.empty()
                        : Optional.of(this.assumptionSetService.find(ladder.getAssumptionSetId())),
                ladder, request.shocksBp(), userId);
    }

    /**
     * Principal outstanding by repricing date ({@link TreasuryRepricingRunService}), replacing the scope's earlier
     * REPRICING run; then the scope's REPRICING_GAP_PCT and EAR_PCT limits.
     */
    TreasuryRun repricing(final LocalDate asOf, final String currency, final Long officeId, final TreasuryRuleSet ruleSet,
            final Optional<TreasuryAssumptionSet> assumptionSet, final TreasuryRun ladderRun, final List<Integer> shocks,
            final Long userId) {
        replace(RunType.REPRICING, asOf, currency, officeId);
        final Assumptions assumptions = assumptionSet.map(s -> this.assumptionSetService.assumptions(s.getId())).orElse(Assumptions.NONE);
        final LadderContext context = new LadderContext(asOf, currency, officeId, this.ruleSetService.buckets(ruleSet.getId()), assumptions,
                this.referenceDataLookup.decimalPlaces(currency));
        final TreasuryRun run = newRun(RunType.REPRICING, asOf, currency, officeId, ruleSet.getId(), ruleSet.getVersion(),
                assumptionSet.map(TreasuryAssumptionSet::getId).orElse(null),
                assumptionSet.map(TreasuryAssumptionSet::getVersion).orElse(null), ladderRun == null ? null : ladderRun.getId(), List.of(),
                userId);
        save(run);
        this.repricingRunService.compute(run, context, shocks);
        final String notes = String.join("; ", context.warnings());
        run.setNotes(notes.isEmpty() ? null : notes.substring(0, Math.min(2000, notes.length())));
        save(run);
        this.limitEngine.afterRun(run, ladderRun, userId);
        return run;
    }

    private TreasuryScenario activeScenario(final Long scenarioId) {
        final TreasuryScenario scenario = this.scenarioService.find(scenarioId);
        if (scenario.getStatus() != ConfigStatus.ACTIVE) {
            throw new TreasuryWorkflowException(ENTITY, "scenario.not.active",
                    "Scenario " + scenario.getCode() + " version " + scenario.getVersion() + " is " + scenario.getStatus(), scenarioId);
        }
        return scenario;
    }

    /** The ladder run a ratios run is computed from: the one given, else the scope's, else a new one. */
    private TreasuryRun ladderFor(final RunRequest request, final LocalDate asOf, final Long userId) {
        if (request.sourceRunId() != null) {
            return source(request.sourceRunId(), RunType.LADDER, request, asOf);
        }
        final Optional<TreasuryRun> existing = inScope(asOf, request.currencyCode(), request.officeId()).stream()
                .filter(r -> r.getRunType() == RunType.LADDER).findFirst();
        if (existing.isPresent()) {
            if (request.ruleSetId() != null && !request.ruleSetId().equals(existing.get().getRuleSetId())) {
                throw new TreasuryWorkflowException(ENTITY, "ladder.rule.set.differs", "The ladder run of the scope uses rule set "
                        + existing.get().getRuleSetId() + "; run the ladder again with rule set " + request.ruleSetId() + " first",
                        existing.get().getId());
            }
            return existing.get();
        }
        return ladder(asOf, request.currencyCode(), request.officeId(), ruleSet(request.ruleSetId()),
                assumptionSet(request.assumptionSetId()), userId);
    }

    private TreasuryRun ratiosFor(final RunRequest request, final LocalDate asOf, final Long userId) {
        if (request.sourceRunId() != null) {
            return source(request.sourceRunId(), RunType.RATIOS, request, asOf);
        }
        final Optional<TreasuryRun> existing = inScope(asOf, request.currencyCode(), request.officeId()).stream()
                .filter(r -> r.getRunType() == RunType.RATIOS).findFirst();
        return existing.isPresent() ? existing.get() : ratios(ladderFor(request, asOf, userId), userId);
    }

    private TreasuryRun source(final Long runId, final RunType type, final RunRequest request, final LocalDate asOf) {
        final TreasuryRun run = find(runId);
        if (run.getRunType() != type || !run.getAsOfDate().equals(asOf) || !run.getCurrencyCode().equals(request.currencyCode())
                || !Objects.equals(run.getOfficeId(), request.officeId())) {
            throw new TreasuryWorkflowException(ENTITY, "source.run.invalid", "Run " + runId + " is not a " + type + " run of " + asOf
                    + ", " + request.currencyCode() + (request.officeId() == null ? ", institution" : ", office " + request.officeId()),
                    runId);
        }
        return run;
    }

    /**
     * Before a run of the type: refused when a return of the scope is SUBMITTED; else the scope's earlier run of the
     * type goes, with whatever was computed from it.
     */
    private void replace(final RunType type, final LocalDate asOf, final String currency, final Long officeId) {
        replace(type, asOf, currency, officeId, r -> true);
    }

    /** A STRESS run replaces the scope's earlier run of the same scenario only. */
    private void replace(final RunType type, final LocalDate asOf, final String currency, final Long officeId,
            final Predicate<TreasuryRun> same) {
        final List<TreasuryRun> scope = inScope(asOf, currency, officeId);
        final Optional<TreasuryRun> submitted = scope.stream().filter(r -> SUBMITTED.equals(r.getStatus())).findFirst();
        if (submitted.isPresent()) {
            throw new TreasuryWorkflowException(ENTITY, "locked", "Return " + submitted.get().getId() + " of " + asOf + " in " + currency
                    + " has been submitted; the runs it rests on cannot be replaced", submitted.get().getId());
        }
        for (final TreasuryRun earlier : scope) {
            if (earlier.getRunType() == type && same.test(earlier)) {
                delete(earlier);
            }
        }
        this.runRepository.flush();
    }

    private void delete(final TreasuryRun run) {
        for (final TreasuryRun dependent : this.runRepository.findBySourceRunId(run.getId())) {
            delete(dependent);
        }
        this.lineRepository.deleteByRunId(run.getId());
        this.lineRepository.flush();
        this.ratioRunService.deleteRun(run.getId());
        this.stressRunService.deleteRun(run.getId());
        this.repricingRunService.deleteRun(run.getId());
        this.runRepository.delete(run);
        this.runRepository.flush();
    }

    private List<TreasuryRun> inScope(final LocalDate asOf, final String currency, final Long officeId) {
        final List<TreasuryRun> runs = new ArrayList<>(this.runRepository.findByAsOfDateAndCurrencyCode(asOf, currency));
        for (final TreasuryRun run : this.runRepository.findByRunTypeAndAsOfDateAndCurrencyCode(RunType.LADDER, asOf, currency)) {
            if (runs.stream().noneMatch(r -> Objects.equals(r.getId(), run.getId()))) {
                runs.add(run);
            }
        }
        return runs.stream().filter(r -> Objects.equals(r.getOfficeId(), officeId)).toList();
    }

    /** Saves the run; another run of the scope saved meanwhile (the unique scope key) is refused as concurrent. */
    private void save(final TreasuryRun run) {
        try {
            this.runRepository.saveAndFlush(run);
        } catch (DataIntegrityViolationException | JpaSystemException e) {
            // EclipseLink reports the unique violation as a JpaSystemException; anything else is not ours to explain
            if (e instanceof JpaSystemException && !String.valueOf(e.getMessage()).contains(UNIQUE_SCOPE)) {
                throw e;
            }
            // the cause is taken from the arguments and left out of them
            throw new TreasuryWorkflowException(ENTITY, "concurrent",
                    "Another " + run.getRunType() + " run of " + run.getAsOfDate() + " in " + run.getCurrencyCode()
                            + (run.getOfficeId() == null ? "" : " for office " + run.getOfficeId())
                            + " was saved at the same time; run it again",
                    run.getRunType().name(), run.getAsOfDate(), run.getCurrencyCode(), e);
        }
    }

    private static TreasuryRun newRun(final RunType type, final LocalDate asOf, final String currency, final Long officeId,
            final Long ruleSetId, final int ruleSetVersion, final Long assumptionSetId, final Integer assumptionSetVersion,
            final Long sourceRunId, final List<String> notes, final Long userId) {
        final TreasuryRun run = new TreasuryRun();
        run.setRunType(type);
        run.setAsOfDate(asOf);
        run.setCurrencyCode(currency);
        run.setOfficeId(officeId);
        run.setRuleSetId(ruleSetId);
        run.setRuleSetVersion(ruleSetVersion);
        run.setAssumptionSetId(assumptionSetId);
        run.setAssumptionSetVersion(assumptionSetVersion);
        run.setSourceRunId(sourceRunId);
        run.setStatus(COMPLETED);
        run.setBeyondHorizon(BigDecimal.ZERO);
        final String joined = String.join("; ", notes);
        run.setNotes(joined.isEmpty() ? null : joined.substring(0, Math.min(2000, joined.length())));
        run.setCreatedBy(userId);
        run.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        return run;
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public List<TreasuryRunData> retrieveRuns(final String type, final String currency, final Long officeId, final LocalDate from,
            final LocalDate to) {
        final StringBuilder sql = new StringBuilder("SELECT id FROM m_treasury_run WHERE 1 = 1");
        final MapSqlParameterSource params = new MapSqlParameterSource();
        if (type != null) {
            sql.append(" AND run_type = :type");
            params.addValue("type", type.trim().toUpperCase());
        }
        if (currency != null) {
            sql.append(" AND currency_code = :currency");
            params.addValue("currency", currency.trim().toUpperCase());
        }
        if (officeId != null) {
            sql.append(" AND office_id = :officeId");
            params.addValue("officeId", officeId);
        }
        if (from != null) {
            sql.append(" AND as_of_date >= :from");
            params.addValue("from", from);
        }
        if (to != null) {
            sql.append(" AND as_of_date <= :to");
            params.addValue("to", to);
        }
        sql.append(" ORDER BY as_of_date DESC, id DESC");
        return this.jdbcTemplate.queryForList(sql.toString(), params, Long.class).stream().map(id -> data(find(id))).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryRunData retrieveRun(final Long runId) {
        return data(find(runId));
    }

    @Transactional(readOnly = true)
    public List<TreasuryRunData.Line> retrieveLines(final Long runId) {
        find(runId);
        return this.lineRepository.findByRunIdOrderByBucketSeqAscIdAsc(runId).stream().map(TreasuryLiquidityRunService::line).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryReturnData retrieveReturn(final Long runId) {
        final TreasuryRun run = returnRun(runId);
        return this.ratioRunService.returnData(run,
                run.getSourceRunId() == null ? null : this.runRepository.findById(run.getSourceRunId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public TreasuryRepricingData retrieveRepricing(final Long runId) {
        final TreasuryRun run = find(runId);
        if (run.getRunType() != RunType.REPRICING) {
            throw new TreasuryWorkflowException(ENTITY, "not.a.repricing.run", "Run " + runId + " is a " + run.getRunType() + " run",
                    runId);
        }
        return this.repricingRunService.data(run);
    }

    @Transactional(readOnly = true)
    public TreasuryStressData retrieveStress(final Long runId) {
        final TreasuryRun run = find(runId);
        if (run.getRunType() != RunType.STRESS) {
            throw new TreasuryWorkflowException(ENTITY, "not.a.stress.run", "Run " + runId + " is a " + run.getRunType() + " run", runId);
        }
        return this.stressRunService.data(run);
    }

    @Transactional(readOnly = true)
    public String retrieveReturnCsv(final Long runId) {
        final TreasuryRun run = returnRun(runId);
        return TreasuryRatioRunService.csv(retrieveReturn(runId), this.referenceDataLookup.decimalPlaces(run.getCurrencyCode()));
    }

    private TreasuryRun returnRun(final Long runId) {
        final TreasuryRun run = find(runId);
        if (run.getRunType() != RunType.RETURN) {
            throw new TreasuryWorkflowException(ENTITY, "not.a.return", "Run " + runId + " is a " + run.getRunType() + " run, not a return",
                    runId);
        }
        return run;
    }

    private TreasuryRunData data(final TreasuryRun run) {
        final List<TreasuryRunLine> lines = this.lineRepository.findByRunIdOrderByBucketSeqAscIdAsc(run.getId());
        final BigDecimal opening = lines.stream().filter(l -> l.getSource() == LadderSourceCode.OPENING)
                .map(l -> l.getContractual().add(l.getBehavioural())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TreasuryRunData(run.getId(), run.getRunType().name(), run.getAsOfDate(), run.getCurrencyCode(), run.getOfficeId(),
                run.getRuleSetId(), run.getRuleSetVersion(), run.getAssumptionSetId(), run.getAssumptionSetVersion(), run.getStatus(),
                run.getNotes(), opening, run.getBeyondHorizon(), run.getSourceRunId(), run.getSubmittedBy(), run.getSubmittedOnDate(),
                run.getCreatedBy(), run.getCreatedOnUtc(),
                lines.stream().filter(l -> l.getSource() == LadderSourceCode.TOTAL).map(TreasuryLiquidityRunService::line).toList(),
                run.getRunType() == RunType.RATIOS ? this.ratioRunService.ratios(run.getId()) : List.of());
    }

    private static TreasuryRunData.Line line(final TreasuryRunLine l) {
        return new TreasuryRunData.Line(l.getBucketSeq(), l.getBucketCode(), l.getSource().name(), l.getLineCode(), l.getProductId(),
                l.getProductName(), l.getInflow(), l.getOutflow(), l.getContractual(), l.getBehavioural(), l.getNet(), l.getCumulativeGap(),
                l.getRateType() == null ? null : l.getRateType().name());
    }

    private static TreasuryRunLine line(final TreasuryRun run, final Bucket bucket, final LadderSourceCode source, final String lineCode,
            final Long productId, final String productName, final BigDecimal inflow, final BigDecimal outflow, final BigDecimal contractual,
            final BigDecimal behavioural, final BigDecimal net, final BigDecimal cumulative) {
        final TreasuryRunLine line = new TreasuryRunLine();
        line.setRunId(run.getId());
        line.setBucketSeq(bucket.seq());
        line.setBucketCode(bucket.code());
        line.setSource(source);
        line.setLineCode(lineCode);
        line.setProductId(productId);
        line.setProductName(productName == null || productName.length() <= 150 ? productName : productName.substring(0, 150));
        line.setInflow(inflow);
        line.setOutflow(outflow);
        line.setContractual(contractual);
        line.setBehavioural(behavioural);
        line.setNet(net);
        line.setCumulativeGap(cumulative);
        return line;
    }

    private TreasuryRuleSet ruleSet(final Long ruleSetId) {
        return ruleSetId == null ? this.ruleSetService.defaultRuleSet() : active(this.ruleSetService.find(ruleSetId));
    }

    private Optional<TreasuryAssumptionSet> assumptionSet(final Long setId) {
        return setId == null ? this.assumptionSetService.activeSet() : Optional.of(active(this.assumptionSetService.find(setId)));
    }

    private TreasuryRun find(final Long runId) {
        return this.runRepository.findById(runId)
                .orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "not.found", "Treasury run " + runId + " does not exist", runId));
    }

    private static TreasuryRuleSet active(final TreasuryRuleSet set) {
        if (set.getStatus() != ConfigStatus.ACTIVE) {
            throw new TreasuryWorkflowException(ENTITY, "rule.set.not.active",
                    "Rule set " + set.getCode() + " version " + set.getVersion() + " is " + set.getStatus(), set.getId());
        }
        return set;
    }

    private static TreasuryAssumptionSet active(final TreasuryAssumptionSet set) {
        if (set.getStatus() != ConfigStatus.ACTIVE) {
            throw new TreasuryWorkflowException(ENTITY, "assumption.set.not.active",
                    "Assumption set " + set.getCode() + " version " + set.getVersion() + " is " + set.getStatus(), set.getId());
        }
        return set;
    }
}
