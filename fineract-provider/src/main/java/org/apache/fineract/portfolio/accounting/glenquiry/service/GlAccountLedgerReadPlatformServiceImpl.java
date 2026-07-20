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
package org.apache.fineract.portfolio.accounting.glenquiry.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.glaccount.exception.GLAccountNotFoundException;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.organisation.office.exception.OfficeNotFoundException;
import org.apache.fineract.portfolio.accounting.glbalance.GlBalanceSnapshotConstants;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountLedgerData;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountLedgerEntryData;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountLedgerRequest;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountLedgerSummaryData;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GlAccountLedgerReadPlatformServiceImpl implements GlAccountLedgerReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final GlAccountLedgerDataValidator dataValidator;

    @Override
    public GlAccountLedgerData retrieveLedger(final GlAccountLedgerRequest request) {
        this.dataValidator.validate(request);

        final AccountMeta account = loadAccount(request.getGlAccountId());
        final String officeName = loadOfficeName(request.getOfficeId());
        final String departmentName = loadDepartmentName(request.getDepartmentId());
        final String currencyCode = request.getCurrencyCode().trim();

        final LocalDate asOfDate = request.getStartDate().minusDays(1);
        final SnapshotWatermark watermark = resolveWatermark(request, asOfDate);
        final BigDecimal openingBalance = computeOpeningBalance(request, account.classification(), asOfDate, watermark);

        final List<PeriodLine> periodLines = loadPeriodLines(request, account.classification());
        final List<GlAccountLedgerEntryData> entries = buildEntries(openingBalance, periodLines);

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        OffsetDateTime lastUpdated = null;
        for (final PeriodLine line : periodLines) {
            totalDebit = totalDebit.add(line.debit());
            totalCredit = totalCredit.add(line.credit());
            if (line.createdOnUtc() != null && (lastUpdated == null || line.createdOnUtc().isAfter(lastUpdated))) {
                lastUpdated = line.createdOnUtc();
            }
        }

        final BigDecimal closingBalance = entries.isEmpty() ? openingBalance : entries.get(0).getRunningBalance();

        final GlAccountLedgerSummaryData summary = GlAccountLedgerSummaryData.builder() //
                .openingBalance(openingBalance) //
                .totalDebit(totalDebit) //
                .totalCredit(totalCredit) //
                .closingBalance(closingBalance) //
                .lastUpdated(lastUpdated) //
                .build();

        return GlAccountLedgerData.builder() //
                .glAccountId(account.id()) //
                .glCode(account.glCode()) //
                .glAccountName(account.name()) //
                .officeId(request.getOfficeId()) //
                .officeName(officeName) //
                .departmentId(request.getDepartmentId()) //
                .departmentName(departmentName) //
                .currencyCode(currencyCode) //
                .startDate(request.getStartDate()) //
                .endDate(request.getEndDate()) //
                .summary(summary) //
                .entries(entries) //
                .build();
    }

    private AccountMeta loadAccount(final Long glAccountId) {
        try {
            return this.jdbcTemplate.queryForObject(
                    "SELECT id, gl_code, name, classification_enum FROM acc_gl_account WHERE id = ?", (rs, rowNum) -> {
                        final int classification = rs.getInt("classification_enum");
                        return new AccountMeta(rs.getLong("id"), rs.getString("gl_code"), rs.getString("name"),
                                rs.wasNull() ? null : classification);
                    }, glAccountId);
        } catch (final EmptyResultDataAccessException ex) {
            throw new GLAccountNotFoundException(glAccountId);
        }
    }

    private String loadOfficeName(final Long officeId) {
        try {
            return this.jdbcTemplate.queryForObject("SELECT name FROM m_office WHERE id = ?", String.class, officeId);
        } catch (final EmptyResultDataAccessException ex) {
            throw new OfficeNotFoundException(officeId);
        }
    }

    private String loadDepartmentName(final Long departmentId) {
        if (departmentId == null || departmentId.equals(GlBalanceSnapshotConstants.UNASSIGNED_DEPARTMENT_ID)) {
            return null;
        }
        try {
            return this.jdbcTemplate.queryForObject("SELECT department_name FROM m_department WHERE id = ?", String.class, departmentId);
        } catch (final EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private BigDecimal computeOpeningBalance(final GlAccountLedgerRequest request, final Integer classification, final LocalDate asOfDate,
            final SnapshotWatermark watermark) {
        final BigDecimal snapshotNet = querySnapshotNet(request, watermark);
        final BigDecimal deltaNet = queryJournalDeltaNet(request, asOfDate, watermark);
        final BigDecimal rawNet = snapshotNet.add(deltaNet);
        // Match GeneralLedgerReport Table presentation: asset/expense negate raw credit-positive net
        if (classification != null && (classification == 1 || classification == 5)) {
            return rawNet.negate();
        }
        return rawNet;
    }

    private BigDecimal querySnapshotNet(final GlAccountLedgerRequest request, final SnapshotWatermark watermark) {
        if (watermark.baselineDate() == null) {
            return BigDecimal.ZERO;
        }
        final StringBuilder sql = new StringBuilder();
        final List<Object> params = new ArrayList<>();
        sql.append("SELECT COALESCE(SUM(s.closing_balance_foreign), 0) FROM m_gl_balance_snapshot s ");
        sql.append("INNER JOIN m_office ounder ON s.office_id = ounder.id ");
        sql.append("INNER JOIN m_office o ON o.id = ? AND ounder.hierarchy LIKE CONCAT(o.hierarchy, '%') ");
        params.add(request.getOfficeId());
        sql.append("WHERE s.snapshot_date = ? AND s.snapshot_granularity = ? AND s.gl_account_id = ? ");
        params.add(java.sql.Date.valueOf(watermark.baselineDate()));
        params.add(watermark.granularity());
        params.add(request.getGlAccountId());
        sql.append("AND s.currency_code = ? ");
        params.add(request.getCurrencyCode().trim());
        appendDepartmentFilter(sql, params, "s.department_id", request.getDepartmentId());
        final BigDecimal value = this.jdbcTemplate.queryForObject(sql.toString(), BigDecimal.class, params.toArray());
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal queryJournalDeltaNet(final GlAccountLedgerRequest request, final LocalDate asOfDate,
            final SnapshotWatermark watermark) {
        final StringBuilder sql = new StringBuilder();
        final List<Object> params = new ArrayList<>();
        sql.append("SELECT COALESCE(SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END), 0) ");
        sql.append("FROM acc_gl_journal_entry je ");
        sql.append("INNER JOIN m_office ounder ON je.office_id = ounder.id ");
        sql.append("INNER JOIN m_office o ON o.id = ? AND ounder.hierarchy LIKE CONCAT(o.hierarchy, '%') ");
        params.add(request.getOfficeId());
        sql.append("WHERE je.account_id = ? AND je.entry_date <= ? ");
        params.add(request.getGlAccountId());
        params.add(java.sql.Date.valueOf(asOfDate));
        if (watermark.baselineDate() != null) {
            sql.append("AND je.entry_date > ? ");
            params.add(java.sql.Date.valueOf(watermark.baselineDate()));
        }
        sql.append("AND je.currency_code = ? ");
        params.add(request.getCurrencyCode().trim());
        appendDepartmentFilter(sql, params, "COALESCE(je.department_id, 0)", request.getDepartmentId());
        final BigDecimal value = this.jdbcTemplate.queryForObject(sql.toString(), BigDecimal.class, params.toArray());
        return value == null ? BigDecimal.ZERO : value;
    }

    private List<PeriodLine> loadPeriodLines(final GlAccountLedgerRequest request, final Integer classification) {
        final StringBuilder sql = new StringBuilder();
        final List<Object> params = new ArrayList<>();
        sql.append("SELECT je.id AS id, je.entry_date AS entryDate, je.transaction_id AS transactionId, ");
        sql.append("je.description AS description, je.manual_entry AS manualEntry, ");
        sql.append("CASE WHEN je.type_enum = 1 THEN je.amount ELSE 0 END AS debit, ");
        sql.append("CASE WHEN je.type_enum = 2 THEN je.amount ELSE 0 END AS credit, ");
        sql.append("je.created_on_utc AS createdOnUtc, ");
        sql.append("CASE WHEN ? IN (1, 5) THEN CASE WHEN je.type_enum = 1 THEN je.amount ELSE -je.amount END ");
        sql.append("ELSE CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END END AS movement ");
        params.add(classification == null ? -1 : classification);
        sql.append("FROM acc_gl_journal_entry je ");
        sql.append("INNER JOIN m_office ounder ON je.office_id = ounder.id ");
        sql.append("INNER JOIN m_office o ON o.id = ? AND ounder.hierarchy LIKE CONCAT(o.hierarchy, '%') ");
        params.add(request.getOfficeId());
        sql.append("WHERE je.account_id = ? AND je.entry_date BETWEEN ? AND ? AND je.currency_code = ? ");
        params.add(request.getGlAccountId());
        params.add(java.sql.Date.valueOf(request.getStartDate()));
        params.add(java.sql.Date.valueOf(request.getEndDate()));
        params.add(request.getCurrencyCode().trim());
        appendDepartmentFilter(sql, params, "COALESCE(je.department_id, 0)", request.getDepartmentId());
        sql.append("ORDER BY je.entry_date ASC, je.id ASC ");

        return this.jdbcTemplate.query(sql.toString(), new PeriodLineMapper(), params.toArray());
    }

    private List<GlAccountLedgerEntryData> buildEntries(final BigDecimal openingBalance, final List<PeriodLine> chronologicalLines) {
        if (chronologicalLines.isEmpty()) {
            return Collections.emptyList();
        }
        BigDecimal running = openingBalance;
        final List<GlAccountLedgerEntryData> chronologicalEntries = new ArrayList<>(chronologicalLines.size());
        for (final PeriodLine line : chronologicalLines) {
            running = running.add(line.movement());
            chronologicalEntries.add(GlAccountLedgerEntryData.builder() //
                    .entryDate(line.entryDate()) //
                    .transactionId(line.transactionId()) //
                    .description(line.description()) //
                    .source(Boolean.TRUE.equals(line.manualEntry()) ? "Manual" : "System") //
                    .debit(line.debit()) //
                    .credit(line.credit()) //
                    .runningBalance(running) //
                    .build());
        }
        Collections.reverse(chronologicalEntries);
        return chronologicalEntries;
    }

    private SnapshotWatermark resolveWatermark(final GlAccountLedgerRequest request, final LocalDate asOfDate) {
        final LocalDate dailyFloor = asOfDate.minusDays(GlBalanceSnapshotConstants.DAILY_RETENTION_DAYS);
        final LocalDate dailyBaseline = queryMaxSnapshotDate(request, GlBalanceSnapshotConstants.GRANULARITY_DAILY, asOfDate, dailyFloor);
        if (dailyBaseline != null) {
            return new SnapshotWatermark(dailyBaseline, GlBalanceSnapshotConstants.GRANULARITY_DAILY);
        }
        final LocalDate monthlyBaseline = queryMaxSnapshotDate(request, GlBalanceSnapshotConstants.GRANULARITY_MONTHLY, asOfDate, null);
        if (monthlyBaseline != null) {
            return new SnapshotWatermark(monthlyBaseline, GlBalanceSnapshotConstants.GRANULARITY_MONTHLY);
        }
        return new SnapshotWatermark(null, null);
    }

    private LocalDate queryMaxSnapshotDate(final GlAccountLedgerRequest request, final String granularity, final LocalDate asOfDate,
            final LocalDate notBefore) {
        final StringBuilder sql = new StringBuilder();
        final List<Object> params = new ArrayList<>();
        sql.append("SELECT MAX(s.snapshot_date) FROM m_gl_balance_snapshot s ");
        sql.append("INNER JOIN m_office ounder ON s.office_id = ounder.id ");
        sql.append("INNER JOIN m_office o ON o.id = ? AND ounder.hierarchy LIKE CONCAT(o.hierarchy, '%') ");
        params.add(request.getOfficeId());
        sql.append("WHERE s.snapshot_granularity = ? AND s.snapshot_date <= ? AND s.gl_account_id = ? AND s.currency_code = ? ");
        params.add(granularity);
        params.add(java.sql.Date.valueOf(asOfDate));
        params.add(request.getGlAccountId());
        params.add(request.getCurrencyCode().trim());
        if (notBefore != null) {
            sql.append("AND s.snapshot_date >= ? ");
            params.add(java.sql.Date.valueOf(notBefore));
        }
        appendDepartmentFilter(sql, params, "s.department_id", request.getDepartmentId());
        try {
            return this.jdbcTemplate.queryForObject(sql.toString(), (rs, rowNum) -> {
                final java.sql.Date value = rs.getDate(1);
                return value == null ? null : value.toLocalDate();
            }, params.toArray());
        } catch (final EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private void appendDepartmentFilter(final StringBuilder sql, final List<Object> params, final String columnExpression,
            final Long departmentId) {
        if (departmentId != null) {
            sql.append("AND ").append(columnExpression).append(" = ? ");
            params.add(departmentId);
        }
    }

    private record AccountMeta(Long id, String glCode, String name, Integer classification) {}

    private record SnapshotWatermark(LocalDate baselineDate, String granularity) {}

    private record PeriodLine(Long id, LocalDate entryDate, String transactionId, String description, Boolean manualEntry, BigDecimal debit,
            BigDecimal credit, BigDecimal movement, OffsetDateTime createdOnUtc) {}

    private static final class PeriodLineMapper implements RowMapper<PeriodLine> {

        @Override
        public PeriodLine mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final Timestamp createdTs = rs.getTimestamp("createdOnUtc");
            final OffsetDateTime createdOnUtc = createdTs == null ? null : createdTs.toInstant().atOffset(ZoneOffset.UTC);
            return new PeriodLine( //
                    rs.getLong("id"), //
                    JdbcSupport.getLocalDate(rs, "entryDate"), //
                    rs.getString("transactionId"), //
                    rs.getString("description"), //
                    rs.getBoolean("manualEntry"), //
                    JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "debit"), //
                    JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "credit"), //
                    JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "movement"), //
                    createdOnUtc);
        }
    }
}
