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
package com.micropay.fraud.engine.stats;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StatsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AlertStats stats(String tenantId, Instant from, Instant to) {
        final Scope period = new Scope(tenantId, from, to);
        final Scope backlog = new Scope(tenantId, null, null);
        final Map<String, Long> byType = countBy("alert_type", period);
        final long total = byType.values().stream().mapToLong(Long::longValue).sum();
        return new AlertStats(from, to, total, transactionsScored(period), backlog(backlog), byType, countBy("severity", period),
                countBy("status", period), countBy("COALESCE(verdict, 'NONE')", period), daily(period), reasons(period));
    }

    private Map<String, Long> countBy(String expression, Scope scope) {
        final List<Object> params = new ArrayList<>();
        final String sql = "SELECT " + expression + " AS k, COUNT(*) AS n FROM fe_alert a" + scope.where("a", "created_at", params)
                + " GROUP BY " + expression + " ORDER BY " + expression;
        final Map<String, Long> counts = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            counts.put(rs.getString("k"), rs.getLong("n"));
        }, params.toArray());
        return counts;
    }

    private AlertStats.Backlog backlog(Scope scope) {
        final List<Object> params = new ArrayList<>();
        final String sql = "SELECT COUNT(*) AS total, SUM(CASE WHEN severity = 'HIGH' THEN 1 ELSE 0 END) AS high, "
                + "SUM(CASE WHEN severity = 'MEDIUM' THEN 1 ELSE 0 END) AS medium, SUM(CASE WHEN severity = 'LOW' THEN 1 ELSE 0 END) AS low, "
                + "SUM(CASE WHEN assigned_to IS NULL THEN 1 ELSE 0 END) AS unassigned FROM fe_alert a"
                + scope.where("a", "created_at", params) + (params.isEmpty() ? " WHERE" : " AND") + " a.status <> 'CLOSED'";
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new AlertStats.Backlog(rs.getLong("total"), rs.getLong("high"),
                rs.getLong("medium"), rs.getLong("low"), rs.getLong("unassigned")), params.toArray());
    }

    private long transactionsScored(Scope scope) {
        final List<Object> params = new ArrayList<>();
        final Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fe_transaction t" + scope.where("t", "occurred_at", params),
                Long.class, params.toArray());
        return count == null ? 0 : count;
    }

    private List<AlertStats.Day> daily(Scope scope) {
        final List<Object> params = new ArrayList<>();
        final String sql = "SELECT CAST(a.created_at AS DATE) AS d, "
                + "SUM(CASE WHEN a.alert_type = 'TRANSACTION' THEN 1 ELSE 0 END) AS txn, "
                + "SUM(CASE WHEN a.alert_type = 'WATCHLIST' THEN 1 ELSE 0 END) AS wl FROM fe_alert a"
                + scope.where("a", "created_at", params) + " GROUP BY CAST(a.created_at AS DATE)";
        final Map<LocalDate, AlertStats.Day> days = new TreeMap<>();
        jdbcTemplate.query(sql, rs -> {
            final LocalDate date = rs.getDate("d").toLocalDate();
            days.put(date, new AlertStats.Day(date, rs.getLong("txn"), rs.getLong("wl")));
        }, params.toArray());
        return List.copyOf(days.values());
    }

    private List<AlertStats.ReasonStats> reasons(Scope scope) {
        final List<Object> params = new ArrayList<>();
        final String sql = "SELECT r.code, COUNT(DISTINCT a.id) AS alerts, "
                + "COUNT(DISTINCT CASE WHEN a.verdict = 'FRAUD' THEN a.id END) AS fraud, "
                + "COUNT(DISTINCT CASE WHEN a.verdict = 'NOT_FRAUD' THEN a.id END) AS not_fraud "
                + "FROM fe_alert_reason r JOIN fe_alert a ON a.id = r.alert_id" + scope.where("a", "created_at", params)
                + " GROUP BY r.code ORDER BY alerts DESC, r.code";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AlertStats.ReasonStats(rs.getString("code"), rs.getLong("alerts"),
                rs.getLong("fraud"), rs.getLong("not_fraud")), params.toArray());
    }

    private record Scope(String tenantId, Instant from, Instant to) {

        String where(String alias, String timeColumn, List<Object> params) {
            final StringBuilder sql = new StringBuilder();
            if (tenantId != null) {
                sql.append(sql.isEmpty() ? " WHERE " : " AND ").append(alias).append(".tenant_id = ?");
                params.add(tenantId);
            }
            if (from != null) {
                sql.append(sql.isEmpty() ? " WHERE " : " AND ").append(alias).append('.').append(timeColumn).append(" >= ?");
                params.add(Timestamp.from(from));
            }
            if (to != null) {
                sql.append(sql.isEmpty() ? " WHERE " : " AND ").append(alias).append('.').append(timeColumn).append(" < ?");
                params.add(Timestamp.from(to));
            }
            return sql.toString();
        }
    }
}
