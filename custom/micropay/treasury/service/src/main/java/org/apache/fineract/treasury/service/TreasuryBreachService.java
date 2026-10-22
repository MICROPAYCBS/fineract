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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryBreachData;
import org.apache.fineract.treasury.domain.TreasuryBreach;
import org.apache.fineract.treasury.domain.TreasuryBreachComment;
import org.apache.fineract.treasury.domain.TreasuryBreachCommentRepository;
import org.apache.fineract.treasury.domain.TreasuryBreachRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.limits.BreachStatus;
import org.apache.fineract.treasury.serialization.TreasuryLimitDataValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The breach log: OPEN breaches are acknowledged ({@code ACKNOWLEDGE_TREASURYBREACH}, comment optional) and resolved
 * ({@code RESOLVE_TREASURYBREACH}, with a comment); anyone with {@code COMMENT_TREASURYBREACH} adds to the thread. The
 * resolver may be the acknowledger. Resolution is a human's: a value back within the levels only marks it cleared.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryBreachService {

    static final String ENTITY = "breach";

    private final TreasuryLimitDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryBreachRepository breachRepository;
    private final TreasuryBreachCommentRepository commentRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
    public CommandProcessingResult acknowledge(final Long breachId, final JsonCommand command) {
        final String comment = this.validator.validateBreachAction(command.json(), false);
        final TreasuryBreach breach = find(breachId);
        if (!breach.getStatus().canAcknowledge()) {
            throw new TreasuryWorkflowException(ENTITY, "not.open", "Breach " + breachId + " is " + breach.getStatus() + ", not OPEN",
                    breachId);
        }
        final Long userId = this.actor.userId(command);
        breach.setStatus(BreachStatus.ACKNOWLEDGED);
        breach.setAcknowledgedBy(userId);
        breach.setAcknowledgedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.breachRepository.saveAndFlush(breach);
        if (comment != null) {
            comment(breachId, "ACKNOWLEDGE", comment, userId);
        }
        return result(command, breach, null, Map.of("status", BreachStatus.ACKNOWLEDGED.name()));
    }

    @Transactional
    public CommandProcessingResult resolve(final Long breachId, final JsonCommand command) {
        final String comment = this.validator.validateBreachAction(command.json(), true);
        final TreasuryBreach breach = find(breachId);
        if (!breach.getStatus().canResolve()) {
            throw new TreasuryWorkflowException(ENTITY, "already.resolved", "Breach " + breachId + " is already resolved", breachId);
        }
        final Long userId = this.actor.userId(command);
        breach.setStatus(BreachStatus.RESOLVED);
        breach.setResolvedBy(userId);
        breach.setResolvedOnDate(DateUtils.getBusinessLocalDate());
        breach.setResolvedOnUtc(DateUtils.getAuditOffsetDateTime());
        breach.setResolutionComment(comment);
        this.breachRepository.saveAndFlush(breach);
        comment(breachId, "RESOLVE", comment, userId);
        return result(command, breach, null, Map.of("status", BreachStatus.RESOLVED.name()));
    }

    @Transactional
    public CommandProcessingResult comment(final Long breachId, final JsonCommand command) {
        final String text = this.validator.validateBreachAction(command.json(), true);
        final TreasuryBreach breach = find(breachId);
        final TreasuryBreachComment comment = comment(breachId, "USER", text, this.actor.userId(command));
        return result(command, breach, comment.getId(),
                Map.of("comments", this.commentRepository.findByBreachIdOrderByIdAsc(breachId).size()));
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public List<TreasuryBreachData> retrieveAll(final String status, final String metric, final Long limitId, final String level,
            final String currency, final String scopeType, final Long scopeId, final LocalDate from, final LocalDate to) {
        final StringBuilder sql = new StringBuilder("SELECT id FROM m_treasury_breach WHERE 1 = 1");
        final MapSqlParameterSource params = new MapSqlParameterSource();
        filter(sql, params, "status", status);
        filter(sql, params, "metric", metric);
        filter(sql, params, "level_hit", level);
        filter(sql, params, "currency_code", currency);
        filter(sql, params, "scope_type", scopeType);
        if (limitId != null) {
            sql.append(" AND limit_id = :limitId");
            params.addValue("limitId", limitId);
        }
        if (scopeId != null) {
            sql.append(" AND scope_id = :scopeId");
            params.addValue("scopeId", scopeId);
        }
        if (from != null) {
            sql.append(" AND last_seen_on >= :from");
            params.addValue("from", from);
        }
        if (to != null) {
            sql.append(" AND first_seen_on <= :to");
            params.addValue("to", to);
        }
        sql.append(" ORDER BY id DESC LIMIT 500");
        return this.jdbcTemplate.queryForList(sql.toString(), params, Long.class).stream().map(id -> data(find(id), false)).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryBreachData retrieveOne(final Long breachId) {
        return data(find(breachId), true);
    }

    private static void filter(final StringBuilder sql, final MapSqlParameterSource params, final String column, final String value) {
        if (value != null && !value.isBlank()) {
            sql.append(" AND ").append(column).append(" = :").append(column);
            params.addValue(column, value.trim().toUpperCase());
        }
    }

    private TreasuryBreachData data(final TreasuryBreach b, final boolean withComments) {
        return new TreasuryBreachData(b.getId(), b.getLimitId(), b.getLimitCode(), b.getLimitVersion(), b.getMetric().name(),
                b.getMetricKey(), b.getScopeType().name(), b.getScopeId(), b.getScopeLabel(), b.getCurrencyCode(), b.getAsOfDate(),
                b.getObservedValue(), b.getLevel().name(), b.getLastLevel() == null ? null : b.getLastLevel().name(),
                b.getComparison() == null ? null : b.getComparison().name(), b.getWarningLevel(), b.getHardLevel(), b.getUnit().name(),
                b.getSourceType(), b.getSourceId(), b.getEvaluationId(), b.getDetail(), b.getStatus().name(), b.getFirstSeenOn(),
                b.getLastSeenOn(), b.getOccurrences(), b.getEscalatedOn(), b.getClearedOn(), b.getAcknowledgedBy(),
                b.getAcknowledgedOnUtc(), b.getResolvedBy(), b.getResolvedOnDate(), b.getResolvedOnUtc(), b.getResolutionComment(),
                b.getCreatedOnUtc(),
                withComments ? this.commentRepository.findByBreachIdOrderByIdAsc(b.getId()).stream().map(
                        c -> new TreasuryBreachData.Comment(c.getId(), c.getKind(), c.getComment(), c.getCreatedBy(), c.getCreatedOnUtc()))
                        .toList() : List.of());
    }

    private TreasuryBreachComment comment(final Long breachId, final String kind, final String text, final Long userId) {
        final TreasuryBreachComment comment = new TreasuryBreachComment();
        comment.setBreachId(breachId);
        comment.setKind(kind);
        comment.setComment(text);
        comment.setCreatedBy(userId);
        comment.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        return this.commentRepository.saveAndFlush(comment);
    }

    private TreasuryBreach find(final Long breachId) {
        return this.breachRepository.findById(breachId).orElseThrow(
                () -> new TreasuryWorkflowException(ENTITY, "not.found", "Treasury breach " + breachId + " does not exist", breachId));
    }

    private static CommandProcessingResult result(final JsonCommand command, final TreasuryBreach breach, final Long subResourceId,
            final Map<String, Object> changes) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(breach.getId())
                .withSubEntityId(subResourceId).with(new LinkedHashMap<>(changes)).build();
    }
}
