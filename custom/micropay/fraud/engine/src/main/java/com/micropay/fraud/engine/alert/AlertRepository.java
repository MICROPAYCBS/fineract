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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final String COLUMNS = "id, tenant_id, alert_type, transaction_ref, account_type, account_id, client_id, kind, amount, currency, occurred_at, "
            + "risk_score, severity, status, verdict, assigned_to, note, fineract_hold_id, created_at, updated_at";
    private static final RowMapper<AlertData> MAPPER = AlertRepository::map;
    private static final int MAX_DETAIL = 500;

    private final JdbcTemplate jdbcTemplate;

    public Long insert(Long transactionRef, TransactionEvent e, RiskAssessment risk, Instant now) {
        final String sql = "INSERT INTO fe_alert (tenant_id, transaction_ref, account_type, account_id, client_id, kind, amount, currency, "
                + "occurred_at, risk_score, severity, status, created_at, updated_at, alert_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            ps.setString(15, AlertType.TRANSACTION.name());
            return ps;
        }, keyHolder);
        final Long alertId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        insertReasons(alertId, risk.reasons());
        return alertId;
    }

    /**
     * A watchlist alert for one client, with one reason and one match row per listed entry it matched.
     */
    public Long insertWatchlist(String tenantId, Long clientId, int score, Severity severity, List<RuleHit> reasons,
            List<WatchlistMatchData> matches, Instant now) {
        final String sql = "INSERT INTO fe_alert (tenant_id, alert_type, client_id, occurred_at, risk_score, severity, status, created_at, "
                + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            final PreparedStatement ps = con.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, tenantId);
            ps.setString(2, AlertType.WATCHLIST.name());
            ps.setLong(3, clientId);
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setInt(5, score);
            ps.setString(6, severity.name());
            ps.setString(7, AlertStatus.NEW.name());
            ps.setTimestamp(8, Timestamp.from(now));
            ps.setTimestamp(9, Timestamp.from(now));
            return ps;
        }, keyHolder);
        final Long alertId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        insertReasons(alertId, reasons);
        for (WatchlistMatchData m : matches) {
            jdbcTemplate.update(
                    "INSERT INTO fe_watchlist_match (alert_id, tenant_id, client_id, source, entry_uid, entry_name, matched_name, client_name, "
                            + "score, birth_date_check, programs) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    alertId, tenantId, clientId, m.source(), m.entryUid(), cut(m.entryName(), MAX_DETAIL), cut(m.matchedName(), MAX_DETAIL),
                    cut(m.clientName(), 300), m.score(), m.birthDateCheck(),
                    m.programs().isEmpty() ? null : cut(String.join("|", m.programs()), MAX_DETAIL));
        }
        return alertId;
    }

    /**
     * Whether this client was already alerted on this listed entry. Each pair is alerted once, so a match an analyst
     * cleared as a false positive does not come back after every refresh.
     */
    public boolean watchlistPairAlerted(String tenantId, Long clientId, String source, String entryUid) {
        final Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fe_watchlist_match WHERE tenant_id = ? AND client_id = ? AND source = ? AND entry_uid = ?",
                Integer.class, tenantId, clientId, source, entryUid);
        return count != null && count > 0;
    }

    private void insertReasons(Long alertId, List<RuleHit> reasons) {
        for (RuleHit reason : reasons) {
            jdbcTemplate.update("INSERT INTO fe_alert_reason (alert_id, code, score, detail) VALUES (?, ?, ?, ?)", alertId, reason.code(),
                    reason.score(), cut(reason.detail(), MAX_DETAIL));
        }
    }

    private List<WatchlistMatchData> watchlistMatches(Long alertId) {
        return jdbcTemplate.query(
                "SELECT source, entry_uid, entry_name, matched_name, client_name, score, birth_date_check, programs FROM fe_watchlist_match "
                        + "WHERE alert_id = ? ORDER BY score DESC, id",
                (rs, rowNum) -> {
                    final String programs = rs.getString("programs");
                    return new WatchlistMatchData(rs.getString("source"), rs.getString("entry_uid"), rs.getString("entry_name"),
                            rs.getString("matched_name"), rs.getString("client_name"), rs.getInt("score"), rs.getString("birth_date_check"),
                            programs == null ? List.of() : List.of(programs.split("\\|")));
                }, alertId);
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public Optional<AlertData> findById(Long id) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM fe_alert WHERE id = ?", MAPPER, id).stream().findFirst()
                .map(alert -> alert.withDetails(reasons(alert.id()), watchlistMatches(alert.id()), history(alert.id())));
    }

    public List<AlertData> find(AlertFilter filter, int offset, int limit) {
        return find(filter, AlertSort.SCORE, offset, limit);
    }

    /**
     * One page of the queue, each alert with its strongest reason.
     */
    public List<AlertData> find(AlertFilter filter, AlertSort sort, int offset, int limit) {
        final List<Object> params = new ArrayList<>();
        final String where = where(filter, params);
        params.add(limit);
        params.add(offset);
        final String order = sort == AlertSort.NEWEST ? " ORDER BY created_at DESC, id DESC" : " ORDER BY risk_score DESC, id DESC";
        final List<AlertData> page = jdbcTemplate.query("SELECT " + COLUMNS + " FROM fe_alert" + where + order + " LIMIT ? OFFSET ?",
                MAPPER, params.toArray());
        if (page.isEmpty()) {
            return page;
        }
        final Map<Long, RuleHit> top = new HashMap<>();
        final String ids = String.join(",", Collections.nCopies(page.size(), "?"));
        jdbcTemplate.query(
                "SELECT alert_id, code, score, detail FROM fe_alert_reason WHERE alert_id IN (" + ids + ") ORDER BY score DESC, id",
                (ResultSet rs) -> {
                    top.putIfAbsent(rs.getLong("alert_id"), new RuleHit(rs.getString("code"), rs.getInt("score"), rs.getString("detail")));
                }, page.stream().map(AlertData::id).toArray());
        return page.stream().map(a -> a.withTopReason(top.get(a.id()))).toList();
    }

    public long count(AlertFilter filter) {
        final List<Object> params = new ArrayList<>();
        final Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fe_alert" + where(filter, params), Long.class,
                params.toArray());
        return count == null ? 0 : count;
    }

    public void update(Long id, AlertStatus status, AlertVerdict verdict, String assignedTo, String note, String actor, Instant now) {
        final String verdictName = verdict == null ? null : verdict.name();
        jdbcTemplate.update("UPDATE fe_alert SET status = ?, verdict = ?, assigned_to = ?, note = ?, updated_at = ? WHERE id = ?",
                status.name(), verdictName, assignedTo, note, Timestamp.from(now), id);
        jdbcTemplate.update(
                "INSERT INTO fe_alert_history (alert_id, changed_at, actor, status, verdict, assigned_to, note) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, Timestamp.from(now), actor, status.name(), verdictName, assignedTo, note);
    }

    private List<AlertHistoryEntry> history(Long alertId) {
        return jdbcTemplate.query(
                "SELECT changed_at, actor, status, verdict, assigned_to, note FROM fe_alert_history WHERE alert_id = ? ORDER BY changed_at, id",
                (rs, rowNum) -> {
                    final String verdict = rs.getString("verdict");
                    return new AlertHistoryEntry(rs.getTimestamp("changed_at").toInstant(), rs.getString("actor"),
                            AlertStatus.valueOf(rs.getString("status")), verdict == null ? null : AlertVerdict.valueOf(verdict),
                            rs.getString("assigned_to"), rs.getString("note"));
                }, alertId);
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
        if (filter.type() != null) {
            sql.append(" AND alert_type = ?");
            params.add(filter.type().name());
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
        if (filter.assignedTo() != null) {
            sql.append(" AND assigned_to = ?");
            params.add(filter.assignedTo());
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
        final String accountType = rs.getString("account_type");
        final String kind = rs.getString("kind");
        return new AlertData(rs.getLong("id"), rs.getString("tenant_id"), AlertType.valueOf(rs.getString("alert_type")),
                rs.getObject("transaction_ref", Long.class), accountType == null ? null : AccountType.valueOf(accountType),
                rs.getObject("account_id", Long.class), rs.getObject("client_id", Long.class),
                kind == null ? null : TransactionKind.valueOf(kind), rs.getBigDecimal("amount"), rs.getString("currency"),
                rs.getTimestamp("occurred_at").toInstant(), rs.getInt("risk_score"), Severity.valueOf(rs.getString("severity")),
                AlertStatus.valueOf(rs.getString("status")), verdict == null ? null : AlertVerdict.valueOf(verdict),
                rs.getString("assigned_to"), rs.getString("note"), rs.getObject("fineract_hold_id", Long.class),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), List.of(), List.of(), List.of());
    }
}
