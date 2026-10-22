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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryReconMatchData;
import org.apache.fineract.treasury.data.TreasuryReconciliationData;
import org.apache.fineract.treasury.data.TreasuryStatementData;
import org.apache.fineract.treasury.data.TreasuryStatementFormatData;
import org.apache.fineract.treasury.data.TreasuryStatementLineData;
import org.apache.fineract.treasury.data.TreasuryUnmatchedData;
import org.apache.fineract.treasury.data.TreasuryUnmatchedEntryData;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryStatementFormatRepository;
import org.apache.fineract.treasury.domain.TreasuryStatementStatus;
import org.apache.fineract.treasury.exception.TreasuryAccountNotFoundException;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.service.TreasuryPositionCalculator.ReconStatus;
import org.apache.fineract.treasury.statement.TreasuryAutoMatcher.EntryCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statements, lines, matches and what is still unmatched.
 *
 * <p>
 * The GL side of an account's reconciliation is the journal entries on its GL account, office and currency from the
 * start of its first statement on, leaving out entries that were reversed and the reversing entries themselves (they
 * cancel out), and entries an active match already ties.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryReconciliationReadService {

    private static final String ENTITY = "reconciliation";

    /** Upper bounds (inclusive) of the age buckets of {@code GET /reconciliation/unmatched?ageBucket=}. */
    private static final Map<String, long[]> AGE_BUCKETS = Map.of("0-7", new long[] { 0, 7 }, "8-30", new long[] { 8, 30 }, "31-90",
            new long[] { 31, 90 }, "90+", new long[] { 91, Long.MAX_VALUE });

    private static final String UNMATCHED_ENTRIES_SQL = """
            SELECT je.id, je.entry_date, je.transaction_id, je.ref_num, je.description,
                   CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END AS signed_amount
            FROM acc_gl_journal_entry je
            WHERE je.account_id = :glAccountId AND je.office_id = :officeId AND je.currency_code = :currencyCode
              AND je.reversed = :notReversed AND je.entry_date >= :fromDate AND je.entry_date <= :toDate
              AND NOT EXISTS (SELECT 1 FROM acc_gl_journal_entry original WHERE original.reversal_id = je.id)
              AND NOT EXISTS (SELECT 1 FROM m_treasury_recon_match_item i JOIN m_treasury_recon_match m ON m.id = i.match_id
                              WHERE i.journal_entry_id = je.id AND m.status = 'ACTIVE')
            """;

    private static final String STATEMENT_SQL = """
            SELECT s.*, a.code AS account_code, f.code AS format_code,
                   (SELECT COUNT(*) FROM m_treasury_statement_line l WHERE l.statement_id = s.id AND l.match_status = 'UNMATCHED') AS unmatched
            FROM m_treasury_statement s
            JOIN m_treasury_account a ON a.id = s.treasury_account_id
            JOIN m_treasury_statement_format f ON f.id = s.format_id
            """;

    private static final String LINE_SQL = "SELECT l.* FROM m_treasury_statement_line l";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TreasuryAccountRepository accountRepository;
    private final TreasuryStatementFormatRepository formatRepository;
    private final TreasuryBalanceReader balanceReader;

    public List<TreasuryStatementFormatData> retrieveFormats() {
        return this.formatRepository.findAllByOrderByCodeAsc().stream().map(TreasuryStatementFormatData::from).toList();
    }

    public List<TreasuryStatementData> retrieveStatements(final Long accountId, final String status) {
        final List<String> conditions = new ArrayList<>();
        final MapSqlParameterSource params = new MapSqlParameterSource();
        if (accountId != null) {
            conditions.add("s.treasury_account_id = :accountId");
            params.addValue("accountId", accountId);
        }
        if (StringUtils.isNotBlank(status)) {
            conditions.add("s.status = :status");
            params.addValue("status", status.trim().toUpperCase());
        }
        final String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return this.jdbcTemplate.query(STATEMENT_SQL + where + " ORDER BY s.treasury_account_id, s.period_from, s.id", params,
                (rs, rowNum) -> mapStatement(rs));
    }

    public TreasuryStatementData retrieveStatement(final Long statementId) {
        final List<TreasuryStatementData> rows = this.jdbcTemplate.query(STATEMENT_SQL + " WHERE s.id = :id",
                new MapSqlParameterSource("id", statementId), (rs, rowNum) -> mapStatement(rs));
        if (rows.isEmpty()) {
            throw new TreasuryWorkflowException("statement", "not.found", "Statement " + statementId + " does not exist", statementId);
        }
        return rows.getFirst();
    }

    public List<TreasuryStatementLineData> retrieveLines(final Long statementId, final String matchStatus) {
        retrieveStatement(statementId);
        final MapSqlParameterSource params = new MapSqlParameterSource("statementId", statementId);
        String where = " WHERE l.statement_id = :statementId";
        if (StringUtils.isNotBlank(matchStatus)) {
            where += " AND l.match_status = :matchStatus";
            params.addValue("matchStatus", matchStatus.trim().toUpperCase());
        }
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        return this.jdbcTemplate.query(LINE_SQL + where + " ORDER BY l.line_number", params, (rs, rowNum) -> mapLine(rs, businessDate));
    }

    /**
     * Statement lines and GL entries not yet matched, for one account or all active ones, oldest first, optionally
     * within one age bucket ({@code 0-7}, {@code 8-30}, {@code 31-90}, {@code 90+} days).
     */
    public TreasuryUnmatchedData retrieveUnmatched(final Long accountId, final String ageBucket) {
        final long[] bucket = bucket(ageBucket);
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        final List<TreasuryAccount> accounts = accountId == null
                ? this.accountRepository.findAll().stream().filter(TreasuryAccount::isActive).toList()
                : List.of(account(accountId));
        final List<TreasuryStatementLineData> lines = new ArrayList<>();
        final List<TreasuryUnmatchedEntryData> entries = new ArrayList<>();
        for (final TreasuryAccount account : accounts) {
            lines.addAll(this.jdbcTemplate.query(
                    LINE_SQL + " WHERE l.treasury_account_id = :accountId AND l.match_status = 'UNMATCHED'"
                            + " ORDER BY l.transaction_date, l.id",
                    new MapSqlParameterSource("accountId", account.getId()), (rs, rowNum) -> mapLine(rs, businessDate)));
            for (final EntryCandidate entry : unmatchedEntries(account, null, businessDate)) {
                entries.add(new TreasuryUnmatchedEntryData(entry.id(), account.getId(), entry.date(), entry.amount(), entry.transactionId(),
                        entry.referenceNumber(), descriptionOf(entry.id()), ChronoUnit.DAYS.between(entry.date(), businessDate)));
            }
        }
        return new TreasuryUnmatchedData(lines.stream().filter(line -> bucket == null || inBucket(line.ageDays(), bucket)).toList(),
                entries.stream().filter(entry -> bucket == null || inBucket(entry.ageDays(), bucket))
                        .sorted((a, b) -> a.entryDate().compareTo(b.entryDate())).toList());
    }

    /**
     * Journal entries of the account that can still be matched, between {@code from} (default: the start of its first
     * statement) and {@code to}. Empty when the account has no statement yet.
     */
    public List<EntryCandidate> unmatchedEntries(final TreasuryAccount account, final LocalDate from, final LocalDate to) {
        final LocalDate start = from != null ? from : reconciliationFrom(account.getId());
        if (start == null || start.isAfter(to)) {
            return List.of();
        }
        final MapSqlParameterSource params = new MapSqlParameterSource().addValue("glAccountId", account.getGlAccountId())
                .addValue("officeId", account.getOfficeId()).addValue("currencyCode", account.getCurrencyCode())
                .addValue("notReversed", Boolean.FALSE).addValue("fromDate", start).addValue("toDate", to);
        return this.jdbcTemplate.query(UNMATCHED_ENTRIES_SQL + " ORDER BY je.entry_date, je.id", params,
                (rs, rowNum) -> new EntryCandidate(rs.getLong("id"), JdbcSupport.getLocalDate(rs, "entry_date"),
                        rs.getBigDecimal("signed_amount"), rs.getString("transaction_id"), rs.getString("ref_num")));
    }

    /** The subset of the ids that are matchable entries of the account, with their signed amounts. */
    public List<EntryCandidate> matchableEntries(final TreasuryAccount account, final Collection<Long> journalEntryIds) {
        if (journalEntryIds.isEmpty()) {
            return List.of();
        }
        final MapSqlParameterSource params = new MapSqlParameterSource().addValue("glAccountId", account.getGlAccountId())
                .addValue("officeId", account.getOfficeId()).addValue("currencyCode", account.getCurrencyCode())
                .addValue("notReversed", Boolean.FALSE).addValue("fromDate", LocalDate.of(1900, 1, 1))
                .addValue("toDate", LocalDate.of(9999, 12, 31)).addValue("ids", journalEntryIds);
        return this.jdbcTemplate.query(UNMATCHED_ENTRIES_SQL + " AND je.id IN (:ids)", params,
                (rs, rowNum) -> new EntryCandidate(rs.getLong("id"), JdbcSupport.getLocalDate(rs, "entry_date"),
                        rs.getBigDecimal("signed_amount"), rs.getString("transaction_id"), rs.getString("ref_num")));
    }

    public TreasuryReconciliationData retrieveAccountReconciliation(final Long accountId) {
        final TreasuryAccount account = account(accountId);
        final List<TreasuryStatementData> statements = retrieveStatements(accountId, null);
        TreasuryStatementData lastReconciled = null;
        for (final TreasuryStatementData statement : statements.stream().sorted((a, b) -> a.periodTo().compareTo(b.periodTo())).toList()) {
            if (!TreasuryStatementStatus.RECONCILED.name().equals(statement.status())) {
                break;
            }
            lastReconciled = statement;
        }
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        BigDecimal glBalance = null;
        BigDecimal difference = null;
        if (lastReconciled != null) {
            glBalance = this.balanceReader.glBalances(List.of(accountId), lastReconciled.periodTo()).getOrDefault(accountId,
                    BigDecimal.ZERO);
            difference = lastReconciled.closingBalance().subtract(glBalance);
        }
        final long unmatchedLines = statements.stream().mapToLong(TreasuryStatementData::unmatchedLines).sum();
        return new TreasuryReconciliationData(accountId, account.getCode(), statements.size(),
                (int) statements.stream().filter(s -> TreasuryStatementStatus.RECONCILED.name().equals(s.status())).count(),
                lastReconciled == null ? null : lastReconciled.periodTo(), lastReconciled == null ? null : lastReconciled.closingBalance(),
                glBalance, difference, unmatchedLines, unmatchedEntries(account, null, businessDate).size(), reconciliationFrom(accountId));
    }

    /** For the position response. */
    public ReconStatus reconStatus(final Long accountId) {
        final TreasuryReconciliationData data = retrieveAccountReconciliation(accountId);
        return new ReconStatus(data.lastReconciledDate(), data.unmatchedStatementLines() + data.unmatchedJournalEntries());
    }

    public List<TreasuryReconMatchData> retrieveMatches(final Long accountId, final String status) {
        final List<String> conditions = new ArrayList<>();
        final MapSqlParameterSource params = new MapSqlParameterSource();
        if (accountId != null) {
            conditions.add("m.treasury_account_id = :accountId");
            params.addValue("accountId", accountId);
        }
        if (StringUtils.isNotBlank(status)) {
            conditions.add("m.status = :status");
            params.addValue("status", status.trim().toUpperCase());
        }
        final String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return this.jdbcTemplate.query(MATCH_SQL + where + " ORDER BY m.id DESC", params, (rs, rowNum) -> mapMatch(rs)).stream()
                .map(this::withItems).toList();
    }

    public TreasuryReconMatchData retrieveMatch(final Long matchId) {
        final List<TreasuryReconMatchData> rows = this.jdbcTemplate.query(MATCH_SQL + " WHERE m.id = :id",
                new MapSqlParameterSource("id", matchId), (rs, rowNum) -> mapMatch(rs));
        if (rows.isEmpty()) {
            throw new TreasuryWorkflowException(ENTITY, "match.not.found", "Reconciliation match " + matchId + " does not exist", matchId);
        }
        return withItems(rows.getFirst());
    }

    private static final String MATCH_SQL = """
            SELECT m.*, cv.code_value AS reason
            FROM m_treasury_recon_match m
            LEFT JOIN m_code_value cv ON cv.id = m.reason_code_value_id
            """;

    private TreasuryReconMatchData withItems(final TreasuryReconMatchData match) {
        final List<TreasuryReconMatchData.Item> items = this.jdbcTemplate.query(
                "SELECT statement_line_id, journal_entry_id, amount FROM m_treasury_recon_match_item WHERE match_id = :id ORDER BY id",
                new MapSqlParameterSource("id", match.id()),
                (rs, rowNum) -> new TreasuryReconMatchData.Item(JdbcSupport.getLong(rs, "statement_line_id"),
                        JdbcSupport.getLong(rs, "journal_entry_id"), rs.getBigDecimal("amount")));
        return new TreasuryReconMatchData(match.id(), match.treasuryAccountId(), match.rule(), match.status(), match.matchedBy(),
                match.matchedOnDate(), match.reasonCodeValueId(), match.reason(), match.note(), match.adjustmentTransactionId(),
                match.unmatchedBy(), match.unmatchedOnDate(), match.unmatchReason(), items);
    }

    private LocalDate reconciliationFrom(final Long accountId) {
        final List<LocalDate> from = this.jdbcTemplate.query(
                "SELECT MIN(period_from) AS period_from FROM m_treasury_statement WHERE treasury_account_id = :accountId",
                new MapSqlParameterSource("accountId", accountId), (rs, rowNum) -> JdbcSupport.getLocalDate(rs, "period_from"));
        return from.isEmpty() ? null : from.getFirst();
    }

    private String descriptionOf(final Long journalEntryId) {
        final List<String> rows = this.jdbcTemplate.queryForList("SELECT description FROM acc_gl_journal_entry WHERE id = :id",
                new MapSqlParameterSource("id", journalEntryId), String.class);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private TreasuryAccount account(final Long accountId) {
        return this.accountRepository.findById(accountId).orElseThrow(() -> new TreasuryAccountNotFoundException(accountId));
    }

    private static long[] bucket(final String ageBucket) {
        if (StringUtils.isBlank(ageBucket)) {
            return null;
        }
        final long[] bucket = AGE_BUCKETS.get(ageBucket.trim());
        if (bucket == null) {
            throw new TreasuryWorkflowException(ENTITY, "age.bucket.unknown", "ageBucket must be one of 0-7, 8-30, 31-90, 90+", ageBucket);
        }
        return bucket;
    }

    private static boolean inBucket(final Long age, final long[] bucket) {
        return age != null && age >= bucket[0] && age <= bucket[1];
    }

    private static TreasuryStatementData mapStatement(final ResultSet rs) throws SQLException {
        return new TreasuryStatementData(rs.getLong("id"), rs.getLong("treasury_account_id"), rs.getString("account_code"),
                rs.getString("format_code"), rs.getString("statement_reference"), rs.getString("file_name"), rs.getString("currency_code"),
                JdbcSupport.getLocalDate(rs, "period_from"), JdbcSupport.getLocalDate(rs, "period_to"), rs.getBigDecimal("opening_balance"),
                rs.getBigDecimal("closing_balance"), rs.getInt("line_count"), rs.getLong("unmatched"), rs.getString("status"),
                JdbcSupport.getLong(rs, "imported_by"), JdbcSupport.getLocalDate(rs, "imported_on_date"));
    }

    private static TreasuryStatementLineData mapLine(final ResultSet rs, final LocalDate businessDate) throws SQLException {
        final String status = rs.getString("match_status");
        final LocalDate date = JdbcSupport.getLocalDate(rs, "transaction_date");
        return new TreasuryStatementLineData(rs.getLong("id"), rs.getLong("statement_id"), rs.getLong("treasury_account_id"),
                rs.getInt("line_number"), date, JdbcSupport.getLocalDate(rs, "value_date"), rs.getBigDecimal("amount"),
                rs.getString("description"), rs.getString("reference"), rs.getBigDecimal("running_balance"), status,
                JdbcSupport.getLong(rs, "match_id"), "UNMATCHED".equals(status) ? ChronoUnit.DAYS.between(date, businessDate) : null);
    }

    private static TreasuryReconMatchData mapMatch(final ResultSet rs) throws SQLException {
        return new TreasuryReconMatchData(rs.getLong("id"), rs.getLong("treasury_account_id"), rs.getString("match_rule"),
                rs.getString("status"), JdbcSupport.getLong(rs, "matched_by"), JdbcSupport.getLocalDate(rs, "matched_on_date"),
                JdbcSupport.getLong(rs, "reason_code_value_id"), rs.getString("reason"), rs.getString("note"),
                rs.getString("adjustment_transaction_id"), JdbcSupport.getLong(rs, "unmatched_by"),
                JdbcSupport.getLocalDate(rs, "unmatched_on_date"), rs.getString("unmatch_reason"), List.of());
    }
}
