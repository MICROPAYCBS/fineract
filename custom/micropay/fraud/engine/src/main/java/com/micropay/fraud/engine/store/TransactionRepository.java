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
package com.micropay.fraud.engine.store;

import com.micropay.fraud.engine.event.AccountType;
import com.micropay.fraud.engine.event.TransactionEvent;
import com.micropay.fraud.engine.event.TransactionKind;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Transaction history the behaviour features are computed from. Reversals are stored but never counted as activity.
 */
@Repository
@RequiredArgsConstructor
public class TransactionRepository {

    private static final String ACTIVE = " AND kind <> 'REVERSAL'";

    private final JdbcTemplate jdbcTemplate;

    public Long insert(TransactionRecord record) {
        final TransactionEvent e = record.event();
        final String sql = "INSERT INTO fe_transaction (tenant_id, event_key, event_type, kind, debit, account_type, account_id, client_id, "
                + "transaction_id, amount, currency, occurred_at, business_date, channel, risk_score, anomaly_score, features) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            final PreparedStatement ps = con.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, e.tenantId());
            ps.setString(2, e.eventKey());
            ps.setString(3, e.eventType());
            ps.setString(4, e.kind().name());
            ps.setBoolean(5, e.kind().isDebit());
            ps.setString(6, e.accountType().name());
            ps.setLong(7, e.accountId());
            ps.setObject(8, e.clientId());
            ps.setObject(9, e.transactionId());
            ps.setBigDecimal(10, e.amount());
            ps.setString(11, e.currency());
            ps.setTimestamp(12, Timestamp.from(e.occurredAt()));
            ps.setDate(13, e.businessDate() == null ? null : Date.valueOf(e.businessDate()));
            ps.setString(14, e.channel());
            ps.setInt(15, record.riskScore());
            ps.setObject(16, record.anomalyScore());
            ps.setString(17, record.features());
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    /**
     * Most recent amounts moving in the same direction (in or out) as the given kind, newest first.
     */
    public List<BigDecimal> recentAmounts(Subject subject, boolean debit, Instant before, int limit) {
        final List<Object> params = new ArrayList<>(subject.whereParams());
        params.add(debit);
        params.add(Timestamp.from(before));
        params.add(limit);
        return jdbcTemplate.queryForList("SELECT amount FROM fe_transaction WHERE " + subject.whereClause() + ACTIVE
                + " AND debit = ? AND occurred_at < ? ORDER BY occurred_at DESC LIMIT ?", BigDecimal.class, params.toArray());
    }

    public int countBetween(Subject subject, Instant from, Instant to) {
        final List<Object> params = new ArrayList<>(subject.whereParams());
        params.add(Timestamp.from(from));
        params.add(Timestamp.from(to));
        final Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fe_transaction WHERE " + subject.whereClause() + ACTIVE + " AND occurred_at >= ? AND occurred_at < ?",
                Integer.class, params.toArray());
        return count == null ? 0 : count;
    }

    /**
     * Money that reached the subject (deposits and disbursals) in the window.
     */
    public BigDecimal sumCreditsBetween(Subject subject, Instant from, Instant to) {
        final List<Object> params = new ArrayList<>(subject.whereParams());
        params.add(TransactionKind.DEPOSIT.name());
        params.add(TransactionKind.LOAN_DISBURSAL.name());
        params.add(Timestamp.from(from));
        params.add(Timestamp.from(to));
        final BigDecimal sum = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM fe_transaction WHERE "
                + subject.whereClause() + " AND kind IN (?, ?) AND occurred_at >= ? AND occurred_at < ?", BigDecimal.class,
                params.toArray());
        return sum == null ? BigDecimal.ZERO : sum;
    }

    public int countAmountsInRange(Subject subject, Instant from, Instant to, BigDecimal low, BigDecimal high) {
        final List<Object> params = new ArrayList<>(subject.whereParams());
        params.add(Timestamp.from(from));
        params.add(Timestamp.from(to));
        params.add(low);
        params.add(high);
        final Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fe_transaction WHERE " + subject.whereClause() + ACTIVE
                + " AND occurred_at >= ? AND occurred_at < ? AND amount >= ? AND amount < ?", Integer.class, params.toArray());
        return count == null ? 0 : count;
    }

    public Optional<Instant> lastActivity(String tenantId, AccountType accountType, Long accountId, Instant before) {
        final Timestamp last = jdbcTemplate.queryForObject(
                "SELECT MAX(occurred_at) FROM fe_transaction WHERE tenant_id = ? AND account_type = ? "
                        + "AND account_id = ? AND kind <> 'REVERSAL' AND occurred_at < ?",
                Timestamp.class, tenantId, accountType.name(), accountId, Timestamp.from(before));
        return Optional.ofNullable(last).map(Timestamp::toInstant);
    }

    public boolean hasAnyActivity(String tenantId, AccountType accountType, Long accountId, Instant before) {
        return lastActivity(tenantId, accountType, accountId, before).isPresent();
    }

    public List<String> recentFeatureVectors(String tenantId, int limit) {
        return jdbcTemplate.queryForList(
                "SELECT features FROM fe_transaction WHERE tenant_id = ? AND features IS NOT NULL ORDER BY occurred_at DESC LIMIT ?",
                String.class, tenantId, limit);
    }

    public List<String> tenants() {
        return jdbcTemplate.queryForList("SELECT DISTINCT tenant_id FROM fe_transaction", String.class);
    }
}
