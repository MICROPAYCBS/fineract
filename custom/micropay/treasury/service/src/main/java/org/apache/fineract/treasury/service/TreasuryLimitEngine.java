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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.deal.LimitCheck;
import org.apache.fineract.treasury.domain.TreasuryBreach;
import org.apache.fineract.treasury.domain.TreasuryBreachComment;
import org.apache.fineract.treasury.domain.TreasuryBreachCommentRepository;
import org.apache.fineract.treasury.domain.TreasuryBreachRepository;
import org.apache.fineract.treasury.domain.TreasuryCounterparty;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.domain.TreasuryLimitEvaluation;
import org.apache.fineract.treasury.domain.TreasuryLimitEvaluationLine;
import org.apache.fineract.treasury.domain.TreasuryLimitEvaluationLineRepository;
import org.apache.fineract.treasury.domain.TreasuryLimitEvaluationRepository;
import org.apache.fineract.treasury.domain.TreasuryLimitRepository;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
import org.apache.fineract.treasury.limits.BreachAction;
import org.apache.fineract.treasury.limits.BreachStatus;
import org.apache.fineract.treasury.limits.CounterpartyExposureLimitEvaluator;
import org.apache.fineract.treasury.limits.EvaluationContext;
import org.apache.fineract.treasury.limits.LimitEvaluator;
import org.apache.fineract.treasury.limits.LimitMetric;
import org.apache.fineract.treasury.limits.LimitScopeType;
import org.apache.fineract.treasury.limits.LimitStatus;
import org.apache.fineract.treasury.limits.Observation;
import org.apache.fineract.treasury.liquidity.RunType;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The limits engine. An evaluation reads the active limits that apply to a date, currency and scope (the institution,
 * or an office), has each metric's {@link LimitEvaluator} observe them, and records every observation as a line of a
 * stored {@link TreasuryLimitEvaluation} and in the breach log:
 *
 * <ul>
 * <li>one breach stays open (OPEN or ACKNOWLEDGED) per limit, subject and currency; seeing it again adds an occurrence
 * and moves its last-seen date;</li>
 * <li>a WARNING breach that crosses the hard level is upgraded to HARD in place (escalated on, levels as they were
 * then, a system comment), and reopened if it had been acknowledged;</li>
 * <li>a value back within the levels marks the breach cleared but leaves it open: only a user resolves it;</li>
 * <li>new and escalated breaches notify the limit's role.</li>
 * </ul>
 *
 * <p>
 * The ladder and ratios runs evaluate their own metrics as they are made (BUCKET_GAP, RATIO); deal capture, edit,
 * rollover and approval evaluate the counterparty's COUNTERPARTY_EXPOSURE limits with the capture check's result, a
 * refused deal in a transaction of its own so its breach is kept; the "Treasury Limits" job and
 * {@code POST /treasury/limits?command=evaluate} evaluate every metric.
 * </p>
 */
