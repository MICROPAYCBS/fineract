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

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryLimitData;
import org.apache.fineract.treasury.data.TreasuryLimitEvaluationData;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyRepository;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.domain.TreasuryLimitEvaluation;
import org.apache.fineract.treasury.domain.TreasuryLimitEvaluationLine;
import org.apache.fineract.treasury.domain.TreasuryLimitEvaluationLineRepository;
import org.apache.fineract.treasury.domain.TreasuryLimitEvaluationRepository;
import org.apache.fineract.treasury.domain.TreasuryLimitHistory;
import org.apache.fineract.treasury.domain.TreasuryLimitHistoryRepository;
import org.apache.fineract.treasury.domain.TreasuryLimitRepository;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.limits.EvaluationContext;
import org.apache.fineract.treasury.limits.LimitScopeType;
import org.apache.fineract.treasury.limits.LimitStatus;
import org.apache.fineract.treasury.serialization.TreasuryLimitDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryLimitDataValidator.EvaluateRequest;
import org.apache.fineract.treasury.serialization.TreasuryLimitDataValidator.LimitRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limits: created INACTIVE, activated and deactivated; every create, update, activation and deactivation is a version
 * in the history, and breaches keep the version and levels they were compared with. Create, update and activate can be
 * put under maker-checker ({@code CREATE_TREASURYLIMIT} and so on); the maker is credited.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryLimitService {

    static final String ENTITY = "limit";

    private final TreasuryLimitDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryLimitRepository limitRepository;
    private final TreasuryLimitHistoryRepository historyRepository;
    private final TreasuryLimitEvaluationRepository evaluationRepository;
    private final TreasuryLimitEvaluationLineRepository evaluationLineRepository;
    private final TreasuryRunRepository runRepository;
    private final TreasuryCounterpartyRepository counterpartyRepository;
    private final TreasuryAccountRepository accountRepository;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final TreasuryLimitEngine engine;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final LimitRequest request = this.validator.validateLimit(command.json(), null);
        if (this.limitRepository.findByCode(request.code()).isPresent()) {
            throw new TreasuryWorkflowException(ENTITY, "code.exists", "A limit with code " + request.code() + " exists", request.code());
        }
        checkReferences(request);
        final TreasuryLimit limit = new TreasuryLimit();
        apply(limit, request);
        limit.setStatus(LimitStatus.INACTIVE);
        limit.setLimitVersion(1);
        limit.setCreatedBy(this.actor.userId(command));
        limit.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.limitRepository.saveAndFlush(limit);
        history(limit, "CREATE", limit.getCreatedBy());
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("code", limit.getCode());
        changes.put("version", limit.getLimitVersion());
        changes.put("status", limit.getStatus().name());
        return result(command, limit, changes);
    }

    /** Any field but the code; a new version. An active limit applies from its next evaluation. */
    @Transactional
    public CommandProcessingResult update(final Long limitId, final JsonCommand command) {
        final TreasuryLimit limit = find(limitId);
        final LimitRequest request = this.validator.validateLimit(command.json(), json(limit));
        checkReferences(request);
        apply(limit, request);
        limit.setLimitVersion(limit.getLimitVersion() + 1);
        limit.setLastModifiedBy(this.actor.userId(command));
        limit.setLastModifiedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.limitRepository.saveAndFlush(limit);
        history(limit, "UPDATE", limit.getLastModifiedBy());
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("version", limit.getLimitVersion());
        return result(command, limit, changes);
    }

    @Transactional
    public CommandProcessingResult activate(final Long limitId, final JsonCommand command) {
        this.validator.validateNote(command.json());
        final TreasuryLimit limit = find(limitId);
        if (limit.getStatus() == LimitStatus.ACTIVE) {
            throw new TreasuryWorkflowException(ENTITY, "already.active", "Limit " + limit.getCode() + " is already active", limitId);
        }
        final Long userId = this.actor.userId(command);
        limit.setStatus(LimitStatus.ACTIVE);
        limit.setActivatedBy(userId);
        limit.setActivatedOnDate(DateUtils.getBusinessLocalDate());
        this.limitRepository.saveAndFlush(limit);
        history(limit, "ACTIVATE", userId);
        return result(command, limit, Map.of("status", LimitStatus.ACTIVE.name()));
    }

    @Transactional
    public CommandProcessingResult deactivate(final Long limitId, final JsonCommand command) {
        this.validator.validateNote(command.json());
        final TreasuryLimit limit = find(limitId);
        if (limit.getStatus() != LimitStatus.ACTIVE) {
            throw new TreasuryWorkflowException(ENTITY, "not.active", "Limit " + limit.getCode() + " is not active", limitId);
        }
        limit.setStatus(LimitStatus.INACTIVE);
        this.limitRepository.saveAndFlush(limit);
        history(limit, "DEACTIVATE", this.actor.userId(command));
        return result(command, limit, Map.of("status", LimitStatus.INACTIVE.name()));
    }

    /**
     * Every active limit of the scope: of a run ({@code runId}, with the ladder or ratios run it belongs with), or of a
     * date (default the business date), currency and office (none for the institution) with that scope's latest runs.
     */
    @Transactional
    public CommandProcessingResult evaluate(final JsonCommand command) {
        final EvaluateRequest request = this.validator.validateEvaluate(command.json());
        final EvaluationContext context;
        if (request.runId() != null) {
            final TreasuryRun run = this.runRepository.findById(request.runId()).orElseThrow(() -> new TreasuryWorkflowException(ENTITY,
                    "run.not.found", "Treasury run " + request.runId() + " does not exist", request.runId()));
            if (request.asOfDate() != null && !request.asOfDate().equals(run.getAsOfDate())
                    || request.currencyCode() != null && !request.currencyCode().equals(run.getCurrencyCode())
                    || request.officeId() != null && !request.officeId().equals(run.getOfficeId())) {
                throw new TreasuryWorkflowException(ENTITY, "run.scope.differs", "Run " + run.getId() + " is of " + run.getAsOfDate() + ", "
                        + run.getCurrencyCode() + (run.getOfficeId() == null ? ", the institution" : ", office " + run.getOfficeId()),
                        run.getId());
            }
            context = this.engine.contextFor(run);
        } else {
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
            context = this.engine.contextFor(asOf, request.currencyCode(), request.officeId());
        }
        final TreasuryLimitEngine.Summary summary = this.engine.evaluate(context, null, TreasuryLimitEngine.TRIGGER_MANUAL, request.runId(),
                this.actor.userId(command), true);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("asOfDate", context.asOfDate());
        changes.put("currencyCode", context.currencyCode());
        changes.put("officeId", context.officeId());
        changes.put("ladderRunId", context.ladderRun() == null ? null : context.ladderRun().getId());
        changes.put("ratiosRunId", context.ratiosRun() == null ? null : context.ratiosRun().getId());
        changes.put("limits", summary.limits());
        changes.put("breachesCreated", summary.created());
        changes.put("breachesEscalated", summary.escalated());
        changes.put("breachesRepeated", summary.repeated());
        changes.put("breachesCleared", summary.cleared());
        changes.put("usersNotified", summary.notified());
        changes.put("breachIds", summary.breachIds());
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(summary.evaluationId()).with(changes)
                .build();
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public List<TreasuryLimitData> retrieveAll(final String status, final String metric) {
        return this.limitRepository.findAllByOrderByCodeAsc().stream()
                .filter(l -> status == null || l.getStatus().name().equalsIgnoreCase(status.trim()))
                .filter(l -> metric == null || l.getMetric().name().equalsIgnoreCase(metric.trim())).map(l -> data(l, List.of())).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryLimitData retrieveOne(final Long limitId) {
        final TreasuryLimit limit = find(limitId);
        return data(limit,
                this.historyRepository.findByLimitIdOrderByIdAsc(limitId).stream()
                        .map(h -> new TreasuryLimitData.History(h.getLimitVersion(), h.getAction(), h.getChangedBy(), h.getChangedOnUtc(),
                                h.getSnapshot()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public List<TreasuryLimitEvaluationData> retrieveEvaluations(final String currency, final Long officeId, final LocalDate from,
            final LocalDate to) {
        final StringBuilder sql = new StringBuilder("SELECT id FROM m_treasury_limit_evaluation WHERE 1 = 1");
        final MapSqlParameterSource params = new MapSqlParameterSource();
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
        sql.append(" ORDER BY id DESC LIMIT 500");
        return this.jdbcTemplate.queryForList(sql.toString(), params, Long.class).stream()
                .map(id -> evaluation(this.evaluationRepository.findById(id).orElseThrow(), false)).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryLimitEvaluationData retrieveEvaluation(final Long evaluationId) {
        return evaluation(this.evaluationRepository.findById(evaluationId).orElseThrow(() -> new TreasuryWorkflowException(ENTITY,
                "evaluation.not.found", "Limit evaluation " + evaluationId + " does not exist", evaluationId)), true);
    }

    private TreasuryLimitEvaluationData evaluation(final TreasuryLimitEvaluation e, final boolean withLines) {
        return new TreasuryLimitEvaluationData(e.getId(), e.getAsOfDate(), e.getCurrencyCode(), e.getOfficeId(), e.getTriggerType(),
                e.getSourceRunId(), e.getSourceDealId(), e.getLimitsEvaluated(), e.getBreachesCreated(), e.getBreachesEscalated(),
                e.getBreachesRepeated(), e.getBreachesCleared(), e.getCreatedBy(), e.getCreatedOnUtc(),
                withLines
                        ? this.evaluationLineRepository.findByEvaluationIdOrderBySequenceNumberAsc(e.getId()).stream()
                                .map(TreasuryLimitService::line).toList()
                        : List.of());
    }

    private static TreasuryLimitEvaluationData.Line line(final TreasuryLimitEvaluationLine l) {
        return new TreasuryLimitEvaluationData.Line(l.getSequenceNumber(), l.getLimitId(), l.getLimitCode(), l.getLimitVersion(),
                l.getMetric().name(), l.getScopeType().name(), l.getScopeId(), l.getScopeLabel(), l.getObservedValue(),
                l.getComparison() == null ? null : l.getComparison().name(), l.getWarningLevel(), l.getHardLevel(), l.getUnit().name(),
                l.getLevel() == null ? null : l.getLevel().name(), l.getAction().name(), l.getBreachId(), l.getSourceType(),
                l.getSourceId(), l.getDetail());
    }

    // ---------- helpers ----------

    private void checkReferences(final LimitRequest request) {
        if (request.currencyCode() != null && !this.referenceDataLookup.isOrganisationCurrency(request.currencyCode())) {
            throw new TreasuryWorkflowException(ENTITY, "currency.invalid", request.currencyCode() + " is not an organisation currency",
                    request.currencyCode());
        }
        final Long scopeId = request.scopeId();
        if (request.scopeType() == LimitScopeType.OFFICE && !this.referenceDataLookup.officeExists(scopeId)) {
            throw new TreasuryWorkflowException(ENTITY, "office.not.found", "Office " + scopeId + " does not exist", scopeId);
        }
        if (request.scopeType() == LimitScopeType.COUNTERPARTY && !this.counterpartyRepository.existsById(scopeId)) {
            throw new TreasuryWorkflowException(ENTITY, "counterparty.not.found", "Treasury counterparty " + scopeId + " does not exist",
                    scopeId);
        }
        if (request.scopeType() == LimitScopeType.ACCOUNT) {
            final TreasuryAccount account = this.accountRepository.findById(scopeId).orElseThrow(() -> new TreasuryWorkflowException(ENTITY,
                    "account.not.found", "Treasury account " + scopeId + " does not exist", scopeId));
            if (request.currencyCode() != null && !request.currencyCode().equals(account.getCurrencyCode())) {
                throw new TreasuryWorkflowException(ENTITY, "account.currency.differs",
                        "Treasury account " + account.getCode() + " is in " + account.getCurrencyCode(), scopeId);
            }
        }
        if (request.notifyRoleId() != null
                && Objects.requireNonNullElse(this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM m_role WHERE id = :id",
                        new MapSqlParameterSource("id", request.notifyRoleId()), Integer.class), 0) == 0) {
            throw new TreasuryWorkflowException(ENTITY, "role.not.found", "Role " + request.notifyRoleId() + " does not exist",
                    request.notifyRoleId());
        }
    }

    private static void apply(final TreasuryLimit limit, final LimitRequest r) {
        limit.setCode(r.code());
        limit.setName(r.name());
        limit.setDescription(r.description());
        limit.setMetric(r.metric());
        limit.setMetricKey(r.metricKey());
        limit.setGapType(r.gapType());
        limit.setGapBasis(r.gapBasis());
        limit.setTopN(r.topN());
        limit.setScopeType(r.scopeType());
        limit.setScopeId(r.scopeId());
        limit.setCurrencyCode(r.currencyCode());
        limit.setComparison(r.comparison());
        limit.setWarningLevel(r.warningLevel());
        limit.setHardLevel(r.hardLevel());
        limit.setUnit(r.unit());
        limit.setUseOwnLevels(r.useOwnLevels());
        limit.setEffectiveFrom(r.effectiveFrom());
        limit.setEffectiveTo(r.effectiveTo());
        limit.setNotifyRoleId(r.notifyRoleId());
    }

    /** The limit's request fields, as an update is laid over and the history keeps them. */
    static JsonObject json(final TreasuryLimit limit) {
        final JsonObject json = new JsonObject();
        json.addProperty("code", limit.getCode());
        json.addProperty("name", limit.getName());
        put(json, "description", limit.getDescription());
        json.addProperty("metric", limit.getMetric().name());
        put(json, "metricKey", limit.getMetricKey());
        put(json, "gapType", limit.getGapType() == null ? null : limit.getGapType().name());
        put(json, "gapBasis", limit.getGapBasis() == null ? null : limit.getGapBasis().name());
        if (limit.getTopN() != null) {
            json.addProperty("topN", limit.getTopN());
        }
        json.addProperty("scopeType", limit.getScopeType().name());
        if (limit.getScopeId() != null) {
            json.addProperty("scopeId", limit.getScopeId());
        }
        put(json, "currencyCode", limit.getCurrencyCode());
        if (limit.isUseOwnLevels()) {
            json.addProperty("useOwnLevels", true);
        } else {
            put(json, "comparison", limit.getComparison() == null ? null : limit.getComparison().name());
            amount(json, "warningLevel", limit.getWarningLevel());
            amount(json, "hardLevel", limit.getHardLevel());
            json.addProperty("unit", limit.getUnit().name());
        }
        put(json, "effectiveFrom", limit.getEffectiveFrom() == null ? null : limit.getEffectiveFrom().toString());
        put(json, "effectiveTo", limit.getEffectiveTo() == null ? null : limit.getEffectiveTo().toString());
        if (limit.getNotifyRoleId() != null) {
            json.addProperty("notifyRoleId", limit.getNotifyRoleId());
        }
        return json;
    }

    private static void put(final JsonObject json, final String name, final String value) {
        if (value != null) {
            json.addProperty(name, value);
        }
    }

    private static void amount(final JsonObject json, final String name, final BigDecimal value) {
        if (value != null) {
            json.addProperty(name, value.stripTrailingZeros().toPlainString());
        }
    }

    private void history(final TreasuryLimit limit, final String action, final Long userId) {
        final TreasuryLimitHistory history = new TreasuryLimitHistory();
        history.setLimitId(limit.getId());
        history.setLimitVersion(limit.getLimitVersion());
        history.setAction(action);
        final JsonObject snapshot = json(limit);
        snapshot.addProperty("status", limit.getStatus().name());
        history.setSnapshot(snapshot.toString());
        history.setChangedBy(userId);
        history.setChangedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.historyRepository.save(history);
    }

    private static TreasuryLimitData data(final TreasuryLimit l, final List<TreasuryLimitData.History> history) {
        return new TreasuryLimitData(l.getId(), l.getCode(), l.getName(), l.getDescription(), l.getMetric().name(), l.getMetricKey(),
                l.getGapType() == null ? null : l.getGapType().name(), l.getGapBasis() == null ? null : l.getGapBasis().name(), l.getTopN(),
                l.getScopeType().name(), l.getScopeId(), l.getCurrencyCode(), l.getComparison() == null ? null : l.getComparison().name(),
                l.getWarningLevel(), l.getHardLevel(), l.getUnit().name(), l.isUseOwnLevels(), l.getStatus().name(), l.getEffectiveFrom(),
                l.getEffectiveTo(), l.getNotifyRoleId(), l.getLimitVersion(), l.getCreatedBy(), l.getCreatedOnUtc(), l.getLastModifiedBy(),
                l.getLastModifiedOnUtc(), l.getActivatedBy(), l.getActivatedOnDate(), history);
    }

    TreasuryLimit find(final Long limitId) {
        return this.limitRepository.findById(limitId).orElseThrow(
                () -> new TreasuryWorkflowException(ENTITY, "not.found", "Treasury limit " + limitId + " does not exist", limitId));
    }

    private static CommandProcessingResult result(final JsonCommand command, final TreasuryLimit limit, final Map<String, Object> changes) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(limit.getId())
                .with(new LinkedHashMap<>(changes)).build();
    }
}
