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
package com.micropay.fraud.engine.alert;

import com.micropay.fraud.engine.event.AccountType;
import com.micropay.fraud.engine.event.TransactionEvent;
import com.micropay.fraud.engine.event.TransactionKind;
import com.micropay.fraud.engine.rule.RuleHit;
import com.micropay.fraud.engine.scoring.RiskAssessment;
import com.micropay.fraud.engine.scoring.Severity;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AlertRepository {

    private static final String COLUMNS = "id, tenant_id, transaction_ref, account_type, account_id, client_id, kind, amount, currency, occurred_at, "
            + "risk_score, severity, status, verdict, assigned_to, note, fineract_hold_id, created_at, updated_at";
    private static final RowMapper<AlertData> MAPPER = AlertRepository::map;
    private static final int MAX_DETAIL = 500;

    private final JdbcTemplate jdbcTemplate;

    public Long insert(Long transactionRef, TransactionEvent e, RiskAssessment risk, Instant now) {
        final String sql = "INSERT INTO fe_alert (tenant_id, transaction_ref, account_type, account_id, client_id, kind, amount, currency, "
                + "occurred_at, risk_score, severity, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            final PreparedStatement ps = con.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, e.tenantId());
            ps.setLong(2, transactionRef);
            ps.setString(3, e.accountType().name());
            ps.setLong(4, e.accountId());
            ps.setObject(5, e.clientId());
            ps.setString(6, e.kind().name());
            ps.setBigDecimal(7, e.amount());
            ps.setString(8, e.currency());
            ps.setTimestamp(9, Timestamp.from(e.occurredAt()));
            ps.setInt(10, risk.score());
            ps.setString(11, risk.severity().name());
            ps.setString(12, AlertStatus.NEW.name());
            ps.setTimestamp(13, Timestamp.from(now));
            ps.setTimestamp(14, Timestamp.from(now));
            return ps;
        }, keyHolder);
        final Long alertId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        for (RuleHit reason : risk.reasons()) {
            final String detail = reason.detail() == null || reason.detail().length() <= MAX_DETAIL ? reason.detail()
                    : reason.detail().substring(0, MAX_DETAIL);
            jdbcTemplate.update("INSERT INTO fe_alert_reason (alert_id, code, score, detail) VALUES (?, ?, ?, ?)", alertId, reason.code(),
                    reason.score(), detail);
        }
        return alertId;
    }

    public Optional<AlertData> findById(Long id) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM fe_alert WHERE id = ?", MAPPER, id).stream().findFirst()
                .map(alert -> alert.withReasons(reasons(alert.id())));
    }

    public List<AlertData> find(AlertFilter filter, int offset, int limit) {
        final List<Object> params = new ArrayList<>();
        final String where = where(filter, params);
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM fe_alert" + where + " ORDER BY risk_score DESC, id DESC LIMIT ? OFFSET ?",
                MAPPER, params.toArray());
    }

    public long count(AlertFilter filter) {
        final List<Object> params = new ArrayList<>();
        final Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fe_alert" + where(filter, params), Long.class,
                params.toArray());
        return count == null ? 0 : count;
    }

    public void update(Long id, AlertStatus status, AlertVerdict verdict, String assignedTo, String note, Instant now) {
        jdbcTemplate.update("UPDATE fe_alert SET status = ?, verdict = ?, assigned_to = ?, note = ?, updated_at = ? WHERE id = ?",
                status.name(), verdict == null ? null : verdict.name(), assignedTo, note, Timestamp.from(now), id);
    }

    public void recordHold(Long id, Long holdId, Instant now) {
        jdbcTemplate.update("UPDATE fe_alert SET fineract_hold_id = ?, updated_at = ? WHERE id = ?", holdId, Timestamp.from(now), id);
    }

    private List<RuleHit> reasons(Long alertId) {
        return jdbcTemplate.query("SELECT code, score, detail FROM fe_alert_reason WHERE alert_id = ? ORDER BY score DESC, id",
                (rs, rowNum) -> new RuleHit(rs.getString("code"), rs.getInt("score"), rs.getString("detail")), alertId);
    }

    private static String where(AlertFilter filter, List<Object> params) {
        final StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        if (filter.tenantId() != null) {
            sql.append(" AND tenant_id = ?");
            params.add(filter.tenantId());
        }
        if (filter.status() != null) {
            sql.append(" AND status = ?");
            params.add(filter.status().name());
        }
        if (filter.severity() != null) {
            sql.append(" AND severity = ?");
            params.add(filter.severity().name());
        }
        if (filter.clientId() != null) {
            sql.append(" AND client_id = ?");
            params.add(filter.clientId());
        }
        if (filter.from() != null) {
            sql.append(" AND occurred_at >= ?");
            params.add(Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND occurred_at < ?");
            params.add(Timestamp.from(filter.to()));
        }
        return sql.toString();
    }

    private static AlertData map(ResultSet rs, int rowNum) throws SQLException {
        final String verdict = rs.getString("verdict");
        return new AlertData(rs.getLong("id"), rs.getString("tenant_id"), rs.getLong("transaction_ref"),
                AccountType.valueOf(rs.getString("account_type")), rs.getLong("account_id"), rs.getObject("client_id", Long.class),
                TransactionKind.valueOf(rs.getString("kind")), rs.getBigDecimal("amount"), rs.getString("currency"),
                rs.getTimestamp("occurred_at").toInstant(), rs.getInt("risk_score"), Severity.valueOf(rs.getString("severity")),
                AlertStatus.valueOf(rs.getString("status")), verdict == null ? null : AlertVerdict.valueOf(verdict),
                rs.getString("assigned_to"), rs.getString("note"), rs.getObject("fineract_hold_id", Long.class),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), List.of());
    }
}