@Slf4j
@Service
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryLimitEngine {

    public static final String TRIGGER_RUN = "RUN";
    public static final String TRIGGER_JOB = "JOB";
    public static final String TRIGGER_MANUAL = "MANUAL";
    public static final String TRIGGER_DEAL = "DEAL";
    static final List<BreachStatus> OPEN = List.of(BreachStatus.OPEN, BreachStatus.ACKNOWLEDGED);

    private final Map<LimitMetric, LimitEvaluator> evaluators = new EnumMap<>(LimitMetric.class);
    private final TreasuryLimitRepository limitRepository;
    private final TreasuryBreachRepository breachRepository;
    private final TreasuryBreachCommentRepository commentRepository;
    private final TreasuryLimitEvaluationRepository evaluationRepository;
    private final TreasuryLimitEvaluationLineRepository lineRepository;
    private final TreasuryRunRepository runRepository;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final TreasuryBreachNotifier notifier;
    private final AppUserRepositoryWrapper appUserRepository;
    private final PlatformTransactionManager transactionManager;
    private final TreasuryCfpService cfpService;

    public TreasuryLimitEngine(final List<LimitEvaluator> evaluators, final TreasuryLimitRepository limitRepository,
            final TreasuryBreachRepository breachRepository, final TreasuryBreachCommentRepository commentRepository,
            final TreasuryLimitEvaluationRepository evaluationRepository, final TreasuryLimitEvaluationLineRepository lineRepository,
            final TreasuryRunRepository runRepository, final TreasuryReferenceDataLookup referenceDataLookup,
            final TreasuryBreachNotifier notifier, final AppUserRepositoryWrapper appUserRepository,
            final PlatformTransactionManager transactionManager, final TreasuryCfpService cfpService) {
        evaluators.forEach(e -> this.evaluators.put(e.metric(), e));
        this.limitRepository = limitRepository;
        this.breachRepository = breachRepository;
        this.commentRepository = commentRepository;
        this.evaluationRepository = evaluationRepository;
        this.lineRepository = lineRepository;
        this.runRepository = runRepository;
        this.referenceDataLookup = referenceDataLookup;
        this.notifier = notifier;
        this.appUserRepository = appUserRepository;
        this.transactionManager = transactionManager;
        this.cfpService = cfpService;
    }

    /** What an evaluation did; {@code evaluationId} is null when no limit applied and nothing was stored. */
    public record Summary(Long evaluationId, int limits, int created, int escalated, int repeated, int cleared, int notified,
            List<Long> breachIds) {

        static final Summary NONE = new Summary(null, 0, 0, 0, 0, 0, 0, List.of());
    }

    // ---------- evaluations ----------

    /**
     * @param metrics
     *            the metrics to evaluate, every metric when null
     * @param keepEmpty
     *            store the evaluation even when no limit applies
     */
    @Transactional
    public Summary evaluate(final EvaluationContext context, final Set<LimitMetric> metrics, final String trigger, final Long sourceRunId,
            final Long userId, final boolean keepEmpty) {
        final List<TreasuryLimit> limits = applicable(context, metrics);
        if (limits.isEmpty() && !keepEmpty) {
            return Summary.NONE;
        }
        final TreasuryLimitEvaluation evaluation = newEvaluation(context, trigger, sourceRunId, null, userId);
        final Tally tally = new Tally();
        for (final TreasuryLimit limit : limits) {
            final LimitEvaluator evaluator = this.evaluators.get(limit.getMetric());
            if (evaluator == null) {
                log.warn("No evaluator for treasury limit {} of metric {}", limit.getCode(), limit.getMetric());
                continue;
            }
            for (final Observation observation : evaluator.observe(limit, context)) {
                record(limit, context, observation, evaluation, tally, userId);
            }
        }
        return finish(evaluation, limits.size(), tally);
    }

    /**
     * After a LADDER run its BUCKET_GAP limits, after a RATIOS run its RATIO limits, after a STRESS run SURVIVAL_DAYS.
     */
    @Transactional
    public Summary afterRun(final TreasuryRun run, final TreasuryRun ladderRun, final Long userId) {
        final LimitMetric metric = switch (run.getRunType()) {
            case LADDER -> LimitMetric.BUCKET_GAP;
            case RATIOS -> LimitMetric.RATIO;
            case STRESS -> LimitMetric.SURVIVAL_DAYS;
            default -> null;
        };
        if (metric == null) {
            return Summary.NONE;
        }
        final boolean ladder = run.getRunType() == RunType.LADDER;
        final EvaluationContext context = new EvaluationContext(run.getAsOfDate(), run.getCurrencyCode(), run.getOfficeId(),
                ladder ? run : ladderRun, run.getRunType() == RunType.RATIOS ? run : null,
                this.referenceDataLookup.decimalPlaces(run.getCurrencyCode()));
        return evaluate(context, EnumSet.of(metric), TRIGGER_RUN, run.getId(), userId, false);
    }

    /** The latest evaluation that read the run. */
    public Optional<TreasuryLimitEvaluation> latestFor(final Long runId) {
        return this.evaluationRepository.findFirstBySourceRunIdOrderByIdDesc(runId);
    }

    /** The job: every organisation currency on the business date, for the institution and each office with a limit. */
    public int evaluateBusinessDate() {
        final LocalDate asOf = DateUtils.getBusinessLocalDate();
        final TransactionTemplate template = new TransactionTemplate(this.transactionManager);
        final Long systemUserId = template.execute(status -> this.appUserRepository.fetchSystemUser().getId());
        final Set<Long> offices = new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));
        offices.add(null);
        template.execute(status -> {
            this.limitRepository.findByStatusOrderByCodeAsc(LimitStatus.ACTIVE).stream()
                    .filter(l -> l.getScopeType() == LimitScopeType.OFFICE).forEach(l -> offices.add(l.getScopeId()));
            return null;
        });
        int evaluations = 0;
        for (final String currency : this.referenceDataLookup.organisationCurrencies()) {
            for (final Long officeId : offices) {
                try {
                    final Summary summary = template.execute(
                            status -> evaluate(contextFor(asOf, currency, officeId), null, TRIGGER_JOB, null, systemUserId, false));
                    if (summary != null && summary.evaluationId() != null) {
                        evaluations++;
                        final String scope = officeId == null ? "institution" : "office " + officeId;
                        final String counts = summary.limits() + " limits, " + summary.created() + " new, " + summary.escalated()
                                + " escalated, " + summary.repeated() + " repeated, " + summary.cleared() + " cleared breaches";
                        log.info("Treasury limits for {} {} {}: {}", asOf, currency, scope, counts);
                    }
                } catch (RuntimeException e) {
                    log.error("Treasury limits for {} {} {} failed: {}", asOf, currency, officeId, e.getMessage());
                }
            }
        }
        log.info("Treasury limits for {}: {} evaluations", asOf, evaluations);
        return evaluations;
    }

    /** The scope's latest LADDER and RATIOS runs of the date. */
    public EvaluationContext contextFor(final LocalDate asOf, final String currency, final Long officeId) {
        final List<TreasuryRun> runs = this.runRepository.findByAsOfDateAndCurrencyCode(asOf, currency).stream()
                .filter(r -> Objects.equals(r.getOfficeId(), officeId)).toList();
        return new EvaluationContext(asOf, currency, officeId, latest(runs, RunType.LADDER), latest(runs, RunType.RATIOS),
                this.referenceDataLookup.decimalPlaces(currency));
    }

    /** A run's scope, with the run and the ladder or ratios run it belongs with. */
    public EvaluationContext contextFor(final TreasuryRun run) {
        TreasuryRun ladder = null;
        TreasuryRun ratios = null;
        switch (run.getRunType()) {
            case LADDER -> {
                ladder = run;
                ratios = this.runRepository.findBySourceRunId(run.getId()).stream().filter(r -> r.getRunType() == RunType.RATIOS)
                        .max(Comparator.comparing(TreasuryRun::getId)).orElse(null);
            }
            case RATIOS -> {
                ratios = run;
                ladder = source(run);
            }
            case RETURN -> {
                ratios = source(run);
                ladder = ratios == null ? null : source(ratios);
            }
            default -> {
            }
        }
        return new EvaluationContext(run.getAsOfDate(), run.getCurrencyCode(), run.getOfficeId(), ladder, ratios,
                this.referenceDataLookup.decimalPlaces(run.getCurrencyCode()));
    }

    // ---------- deal capture ----------

    /** The capture check of a deal that goes ahead (within the limit, at the warning level, or referred). */
    @Transactional
    public Summary dealChecked(final TreasuryCounterparty counterparty, final TreasuryDeal deal, final LimitCheck.Result result,
            final Long userId) {
        return captureCheck(counterparty, deal, result, userId, false);
    }

    /** The capture check of a deal refused for its counterparty's limit: kept although the deal is not. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Summary dealRefused(final TreasuryCounterparty counterparty, final TreasuryDeal deal, final LimitCheck.Result result,
            final Long userId) {
        return captureCheck(counterparty, deal, result, userId, true);
    }

    private Summary captureCheck(final TreasuryCounterparty counterparty, final TreasuryDeal deal, final LimitCheck.Result result,
            final Long userId, final boolean refused) {
        final EvaluationContext context = new EvaluationContext(DateUtils.getBusinessLocalDate(), deal.getCurrencyCode(), null, null, null,
                this.referenceDataLookup.decimalPlaces(deal.getCurrencyCode()));
        final List<TreasuryLimit> limits = applicable(context, EnumSet.of(LimitMetric.COUNTERPARTY_EXPOSURE)).stream()
                .filter(l -> l.getScopeType() != LimitScopeType.COUNTERPARTY || counterparty.getId().equals(l.getScopeId())).toList();
        if (limits.isEmpty()) {
            return Summary.NONE;
        }
        final TreasuryLimitEvaluation evaluation = newEvaluation(context, TRIGGER_DEAL, null, deal.getId(), userId);
        final String sourceType = deal.getId() == null ? "DEAL_REQUEST" : "DEAL";
        final String detail = (refused ? "Refused: " : deal.isLimitExceeded() ? "Referred for an over-limit approval: " : "")
                + (deal.getId() == null ? "a deal being captured" : "deal " + deal.getId()) + " of "
                + deal.getExposureAmount().stripTrailingZeros().toPlainString() + " " + deal.getCurrencyCode() + " takes the exposure to "
                + result.exposureAfter().stripTrailingZeros().toPlainString()
                + (counterparty.getExposureLimit() == null ? ""
                        : " against a limit of " + counterparty.getExposureLimit().stripTrailingZeros().toPlainString() + " "
                                + counterparty.getLimitCurrencyCode());
        final Tally tally = new Tally();
        for (final TreasuryLimit limit : limits) {
            final Observation observation = CounterpartyExposureLimitEvaluator.observe(limit, counterparty, context.currencyCode(), result,
                    result.exposureAfter(), sourceType, deal.getId(), detail);
            if (observation != null) {
                record(limit, context, observation, evaluation, tally, userId);
            }
        }
        return finish(evaluation, limits.size(), tally);
    }

    // ---------- the breach log ----------

    List<TreasuryLimit> applicable(final EvaluationContext context, final Set<LimitMetric> metrics) {
        final LocalDate asOf = context.asOfDate();
        return this.limitRepository.findByStatusOrderByCodeAsc(LimitStatus.ACTIVE).stream()
                .filter(l -> metrics == null || metrics.contains(l.getMetric()))
                .filter(l -> l.getCurrencyCode() == null || l.getCurrencyCode().equals(context.currencyCode()))
                .filter(l -> l.getEffectiveFrom() == null || !l.getEffectiveFrom().isAfter(asOf))
                .filter(l -> l.getEffectiveTo() == null || !l.getEffectiveTo().isBefore(asOf))
                .filter(l -> appliesTo(l.getScopeType(), l.getScopeId(), context)).toList();
    }

    /** An office's limits apply to the office's evaluations; every other limit to the institution's. */
    static boolean appliesTo(final LimitScopeType scopeType, final Long scopeId, final EvaluationContext context) {
        return context.isInstitution() ? scopeType != LimitScopeType.OFFICE
                : scopeType == LimitScopeType.OFFICE && context.officeId().equals(scopeId);
    }

    private void record(final TreasuryLimit limit, final EvaluationContext context, final Observation observation,
            final TreasuryLimitEvaluation evaluation, final Tally tally, final Long userId) {
        final Optional<TreasuryBreach> open = this.breachRepository.findByLimitIdAndStatusIn(limit.getId(), OPEN).stream()
                .filter(b -> b.getScopeType() == observation.subjectType() && Objects.equals(b.getScopeId(), observation.subjectId())
                        && Objects.equals(b.getCurrencyCode(), context.currencyCode()))
                .findFirst();
        final BreachAction action = observation.value() == null && observation.level() == null ? BreachAction.NONE
                : BreachAction.decide(open.map(TreasuryBreach::getLevel).orElse(null), observation.level());
        TreasuryBreach breach = open.orElse(null);
        switch (action) {
            case CREATED -> breach = create(limit, context, observation, evaluation);
            case REPEATED -> seenAgain(breach, context, observation, evaluation, userId);
            case ESCALATED -> escalate(limit, breach, context, observation, evaluation, userId);
            case CLEARED -> clear(breach, context, observation, evaluation, userId);
            default -> {
            }
        }
        if (breach != null && action != BreachAction.NONE) {
            this.breachRepository.saveAndFlush(breach);
        }
        this.lineRepository.save(line(evaluation, ++tally.seq, limit, observation, action, breach == null ? null : breach.getId()));
        tally.count(action, breach);
        if (action.notifies()) {
            tally.notified += this.notifier.notify(limit, breach, action, userId);
            this.cfpService.breachRaised(limit, breach, userId);
        }
    }

    private TreasuryBreach create(final TreasuryLimit limit, final EvaluationContext context, final Observation observation,
            final TreasuryLimitEvaluation evaluation) {
        final TreasuryBreach breach = new TreasuryBreach();
        breach.setLimitId(limit.getId());
        breach.setLimitCode(limit.getCode());
        breach.setMetric(limit.getMetric());
        breach.setMetricKey(limit.getMetricKey());
        breach.setScopeType(observation.subjectType());
        breach.setScopeId(observation.subjectId());
        breach.setScopeLabel(cut(observation.subjectLabel(), 200));
        breach.setCurrencyCode(context.currencyCode());
        breach.setAsOfDate(context.asOfDate());
        breach.setLevel(observation.level());
        breach.setStatus(BreachStatus.OPEN);
        breach.setFirstSeenOn(context.asOfDate());
        breach.setLastSeenOn(context.asOfDate());
        breach.setOccurrences(1);
        breach.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        snapshotLevels(breach, limit, observation);
        seen(breach, context, observation, evaluation);
        return breach;
    }

    private void seenAgain(final TreasuryBreach breach, final EvaluationContext context, final Observation observation,
            final TreasuryLimitEvaluation evaluation, final Long userId) {
        if (breach.getClearedOn() != null) {
            comment(breach.getId(), "SYSTEM", "At the " + observation.level() + " level again on " + context.asOfDate()
                    + " after being back within the levels on " + breach.getClearedOn() + ": " + value(observation), userId);
            breach.setClearedOn(null);
        }
        breach.setOccurrences(breach.getOccurrences() + 1);
        seen(breach, context, observation, evaluation);
    }

    private void escalate(final TreasuryLimit limit, final TreasuryBreach breach, final EvaluationContext context,
            final Observation observation, final TreasuryLimitEvaluation evaluation, final Long userId) {
        comment(breach.getId(), "SYSTEM", "Escalated from " + breach.getLevel() + " to " + observation.level() + " on " + context.asOfDate()
                + ": " + value(observation) + (breach.getStatus() == BreachStatus.ACKNOWLEDGED ? "; reopened" : ""), userId);
        breach.setLevel(observation.level());
        breach.setEscalatedOn(context.asOfDate());
        breach.setClearedOn(null);
        if (breach.getStatus() == BreachStatus.ACKNOWLEDGED) {
            breach.setStatus(BreachStatus.OPEN);
        }
        breach.setOccurrences(breach.getOccurrences() + 1);
        snapshotLevels(breach, limit, observation);
        seen(breach, context, observation, evaluation);
    }

    private void clear(final TreasuryBreach breach, final EvaluationContext context, final Observation observation,
            final TreasuryLimitEvaluation evaluation, final Long userId) {
        if (breach.getClearedOn() == null) {
            breach.setClearedOn(context.asOfDate());
            comment(breach.getId(), "SYSTEM",
                    "Back within the levels on " + context.asOfDate() + ": " + value(observation) + "; open until resolved", userId);
        }
        breach.setLastLevel(null);
        breach.setObservedValue(observation.value());
        breach.setEvaluationId(evaluation.getId());
    }

    private static void seen(final TreasuryBreach breach, final EvaluationContext context, final Observation observation,
            final TreasuryLimitEvaluation evaluation) {
        if (breach.getLastSeenOn() == null || context.asOfDate().isAfter(breach.getLastSeenOn())) {
            breach.setLastSeenOn(context.asOfDate());
        }
        breach.setLastLevel(observation.level());
        breach.setObservedValue(observation.value());
        breach.setSourceType(observation.sourceType());
        breach.setSourceId(observation.sourceId());
        breach.setDetail(cut(observation.detail(), 1000));
        breach.setEvaluationId(evaluation.getId());
    }

    private static void snapshotLevels(final TreasuryBreach breach, final TreasuryLimit limit, final Observation observation) {
        breach.setLimitVersion(limit.getLimitVersion());
        breach.setComparison(observation.comparison());
        breach.setWarningLevel(observation.warning());
        breach.setHardLevel(observation.hard());
        breach.setUnit(limit.getUnit());
    }

    private void comment(final Long breachId, final String kind, final String text, final Long userId) {
        final TreasuryBreachComment comment = new TreasuryBreachComment();
        comment.setBreachId(breachId);
        comment.setKind(kind);
        comment.setComment(cut(text, 2000));
        comment.setCreatedBy(userId);
        comment.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.commentRepository.save(comment);
    }

    private TreasuryLimitEvaluation newEvaluation(final EvaluationContext context, final String trigger, final Long sourceRunId,
            final Long sourceDealId, final Long userId) {
        final TreasuryLimitEvaluation evaluation = new TreasuryLimitEvaluation();
        evaluation.setAsOfDate(context.asOfDate());
        evaluation.setCurrencyCode(context.currencyCode());
        evaluation.setOfficeId(context.officeId());
        evaluation.setTriggerType(trigger);
        evaluation.setSourceRunId(sourceRunId != null ? sourceRunId
                : context.ratiosRun() != null ? context.ratiosRun().getId()
                        : context.ladderRun() == null ? null : context.ladderRun().getId());
        evaluation.setSourceDealId(sourceDealId);
        evaluation.setCreatedBy(userId);
        evaluation.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        return this.evaluationRepository.saveAndFlush(evaluation);
    }

    private Summary finish(final TreasuryLimitEvaluation evaluation, final int limits, final Tally tally) {
        evaluation.setLimitsEvaluated(limits);
        evaluation.setBreachesCreated(tally.created);
        evaluation.setBreachesEscalated(tally.escalated);
        evaluation.setBreachesRepeated(tally.repeated);
        evaluation.setBreachesCleared(tally.cleared);
        this.evaluationRepository.saveAndFlush(evaluation);
        this.lineRepository.flush();
        return new Summary(evaluation.getId(), limits, tally.created, tally.escalated, tally.repeated, tally.cleared, tally.notified,
                List.copyOf(tally.breachIds));
    }

    private static TreasuryLimitEvaluationLine line(final TreasuryLimitEvaluation evaluation, final int seq, final TreasuryLimit limit,
            final Observation observation, final BreachAction action, final Long breachId) {
        final TreasuryLimitEvaluationLine line = new TreasuryLimitEvaluationLine();
        line.setEvaluationId(evaluation.getId());
        line.setSequenceNumber(seq);
        line.setLimitId(limit.getId());
        line.setLimitCode(limit.getCode());
        line.setLimitVersion(limit.getLimitVersion());
        line.setMetric(limit.getMetric());
        line.setScopeType(observation.subjectType());
        line.setScopeId(observation.subjectId());
        line.setScopeLabel(cut(observation.subjectLabel(), 200));
        line.setObservedValue(observation.value());
        line.setComparison(observation.comparison());
        line.setWarningLevel(observation.warning());
        line.setHardLevel(observation.hard());
        line.setUnit(limit.getUnit());
        line.setLevel(observation.level());
        line.setAction(action);
        line.setBreachId(breachId);
        line.setSourceType(observation.sourceType());
        line.setSourceId(observation.sourceId());
        line.setDetail(cut(observation.detail(), 1000));
        return line;
    }

    private TreasuryRun source(final TreasuryRun run) {
        return run.getSourceRunId() == null ? null : this.runRepository.findById(run.getSourceRunId()).orElse(null);
    }

    private static TreasuryRun latest(final List<TreasuryRun> runs, final RunType type) {
        return runs.stream().filter(r -> r.getRunType() == type).max(Comparator.comparing(TreasuryRun::getId)).orElse(null);
    }

    private static String value(final Observation observation) {
        final BigDecimal value = observation.value();
        return (value == null ? "no value" : value.stripTrailingZeros().toPlainString())
                + (observation.hard() == null ? "" : " against " + observation.hard().stripTrailingZeros().toPlainString());
    }

    private static String cut(final String text, final int length) {
        return text == null || text.length() <= length ? text : text.substring(0, length);
    }

    private static final class Tally {

        private int seq;
        private int created;
        private int escalated;
        private int repeated;
        private int cleared;
        private int notified;
        private final List<Long> breachIds = new ArrayList<>();

        private void count(final BreachAction action, final TreasuryBreach breach) {
            switch (action) {
                case CREATED -> this.created++;
                case ESCALATED -> this.escalated++;
                case REPEATED -> this.repeated++;
                case CLEARED -> this.cleared++;
                default -> {
                    return;
                }
            }
            if (breach != null && !this.breachIds.contains(breach.getId())) {
                this.breachIds.add(breach.getId());
            }
        }
    }
}
