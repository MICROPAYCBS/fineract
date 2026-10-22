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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryRunData;
import org.apache.fineract.treasury.domain.TreasuryAssumptionSet;
import org.apache.fineract.treasury.domain.TreasuryRuleSet;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunLine;
import org.apache.fineract.treasury.domain.TreasuryRunLineRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Liquidity runs. A LADDER run asks every {@link LadderSource} for its flows as of a date, in a currency, for an office
 * or the whole institution, buckets them with a rule set's buckets and the assumptions of an assumption set, and stores
 * the lines with the rule set and assumption set versions used. A rerun for the same type, date, currency and scope
 * replaces the earlier run. The "Treasury Liquidity Run" job runs every organisation currency for the business date
 * with the default rule set and the active assumption set.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryLiquidityRunService {

    static final String ENTITY = "run";
    static final String COMPLETED = "COMPLETED";

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

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final RunRequest request = this.validator.validateRun(command.json());
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
        final TreasuryRuleSet ruleSet = request.ruleSetId() == null ? this.ruleSetService.defaultRuleSet()
                : active(this.ruleSetService.find(request.ruleSetId()));
        final Optional<TreasuryAssumptionSet> assumptionSet = request.assumptionSetId() == null ? this.assumptionSetService.activeSet()
                : Optional.of(active(this.assumptionSetService.find(request.assumptionSetId())));
        final TreasuryRun run = ladder(asOf, request.currencyCode(), request.officeId(), ruleSet, assumptionSet,
                this.actor.userId(command));
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("asOfDate", run.getAsOfDate());
        changes.put("ruleSet", ruleSet.getCode() + " v" + ruleSet.getVersion());
        changes.put("assumptionSet", assumptionSet.map(s -> s.getCode() + " v" + s.getVersion()).orElse(null));
        if (run.getNotes() != null) {
            changes.put("notes", run.getNotes());
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(run.getId()).with(changes).build();
    }

    /** The job: every organisation currency, the whole institution, the business date. */
    public int runForBusinessDate() {
        final LocalDate asOf = DateUtils.getBusinessLocalDate();
        final TransactionTemplate template = new TransactionTemplate(this.transactionManager);
        final Long systemUserId = template.execute(status -> this.appUserRepository.fetchSystemUser().getId());
        int runs = 0;
        for (final String currency : this.referenceDataLookup.organisationCurrencies()) {
            try {
                template.execute(status -> ladder(asOf, currency, null, this.ruleSetService.defaultRuleSet(),
                        this.assumptionSetService.activeSet(), systemUserId));
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
        for (final TreasuryRun earlier : this.runRepository.findByRunTypeAndAsOfDateAndCurrencyCode(RunType.LADDER, asOf, currency)) {
            if (Objects.equals(earlier.getOfficeId(), officeId)) {
                this.lineRepository.deleteByRunId(earlier.getId());
                this.runRepository.delete(earlier);
            }
        }
        this.runRepository.flush();
        final TreasuryRun run = new TreasuryRun();
        run.setRunType(RunType.LADDER);
        run.setAsOfDate(asOf);
        run.setCurrencyCode(currency);
        run.setOfficeId(officeId);
        run.setRuleSetId(ruleSet.getId());
        run.setRuleSetVersion(ruleSet.getVersion());
        run.setAssumptionSetId(assumptionSet.map(TreasuryAssumptionSet::getId).orElse(null));
        run.setAssumptionSetVersion(assumptionSet.map(TreasuryAssumptionSet::getVersion).orElse(null));
        run.setStatus(COMPLETED);
        run.setBeyondHorizon(ladder.beyondHorizon());
        final List<String> notes = new ArrayList<>(context.warnings());
        if (assumptionSet.isEmpty()) {
            notes.add("No active assumption set: contractual flows only");
        }
        run.setNotes(notes.isEmpty() ? null : String.join("; ", notes).substring(0, Math.min(2000, String.join("; ", notes).length())));
        run.setCreatedBy(userId);
        run.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.runRepository.saveAndFlush(run);
        for (final LadderBuilder.Line line : ladder.lines()) {
            this.lineRepository.save(line(run, line.bucket(), line.source(), line.lineCode(), line.productId(), line.productName(),
                    line.inflow(), line.outflow(), line.contractual(), line.behavioural(), null, null));
        }
        for (final LadderBuilder.BucketTotal total : ladder.totals()) {
            this.lineRepository.save(line(run, total.bucket(), LadderSourceCode.TOTAL, "NET", null, null, total.inflow(), total.outflow(),
                    total.contractual(), total.behavioural(), total.net(), total.cumulativeGap()));
        }
        this.lineRepository.flush();
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
        return this.jdbcTemplate.queryForList(sql.toString(), params, Long.class).stream().map(id -> data(find(id), false)).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryRunData retrieveRun(final Long runId) {
        return data(find(runId), false);
    }

    @Transactional(readOnly = true)
    public List<TreasuryRunData.Line> retrieveLines(final Long runId) {
        find(runId);
        return this.lineRepository.findByRunIdOrderByBucketSeqAscIdAsc(runId).stream().map(TreasuryLiquidityRunService::line).toList();
    }

    private TreasuryRunData data(final TreasuryRun run, final boolean allLines) {
        final List<TreasuryRunLine> lines = this.lineRepository.findByRunIdOrderByBucketSeqAscIdAsc(run.getId());
        final BigDecimal opening = lines.stream().filter(l -> l.getSource() == LadderSourceCode.OPENING)
                .map(l -> l.getContractual().add(l.getBehavioural())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TreasuryRunData(run.getId(), run.getRunType().name(), run.getAsOfDate(), run.getCurrencyCode(), run.getOfficeId(),
                run.getRuleSetId(), run.getRuleSetVersion(), run.getAssumptionSetId(), run.getAssumptionSetVersion(), run.getStatus(),
                run.getNotes(), opening, run.getBeyondHorizon(), run.getCreatedBy(), run.getCreatedOnUtc(), lines.stream()
                        .filter(l -> allLines || l.getSource() == LadderSourceCode.TOTAL).map(TreasuryLiquidityRunService::line).toList());
    }

    private static TreasuryRunData.Line line(final TreasuryRunLine l) {
        return new TreasuryRunData.Line(l.getBucketSeq(), l.getBucketCode(), l.getSource().name(), l.getLineCode(), l.getProductId(),
                l.getProductName(), l.getInflow(), l.getOutflow(), l.getContractual(), l.getBehavioural(), l.getNet(),
                l.getCumulativeGap());
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
