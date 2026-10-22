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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.treasury.service.TreasuryPositionCalculator.LastSnapshot;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads treasury account balances from the GL.
 *
 * <p>
 * The balance is summed straight from {@code acc_gl_journal_entry} for the account's GL account, office and currency
 * (debits less credits), rather than taken from the latest {@code m_gl_balance_snapshot} plus the entries after it. The
 * GL balance snapshot of a day is not rebuilt when an entry is later posted with an earlier date, or when a Micropay
 * journal entry update changes an amount in place, so snapshot plus later entries can drift from the GL. A sum over the
 * entries of one GL account always equals the GL, and treasury accounts are few.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class TreasuryBalanceReader {

    private static final String BALANCE_SQL = """
            SELECT t.id AS treasury_account_id,
                   COALESCE(SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END), 0) AS gl_balance
            FROM m_treasury_account t
            LEFT JOIN acc_gl_journal_entry je ON je.account_id = t.gl_account_id AND je.office_id = t.office_id
                 AND je.currency_code = t.currency_code
            WHERE t.id IN (:ids)
            GROUP BY t.id
            """;

    private static final String BALANCE_AS_OF_SQL = """
            SELECT t.id AS treasury_account_id,
                   COALESCE(SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END), 0) AS gl_balance
            FROM m_treasury_account t
            LEFT JOIN acc_gl_journal_entry je ON je.account_id = t.gl_account_id AND je.office_id = t.office_id
                 AND je.currency_code = t.currency_code AND je.entry_date <= :asOf
            WHERE t.id IN (:ids)
            GROUP BY t.id
            """;

    private static final String LAST_SNAPSHOT_SQL = """
            SELECT s.treasury_account_id, s.business_date, s.gl_balance, s.level_status
            FROM m_treasury_position_snapshot s
            JOIN (SELECT treasury_account_id, MAX(business_date) AS business_date
                  FROM m_treasury_position_snapshot WHERE treasury_account_id IN (:ids) GROUP BY treasury_account_id) latest
              ON latest.treasury_account_id = s.treasury_account_id AND latest.business_date = s.business_date
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * GL balance per treasury account id; {@code asOf} limits the entries to that date and before, {@code null} takes
     * every entry.
     */
    public Map<Long, BigDecimal> glBalances(final Collection<Long> treasuryAccountIds, final LocalDate asOf) {
        final Map<Long, BigDecimal> balances = new HashMap<>();
        if (treasuryAccountIds.isEmpty()) {
            return balances;
        }
        final MapSqlParameterSource params = new MapSqlParameterSource("ids", treasuryAccountIds).addValue("asOf", asOf);
        this.jdbcTemplate.query(asOf == null ? BALANCE_SQL : BALANCE_AS_OF_SQL, params, rs -> {
            balances.put(rs.getLong("treasury_account_id"), rs.getBigDecimal("gl_balance"));
        });
        return balances;
    }

    public Map<Long, LastSnapshot> lastSnapshots(final Collection<Long> treasuryAccountIds) {
        final Map<Long, LastSnapshot> snapshots = new HashMap<>();
        if (treasuryAccountIds.isEmpty()) {
            return snapshots;
        }
        this.jdbcTemplate.query(LAST_SNAPSHOT_SQL, new MapSqlParameterSource("ids", treasuryAccountIds), rs -> {
            snapshots.put(rs.getLong("treasury_account_id"), new LastSnapshot(JdbcSupport.getLocalDate(rs, "business_date"),
                    rs.getBigDecimal("gl_balance"), rs.getString("level_status")));
        });
        return snapshots;
    }

    /**
     * Transfers pending on the given date: out of an account once approved and until sent, into an account once sent
     * and until received. Cancelled transfers never leave APPROVED, so their cancellation date ends them.
     */
    public Map<Long, Pending> pending(final Collection<Long> treasuryAccountIds, final LocalDate asOf) {
        final Map<Long, Pending> pending = new HashMap<>();
        if (treasuryAccountIds.isEmpty()) {
            return pending;
        }
        final MapSqlParameterSource params = new MapSqlParameterSource("ids", treasuryAccountIds).addValue("asOf", asOf);
        this.jdbcTemplate.query(PENDING_OUT_SQL, params, rs -> {
            pending.merge(rs.getLong("account_id"), new Pending(BigDecimal.ZERO, rs.getBigDecimal("amount")), Pending::plus);
        });
        this.jdbcTemplate.query(PENDING_IN_SQL, params, rs -> {
            pending.merge(rs.getLong("account_id"), new Pending(rs.getBigDecimal("amount"), BigDecimal.ZERO), Pending::plus);
        });
        return pending;
    }

    public record Pending(BigDecimal in, BigDecimal out) {

        public static final Pending NONE = new Pending(BigDecimal.ZERO, BigDecimal.ZERO);

        public Pending plus(final Pending other) {
            return new Pending(this.in.add(other.in), this.out.add(other.out));
        }
    }

    private static final String PENDING_OUT_SQL = """
            SELECT tr.from_account_id AS account_id, SUM(tr.amount) AS amount
            FROM m_treasury_transfer tr
            WHERE tr.from_account_id IN (:ids) AND tr.approved_on_date <= :asOf
              AND (tr.sent_on_date IS NULL OR tr.sent_on_date > :asOf)
              AND (tr.cancelled_on_date IS NULL OR tr.cancelled_on_date > :asOf)
            GROUP BY tr.from_account_id
            """;

    private static final String PENDING_IN_SQL = """
            SELECT tr.to_account_id AS account_id, SUM(tr.amount) AS amount
            FROM m_treasury_transfer tr
            WHERE tr.to_account_id IN (:ids) AND tr.sent_on_date <= :asOf
              AND (tr.received_on_date IS NULL OR tr.received_on_date > :asOf)
            GROUP BY tr.to_account_id
            """;
}
