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
package org.apache.fineract.portfolio.accounting.glbalance.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.database.RoutingDataSourceServiceFactory;
import org.apache.fineract.portfolio.accounting.glbalance.GlBalanceSnapshotConstants;
import org.apache.fineract.portfolio.accounting.glbalance.domain.GlBalanceSnapshotRepository;
import org.apache.fineract.portfolio.accounting.glbalance.domain.GlBalanceSnapshotTracking;
import org.apache.fineract.portfolio.accounting.glbalance.domain.GlBalanceSnapshotTrackingRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlBalanceSnapshotWritePlatformServiceImpl implements GlBalanceSnapshotWritePlatformService {

    private static final LocalDate FALLBACK_START_DATE = LocalDate.of(2010, 1, 1);

    private final RoutingDataSourceServiceFactory dataSourceServiceFactory;
    private final GlBalanceSnapshotRepository glBalanceSnapshotRepository;
    private final GlBalanceSnapshotTrackingRepository glBalanceSnapshotTrackingRepository;
    private final ConfigurationDomainService configurationDomainService;

    @Override
    @Transactional
    public int backfillSnapshots(final Long jobExecutionId) {
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        final LocalDate targetDate = businessDate.minusDays(1);
        if (targetDate.isBefore(FALLBACK_START_DATE)) {
            return 0;
        }

        final JdbcTemplate jdbcTemplate = jdbcTemplate();

        final int dailyRetentionDays = resolveDailyRetentionDays();
        final LocalDate dailyWindowStart = businessDate.minusDays(dailyRetentionDays);
        final LocalDate earliestEntryDate = findEarliestEntryDate(jdbcTemplate).orElse(FALLBACK_START_DATE);
        final LocalDate monthlyStart = earliestEntryDate.withDayOfMonth(1);

        int rowsAffected = 0;
        LocalDate monthCursor = monthlyStart;
        while (!monthCursor.isAfter(targetDate)) {
            final YearMonth yearMonth = YearMonth.from(monthCursor);
            final LocalDate monthEnd = yearMonth.atEndOfMonth();
            if (!monthEnd.isAfter(targetDate) && monthEnd.isBefore(dailyWindowStart)) {
                rowsAffected += buildSnapshotForDate(jdbcTemplate, monthEnd, GlBalanceSnapshotConstants.GRANULARITY_MONTHLY, true);
            }
            monthCursor = yearMonth.plusMonths(1).atDay(1);
        }

        LocalDate dailyCursor = dailyWindowStart.isBefore(earliestEntryDate) ? earliestEntryDate : dailyWindowStart;
        while (!dailyCursor.isAfter(targetDate)) {
            rowsAffected += buildSnapshotForDate(jdbcTemplate, dailyCursor, GlBalanceSnapshotConstants.GRANULARITY_DAILY, false);
            dailyCursor = dailyCursor.plusDays(1);
        }

        recordTracking(GlBalanceSnapshotConstants.GRANULARITY_MONTHLY, monthlyStart, targetDate, jobExecutionId);
        recordTracking(GlBalanceSnapshotConstants.GRANULARITY_DAILY, dailyWindowStart, targetDate, jobExecutionId);
        log.info("GL balance snapshot backfill completed through {} ({} sparse rows written/updated)", targetDate, rowsAffected);
        return rowsAffected;
    }

    @Override
    @Transactional
    public int updateSnapshotsIncrementally(final Long jobExecutionId) {
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        final LocalDate targetDate = businessDate.minusDays(1);
        if (targetDate.isBefore(FALLBACK_START_DATE)) {
            return 0;
        }

        final JdbcTemplate jdbcTemplate = jdbcTemplate();

        final int dailyRetentionDays = resolveDailyRetentionDays();
        final LocalDate dailyWindowStart = businessDate.minusDays(dailyRetentionDays);

        final LocalDate lastDaily = glBalanceSnapshotTrackingRepository
                .findMaxSnapshotDateToByGranularity(GlBalanceSnapshotConstants.GRANULARITY_DAILY).orElse(dailyWindowStart.minusDays(1));
        final LocalDate dailyFrom = lastDaily.plusDays(1);
        if (dailyFrom.isBefore(dailyWindowStart)) {
            purgeDailySnapshotsBefore(jdbcTemplate, dailyWindowStart);
        }

        int rowsAffected = 0;
        LocalDate dailyCursor = dailyFrom.isBefore(dailyWindowStart) ? dailyWindowStart : dailyFrom;
        while (!dailyCursor.isAfter(targetDate)) {
            rowsAffected += buildSnapshotForDate(jdbcTemplate, dailyCursor, GlBalanceSnapshotConstants.GRANULARITY_DAILY, false);
            dailyCursor = dailyCursor.plusDays(1);
        }

        final LocalDate lastMonthly = glBalanceSnapshotTrackingRepository
                .findMaxSnapshotDateToByGranularity(GlBalanceSnapshotConstants.GRANULARITY_MONTHLY)
                .orElse(FALLBACK_START_DATE.minusDays(1));
        LocalDate monthCursor = lastMonthly.plusDays(1);
        while (!monthCursor.isAfter(targetDate)) {
            final LocalDate monthEnd = YearMonth.from(monthCursor).atEndOfMonth();
            if (!monthEnd.isAfter(targetDate) && monthEnd.isBefore(dailyWindowStart) && !monthEnd.isBefore(monthCursor)) {
                rowsAffected += buildSnapshotForDate(jdbcTemplate, monthEnd, GlBalanceSnapshotConstants.GRANULARITY_MONTHLY, true);
            }
            monthCursor = YearMonth.from(monthCursor).plusMonths(1).atDay(1);
        }

        if (!dailyCursor.minusDays(1).isBefore(dailyWindowStart)) {
            recordTracking(GlBalanceSnapshotConstants.GRANULARITY_DAILY, dailyWindowStart, targetDate, jobExecutionId);
        }
        if (!YearMonth.from(monthCursor.minusMonths(1)).atEndOfMonth().isBefore(FALLBACK_START_DATE)) {
            recordTracking(GlBalanceSnapshotConstants.GRANULARITY_MONTHLY, FALLBACK_START_DATE, targetDate, jobExecutionId);
        }
        log.info("GL balance snapshot incremental update through {} ({} sparse rows written/updated)", targetDate, rowsAffected);
        return rowsAffected;
    }

    private int buildSnapshotForDate(final JdbcTemplate jdbcTemplate, final LocalDate snapshotDate, final String granularity,
            final boolean seal) {
        glBalanceSnapshotRepository.deleteBySnapshotDateAndGranularity(snapshotDate, granularity);

        final String insertSql = """
                INSERT INTO m_gl_balance_snapshot (
                    snapshot_date, snapshot_granularity, office_id, department_id, gl_account_id, currency_code,
                    closing_balance_base, closing_balance_foreign, is_sealed, created_on_utc
                )
                SELECT
                    ?,
                    ?,
                    je.office_id,
                    COALESCE(je.department_id, 0),
                    je.account_id,
                    je.currency_code,
                    SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END),
                    SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END),
                    ?,
                    ?
                FROM acc_gl_journal_entry je
                WHERE je.entry_date <= ?
                GROUP BY je.office_id, COALESCE(je.department_id, 0), je.account_id, je.currency_code
                HAVING SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END) <> 0
                """;

        final OffsetDateTime now = DateUtils.getAuditOffsetDateTime();
        return jdbcTemplate.update(insertSql, snapshotDate, granularity, seal, now, snapshotDate);
    }

    private void purgeDailySnapshotsBefore(final JdbcTemplate jdbcTemplate, final LocalDate dailyWindowStart) {
        jdbcTemplate.update("DELETE FROM m_gl_balance_snapshot WHERE snapshot_granularity = ? AND snapshot_date < ?",
                GlBalanceSnapshotConstants.GRANULARITY_DAILY, dailyWindowStart);
    }

    private Optional<LocalDate> findEarliestEntryDate(final JdbcTemplate jdbcTemplate) {
        final List<LocalDate> results = jdbcTemplate.query("SELECT MIN(entry_date) FROM acc_gl_journal_entry",
                (rs, rowNum) -> rs.getObject(1, LocalDate.class));
        if (results.isEmpty() || results.getFirst() == null) {
            return Optional.empty();
        }
        return Optional.of(results.getFirst());
    }

    private void recordTracking(final String granularity, final LocalDate from, final LocalDate to, final Long jobExecutionId) {
        if (to.isBefore(from)) {
            return;
        }
        final GlBalanceSnapshotTracking tracking = new GlBalanceSnapshotTracking();
        tracking.setSnapshotGranularity(granularity);
        tracking.setSnapshotDateFrom(from);
        tracking.setSnapshotDateTo(to);
        tracking.setJobExecutionId(jobExecutionId);
        tracking.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        glBalanceSnapshotTrackingRepository.save(tracking);
    }

    private int resolveDailyRetentionDays() {
        final Integer configured = configurationDomainService.getGlBalanceSnapshotDailyRetentionDays();
        if (configured == null || configured < 1) {
            return GlBalanceSnapshotConstants.DAILY_RETENTION_DAYS;
        }
        return configured;
    }

    private JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSourceServiceFactory.determineDataSourceService().retrieveDataSource());
    }
}
