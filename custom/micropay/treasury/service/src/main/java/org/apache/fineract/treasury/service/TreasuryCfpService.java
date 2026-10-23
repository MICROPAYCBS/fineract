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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryCfpActivationData;
import org.apache.fineract.treasury.data.TreasuryCfpSummaryData;
import org.apache.fineract.treasury.data.TreasuryCfpTriggerData;
import org.apache.fineract.treasury.domain.TreasuryBreach;
import org.apache.fineract.treasury.domain.TreasuryCfpAction;
import org.apache.fineract.treasury.domain.TreasuryCfpActionRepository;
import org.apache.fineract.treasury.domain.TreasuryCfpActivation;
import org.apache.fineract.treasury.domain.TreasuryCfpActivationAction;
import org.apache.fineract.treasury.domain.TreasuryCfpActivationActionRepository;
import org.apache.fineract.treasury.domain.TreasuryCfpActivationRepository;
import org.apache.fineract.treasury.domain.TreasuryCfpTrigger;
import org.apache.fineract.treasury.domain.TreasuryCfpTriggerRepository;
import org.apache.fineract.treasury.domain.TreasuryFundingLine;
import org.apache.fineract.treasury.domain.TreasuryFundingLineRepository;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.domain.TreasuryLimitRepository;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
import org.apache.fineract.treasury.domain.TreasuryRunStressBucket;
import org.apache.fineract.treasury.domain.TreasuryRunStressBucketRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.limits.LimitStatus;
import org.apache.fineract.treasury.liquidity.RunType;
import org.apache.fineract.treasury.serialization.TreasuryStressDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryStressDataValidator.ActionSpec;
import org.apache.fineract.treasury.serialization.TreasuryStressDataValidator.ActionStep;
import org.apache.fineract.treasury.serialization.TreasuryStressDataValidator.TriggerRequest;
import org.apache.fineract.treasury.stress.ActionStatus;
import org.apache.fineract.treasury.stress.ActivationStatus;
import org.apache.fineract.treasury.stress.CfpMatcher;
import org.apache.fineract.treasury.stress.TriggerCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The contingency funding plan: triggers with their stage and actions, activations, and the summary.
 *
 * <ul>
 * <li>a new or escalated breach of a LIMIT_BREACH trigger's limit (or of any limit of its metric) at or above its level
 * opens an activation, one per breach;</li>
 * <li>a STRESS run whose survival is below a SURVIVAL_BELOW trigger's days (of its scenario, or any) opens one, kept
 * per trigger, date, scope and scenario while open: a rerun updates it;</li>
 * <li>{@code ?command=fire} opens one by hand (MANUAL).</li>
 * </ul>
 *
 * <p>
 * An activation copies the trigger's actions, notifies their owners (the users and the users of the roles named, and
 * the trigger's role) in-app, and stays OPEN until closed with a comment; each action is completed on its own.
 * </p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryCfpService {

    static final String ENTITY = "cfp";
    static final String OBJECT_TYPE = "treasuryCfpActivation";

    private final TreasuryStressDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryCfpTriggerRepository triggerRepository;
    private final TreasuryCfpActionRepository actionRepository;
    private final TreasuryCfpActivationRepository activationRepository;
    private final TreasuryCfpActivationActionRepository activationActionRepository;
    private final TreasuryFundingLineRepository fundingLineRepository;
    private final TreasuryLimitRepository limitRepository;
    private final TreasuryRunRepository runRepository;
    private final TreasuryRunStressBucketRepository stressBucketRepository;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final TreasuryBreachNotifier notifier;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    // ---------- triggers ----------

    @Transactional
    public CommandProcessingResult createTrigger(final JsonCommand command) {
        final TriggerRequest request = this.validator.validateTrigger(command.json(), null);
        if (this.triggerRepository.findByCode(request.code()).isPresent()) {
            throw new TreasuryWorkflowException(ENTITY, "trigger.code.exists", "A trigger with code " + request.code() + " exists",
                    request.code());
        }
        checkReferences(request);
        final TreasuryCfpTrigger trigger = new TreasuryCfpTrigger();
        apply(trigger, request);
        trigger.setStatus(LimitStatus.INACTIVE);
        trigger.setCreatedBy(this.actor.userId(command));
        trigger.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.triggerRepository.saveAndFlush(trigger);
        saveActions(trigger, request.actions());
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("code", trigger.getCode());
        changes.put("status", trigger.getStatus().name());
        changes.put("actions", request.actions().size());
        return result(command, trigger.getId(), null, changes);
    }

    /** Any field but the code; the actions sent replace the trigger's. */
    @Transactional
    public CommandProcessingResult updateTrigger(final Long triggerId, final JsonCommand command) {
        final TreasuryCfpTrigger trigger = findTrigger(triggerId);
        final TriggerRequest request = this.validator.validateTrigger(command.json(), json(trigger));
        checkReferences(request);
        apply(trigger, request);
        trigger.setLastModifiedBy(this.actor.userId(command));
        trigger.setLastModifiedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.triggerRepository.saveAndFlush(trigger);
        this.actionRepository.deleteByTriggerId(triggerId);
        this.actionRepository.flush();
        saveActions(trigger, request.actions());
        return result(command, triggerId, null, Map.of("actions", request.actions().size()));
    }

    @Transactional
    public CommandProcessingResult activateTrigger(final Long triggerId, final JsonCommand command, final boolean activate) {
        this.validator.validateNote(command.json());
        final TreasuryCfpTrigger trigger = findTrigger(triggerId);
        final LimitStatus target = activate ? LimitStatus.ACTIVE : LimitStatus.INACTIVE;
        if (trigger.getStatus() == target) {
            throw new TreasuryWorkflowException(ENTITY, activate ? "trigger.already.active" : "trigger.not.active",
                    "Trigger " + trigger.getCode() + " is already " + target, triggerId);
        }
        trigger.setStatus(target);
        trigger.setLastModifiedBy(this.actor.userId(command));
        trigger.setLastModifiedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.triggerRepository.saveAndFlush(trigger);
        return result(command, triggerId, null, Map.of("status", target.name()));
    }

    /** A manual event: an activation of an active trigger, whatever its condition. */
    @Transactional
    public CommandProcessingResult fire(final Long triggerId, final JsonCommand command) {
        final String note = this.validator.validateNote(command.json());
        final TreasuryCfpTrigger trigger = findTrigger(triggerId);
        if (trigger.getStatus() != LimitStatus.ACTIVE) {
            throw new TreasuryWorkflowException(ENTITY, "trigger.not.active", "Trigger " + trigger.getCode() + " is not active", triggerId);
        }
        final TreasuryCfpActivation activation = open(trigger, "MANUAL", null, DateUtils.getBusinessLocalDate(), null, null, null,
                note == null ? "Declared by hand" : note, this.actor.userId(command));
        return result(command, triggerId, activation.getId(),
                Map.of("activationId", activation.getId(), "stage", trigger.getStage().name()));
    }

    // ---------- matching ----------

    /**
     * A new or escalated breach: the LIMIT_BREACH triggers it meets, once per breach while their activation is open.
     */
    @Transactional
    public List<Long> breachRaised(final TreasuryLimit limit, final TreasuryBreach breach, final Long userId) {
        final List<Long> opened = new ArrayList<>();
        for (final TreasuryCfpTrigger trigger : this.triggerRepository.findByStatusAndConditionType(LimitStatus.ACTIVE,
                TriggerCondition.LIMIT_BREACH)) {
            if (!CfpMatcher.meetsBreach(trigger.getConditionType(), trigger.getLimitId(), trigger.getMetric(), trigger.getLevel(),
                    limit.getId(), limit.getMetric(), breach.getLevel())) {
                continue;
            }
            final String detail = breach.getLevel() + " breach " + breach.getId() + " of limit " + limit.getCode() + " by "
                    + breach.getScopeLabel() + ": " + amount(breach.getObservedValue()) + " against " + amount(breach.getHardLevel());
            final Optional<TreasuryCfpActivation> existing = this.activationRepository
                    .findByTriggerIdAndStatus(trigger.getId(), ActivationStatus.OPEN).stream()
                    .filter(a -> "BREACH".equals(a.getSourceType()) && breach.getId().equals(a.getSourceId())).findFirst();
            if (existing.isPresent()) {
                seenAgain(existing.get(), breach.getId(), detail);
            } else {
                opened.add(open(trigger, "BREACH", breach.getId(), breach.getLastSeenOn(), breach.getCurrencyCode(),
                        breach.getScopeType().name().equals("OFFICE") ? breach.getScopeId() : null, null, detail, userId).getId());
            }
        }
        return opened;
    }

    /** A STRESS run: the SURVIVAL_BELOW triggers its survival meets, one activation per date, scope and scenario. */
    @Transactional
    public List<Long> stressRun(final TreasuryRun run, final Long userId) {
        final List<Long> opened = new ArrayList<>();
        for (final TreasuryCfpTrigger trigger : this.triggerRepository.findByStatusAndConditionType(LimitStatus.ACTIVE,
                TriggerCondition.SURVIVAL_BELOW)) {
            if (!CfpMatcher.meetsSurvival(trigger.getConditionType(), trigger.getSurvivalDays(), trigger.getScenarioCode(),
                    run.getSurvivalDays(), run.isSurvivalBeyondHorizon(), run.getScenarioCode())) {
                continue;
            }
            final String detail = "Scenario " + run.getScenarioCode() + " v" + run.getScenarioVersion() + " survives "
                    + amount(run.getSurvivalDays()) + " days, below " + amount(trigger.getSurvivalDays()) + " (stress run " + run.getId()
                    + ")";
            final Optional<TreasuryCfpActivation> existing = this.activationRepository
                    .findByTriggerIdAndStatus(trigger.getId(), ActivationStatus.OPEN).stream()
                    .filter(a -> "RUN".equals(a.getSourceType()) && run.getAsOfDate().equals(a.getAsOfDate())
                            && Objects.equals(run.getCurrencyCode(), a.getCurrencyCode())
                            && Objects.equals(run.getOfficeId(), a.getOfficeId())
                            && Objects.equals(run.getScenarioCode(), a.getScenarioCode()))
                    .findFirst();
            if (existing.isPresent()) {
                seenAgain(existing.get(), run.getId(), detail);
            } else {
                opened.add(open(trigger, "RUN", run.getId(), run.getAsOfDate(), run.getCurrencyCode(), run.getOfficeId(),
                        run.getScenarioCode(), detail, userId).getId());
            }
        }
        return opened;
    }

    private void seenAgain(final TreasuryCfpActivation activation, final Long sourceId, final String detail) {
        activation.setOccurrences(activation.getOccurrences() + 1);
        activation.setSourceId(sourceId);
        activation.setDetail(cut(detail, 1000));
        this.activationRepository.saveAndFlush(activation);
    }

    private TreasuryCfpActivation open(final TreasuryCfpTrigger trigger, final String sourceType, final Long sourceId, final LocalDate asOf,
            final String currency, final Long officeId, final String scenario, final String detail, final Long userId) {
        final TreasuryCfpActivation activation = new TreasuryCfpActivation();
        activation.setTriggerId(trigger.getId());
        activation.setTriggerCode(trigger.getCode());
        activation.setStage(trigger.getStage());
        activation.setSourceType(sourceType);
        activation.setSourceId(sourceId);
        activation.setAsOfDate(asOf);
        activation.setCurrencyCode(currency);
        activation.setOfficeId(officeId);
        activation.setScenarioCode(scenario);
        activation.setDetail(cut(detail, 1000));
        activation.setOccurrences(1);
        activation.setStatus(ActivationStatus.OPEN);
        activation.setOpenedBy(userId);
        activation.setOpenedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.activationRepository.saveAndFlush(activation);
        final Set<Long> owners = new LinkedHashSet<>(this.notifier.roleUsers(trigger.getNotifyRoleId()));
        for (final TreasuryCfpAction action : this.actionRepository.findByTriggerIdOrderBySequenceNumberAsc(trigger.getId())) {
            final TreasuryCfpActivationAction copy = new TreasuryCfpActivationAction();
            copy.setActivationId(activation.getId());
            copy.setActionId(action.getId());
            copy.setSequenceNumber(action.getSequenceNumber());
            copy.setDescription(action.getDescription());
            copy.setOwnerRoleId(action.getOwnerRoleId());
            copy.setOwnerUserId(action.getOwnerUserId());
            copy.setFundingLineId(action.getFundingLineId());
            copy.setStatus(ActionStatus.OPEN);
            this.activationActionRepository.save(copy);
            if (action.getOwnerUserId() != null) {
                owners.add(action.getOwnerUserId());
            }
            owners.addAll(this.notifier.roleUsers(action.getOwnerRoleId()));
        }
        this.activationActionRepository.flush();
        this.notifier.notifyUsers(owners, OBJECT_TYPE, activation.getId(), "cfpActivated", userId, "Contingency funding plan "
                + trigger.getStage() + ": trigger " + trigger.getCode() + " (" + trigger.getName() + ") - " + detail);
        return activation;
    }

    // ---------- activations ----------

    @Transactional
    public CommandProcessingResult completeAction(final Long activationId, final JsonCommand command) {
        final ActionStep step = this.validator.validateActivationStep(command.json(), true);
        final TreasuryCfpActivation activation = openActivation(activationId);
        final TreasuryCfpActivationAction action = this.activationActionRepository.findByActivationIdOrderBySequenceNumberAsc(activationId)
                .stream().filter(a -> a.getId().equals(step.actionId())).findFirst()
                .orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "action.not.found",
                        "Action " + step.actionId() + " is not an action of activation " + activationId, step.actionId()));
        if (action.getStatus() == ActionStatus.DONE) {
            throw new TreasuryWorkflowException(ENTITY, "action.done", "Action " + step.actionId() + " is already done", step.actionId());
        }
        action.setStatus(ActionStatus.DONE);
        action.setCompletedBy(this.actor.userId(command));
        action.setCompletedOnUtc(DateUtils.getAuditOffsetDateTime());
        action.setComment(step.comment());
        this.activationActionRepository.saveAndFlush(action);
        final long open = this.activationActionRepository.findByActivationIdOrderBySequenceNumberAsc(activationId).stream()
                .filter(a -> a.getStatus() == ActionStatus.OPEN).count();
        return result(command, activation.getId(), action.getId(), Map.of("actionStatus", ActionStatus.DONE.name(), "openActions", open));
    }

    @Transactional
    public CommandProcessingResult close(final Long activationId, final JsonCommand command) {
        final ActionStep step = this.validator.validateActivationStep(command.json(), false);
        final TreasuryCfpActivation activation = openActivation(activationId);
        activation.setStatus(ActivationStatus.CLOSED);
        activation.setClosedBy(this.actor.userId(command));
        activation.setClosedOnUtc(DateUtils.getAuditOffsetDateTime());
        activation.setCloseComment(step.comment());
        this.activationRepository.saveAndFlush(activation);
        return result(command, activationId, null, Map.of("status", ActivationStatus.CLOSED.name()));
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public List<TreasuryCfpTriggerData> retrieveTriggers() {
        return this.triggerRepository.findAllByOrderByCodeAsc().stream().map(this::data).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryCfpTriggerData retrieveTrigger(final Long triggerId) {
        return data(findTrigger(triggerId));
    }

    @Transactional(readOnly = true)
    public List<TreasuryCfpActivationData> retrieveActivations(final String status) {
        return (status == null ? this.activationRepository.findAllByOrderByIdDesc()
                : this.activationRepository.findByStatusOrderByIdDesc(ActivationStatus.valueOf(status.trim().toUpperCase()))).stream()
                .map(this::data).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryCfpActivationData retrieveActivation(final Long activationId) {
        return data(findActivation(activationId));
    }

    /**
     * Per organisation currency: the active funding lines not expired on the date by source and days to access, the
     * latest institution STRESS run of each scenario on the date with its worst stressed cumulative gap, and the
     * coverage of each gap and of the worst by the total available.
     */
    @Transactional(readOnly = true)
    public TreasuryCfpSummaryData summary(final LocalDate asOfDate) {
        final LocalDate asOf = asOfDate == null ? DateUtils.getBusinessLocalDate() : asOfDate;
        final List<TreasuryCfpSummaryData.Currency> currencies = new ArrayList<>();
        for (final String currency : this.referenceDataLookup.organisationCurrencies()) {
            final List<TreasuryFundingLine> lines = this.fundingLineRepository.findAllByOrderByCodeAsc().stream()
                    .filter(l -> TreasuryFundingLineService.availableOn(l, currency, asOf)).toList();
            final Map<List<Object>, List<TreasuryFundingLine>> grouped = lines.stream()
                    .sorted(Comparator.comparing((TreasuryFundingLine l) -> l.getSourceType().name())
                            .thenComparingInt(TreasuryFundingLine::getDaysToAccess))
                    .collect(Collectors.groupingBy(l -> List.of(l.getSourceType().name(), l.getDaysToAccess()), LinkedHashMap::new,
                            Collectors.toList()));
            final List<TreasuryCfpSummaryData.Source> sources = grouped.entrySet().stream()
                    .map(e -> new TreasuryCfpSummaryData.Source((String) e.getKey().get(0), (Integer) e.getKey().get(1), sum(e.getValue()),
                            e.getValue().size()))
                    .toList();
            final BigDecimal total = sum(lines);
            final BigDecimal capacity = sum(lines.stream().filter(TreasuryFundingLine::isIncludeInCapacity).toList());
            final Map<String, TreasuryRun> latest = this.runRepository
                    .findByRunTypeAndAsOfDateAndCurrencyCode(RunType.STRESS, asOf, currency).stream().filter(r -> r.getOfficeId() == null)
                    .collect(Collectors.toMap(TreasuryRun::getScenarioCode, Function.identity(), (a, b) -> a.getId() > b.getId() ? a : b,
                            TreeMap::new));
            final List<TreasuryCfpSummaryData.Scenario> scenarios = new ArrayList<>();
            BigDecimal worst = BigDecimal.ZERO;
            for (final TreasuryRun run : latest.values()) {
                final Optional<TreasuryRunStressBucket> lowest = this.stressBucketRepository
                        .findByRunIdOrderByBucketSeqAscIdAsc(run.getId()).stream()
                        .min(Comparator.comparing(TreasuryRunStressBucket::getStressedCumulative));
                final BigDecimal gap = lowest.map(b -> b.getStressedCumulative().negate().max(BigDecimal.ZERO)).orElse(BigDecimal.ZERO);
                worst = worst.max(gap);
                scenarios.add(new TreasuryCfpSummaryData.Scenario(run.getId(), run.getScenarioCode(), run.getScenarioVersion(),
                        run.getSurvivalDays(), run.isSurvivalBeyondHorizon(), gap, gap.signum() > 0 ? lowest.get().getBucketCode() : null,
                        coverage(total, gap)));
            }
            currencies
                    .add(new TreasuryCfpSummaryData.Currency(currency, total, capacity, sources, scenarios, worst, coverage(total, worst)));
        }
        return new TreasuryCfpSummaryData(asOf, currencies, retrieveActivations(ActivationStatus.OPEN.name()));
    }

    static BigDecimal coverage(final BigDecimal available, final BigDecimal gap) {
        return gap.signum() == 0 ? null : available.divide(gap, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(final List<TreasuryFundingLine> lines) {
        return lines.stream().map(TreasuryFundingLineService::available).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------- helpers ----------

    private void checkReferences(final TriggerRequest request) {
        if (request.limitId() != null && !this.limitRepository.existsById(request.limitId())) {
            throw new TreasuryWorkflowException(ENTITY, "limit.not.found", "Treasury limit " + request.limitId() + " does not exist",
                    request.limitId());
        }
        final Set<Long> roles = new LinkedHashSet<>();
        if (request.notifyRoleId() != null) {
            roles.add(request.notifyRoleId());
        }
        for (final ActionSpec action : request.actions()) {
            if (action.ownerRoleId() != null) {
                roles.add(action.ownerRoleId());
            }
            if (action.fundingLineId() != null && !this.fundingLineRepository.existsById(action.fundingLineId())) {
                throw new TreasuryWorkflowException(ENTITY, "funding.line.not.found",
                        "Funding line " + action.fundingLineId() + " does not exist", action.fundingLineId());
            }
        }
        for (final Long role : roles) {
            if (Objects.requireNonNullElse(this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM m_role WHERE id = :id",
                    new MapSqlParameterSource("id", role), Integer.class), 0) == 0) {
                throw new TreasuryWorkflowException(ENTITY, "role.not.found", "Role " + role + " does not exist", role);
            }
        }
    }

    private static void apply(final TreasuryCfpTrigger trigger, final TriggerRequest r) {
        trigger.setCode(r.code());
        trigger.setName(r.name());
        trigger.setConditionType(r.conditionType());
        trigger.setLimitId(r.limitId());
        trigger.setMetric(r.metric());
        trigger.setLevel(r.level());
        trigger.setSurvivalDays(r.survivalDays());
        trigger.setScenarioCode(r.scenarioCode());
        trigger.setStage(r.stage());
        trigger.setNotifyRoleId(r.notifyRoleId());
        trigger.setDescription(r.description());
    }

    private void saveActions(final TreasuryCfpTrigger trigger, final List<ActionSpec> actions) {
        int seq = 0;
        for (final ActionSpec spec : actions) {
            final TreasuryCfpAction action = new TreasuryCfpAction();
            action.setTriggerId(trigger.getId());
            action.setSequenceNumber(++seq);
            action.setDescription(spec.description());
            action.setOwnerRoleId(spec.ownerRoleId());
            action.setOwnerUserId(spec.ownerUserId());
            action.setFundingLineId(spec.fundingLineId());
            this.actionRepository.save(action);
        }
        this.actionRepository.flush();
    }

    private JsonObject json(final TreasuryCfpTrigger t) {
        final JsonObject json = new JsonObject();
        json.addProperty("code", t.getCode());
        json.addProperty("name", t.getName());
        json.addProperty("conditionType", t.getConditionType().name());
        if (t.getLimitId() != null) {
            json.addProperty("limitId", t.getLimitId());
        }
        if (t.getMetric() != null) {
            json.addProperty("metric", t.getMetric().name());
        }
        if (t.getLevel() != null) {
            json.addProperty("level", t.getLevel().name());
        }
        if (t.getSurvivalDays() != null) {
            json.addProperty("survivalDays", t.getSurvivalDays().toPlainString());
        }
        if (t.getScenarioCode() != null) {
            json.addProperty("scenarioCode", t.getScenarioCode());
        }
        json.addProperty("stage", t.getStage().name());
        if (t.getNotifyRoleId() != null) {
            json.addProperty("notifyRoleId", t.getNotifyRoleId());
        }
        if (t.getDescription() != null) {
            json.addProperty("description", t.getDescription());
        }
        final JsonArray actions = new JsonArray();
        for (final TreasuryCfpAction a : this.actionRepository.findByTriggerIdOrderBySequenceNumberAsc(t.getId())) {
            final JsonObject action = new JsonObject();
            action.addProperty("description", a.getDescription());
            if (a.getOwnerRoleId() != null) {
                action.addProperty("ownerRoleId", a.getOwnerRoleId());
            }
            if (a.getOwnerUserId() != null) {
                action.addProperty("ownerUserId", a.getOwnerUserId());
            }
            if (a.getFundingLineId() != null) {
                action.addProperty("fundingLineId", a.getFundingLineId());
            }
            actions.add(action);
        }
        json.add("actions", actions);
        return json;
    }

    private TreasuryCfpTriggerData data(final TreasuryCfpTrigger t) {
        return new TreasuryCfpTriggerData(t.getId(), t.getCode(), t.getName(), t.getConditionType().name(), t.getLimitId(),
                t.getMetric() == null ? null : t.getMetric().name(), t.getLevel() == null ? null : t.getLevel().name(), t.getSurvivalDays(),
                t.getScenarioCode(), t.getStage().name(), t.getStatus().name(), t.getNotifyRoleId(), t.getDescription(),
                this.actionRepository
                        .findByTriggerIdOrderBySequenceNumberAsc(t.getId()).stream().map(a -> new TreasuryCfpTriggerData.Action(a.getId(),
                                a.getSequenceNumber(), a.getDescription(), a.getOwnerRoleId(), a.getOwnerUserId(), a.getFundingLineId()))
                        .toList());
    }

    private TreasuryCfpActivationData data(final TreasuryCfpActivation a) {
        return new TreasuryCfpActivationData(a.getId(), a.getTriggerId(), a.getTriggerCode(), a.getStage().name(), a.getSourceType(),
                a.getSourceId(), a.getAsOfDate(), a.getCurrencyCode(), a.getOfficeId(), a.getScenarioCode(), a.getDetail(),
                a.getOccurrences(), a.getStatus().name(), a.getOpenedBy(), a.getOpenedOnUtc(), a.getClosedBy(), a.getClosedOnUtc(),
                a.getCloseComment(),
                this.activationActionRepository.findByActivationIdOrderBySequenceNumberAsc(a.getId()).stream()
                        .map(x -> new TreasuryCfpActivationData.Action(x.getId(), x.getSequenceNumber(), x.getDescription(),
                                x.getOwnerRoleId(), x.getOwnerUserId(), x.getFundingLineId(), x.getStatus().name(), x.getCompletedBy(),
                                x.getCompletedOnUtc(), x.getComment()))
                        .toList());
    }

    private TreasuryCfpTrigger findTrigger(final Long triggerId) {
        return this.triggerRepository.findById(triggerId).orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "trigger.not.found",
                "CFP trigger " + triggerId + " does not exist", triggerId));
    }

    private TreasuryCfpActivation findActivation(final Long activationId) {
        return this.activationRepository.findById(activationId).orElseThrow(() -> new TreasuryWorkflowException(ENTITY,
                "activation.not.found", "CFP activation " + activationId + " does not exist", activationId));
    }

    private TreasuryCfpActivation openActivation(final Long activationId) {
        final TreasuryCfpActivation activation = findActivation(activationId);
        if (activation.getStatus() != ActivationStatus.OPEN) {
            throw new TreasuryWorkflowException(ENTITY, "activation.closed", "CFP activation " + activationId + " is closed", activationId);
        }
        return activation;
    }

    private static String amount(final BigDecimal value) {
        return value == null ? "none" : value.stripTrailingZeros().toPlainString();
    }

    private static String cut(final String text, final int length) {
        return text == null || text.length() <= length ? text : text.substring(0, length);
    }

    private static CommandProcessingResult result(final JsonCommand command, final Long id, final Long subId,
            final Map<String, Object> changes) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(id).withSubEntityId(subId)
                .with(new LinkedHashMap<>(changes)).build();
    }
}
