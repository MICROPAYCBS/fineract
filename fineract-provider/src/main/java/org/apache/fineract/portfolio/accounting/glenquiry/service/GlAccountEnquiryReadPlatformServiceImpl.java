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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.accounting.glbalance.GlBalanceSnapshotConstants;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountEnquiryData;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountEnquiryRequest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GlAccountEnquiryReadPlatformServiceImpl implements GlAccountEnquiryReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final GlAccountEnquiryDataValidator dataValidator;

    @Override
    public List<GlAccountEnquiryData> enquire(final GlAccountEnquiryRequest request) {
        this.dataValidator.validate(request);

        final LocalDate asOfDate = DateUtils.getBusinessLocalDate();
        final SnapshotWatermark watermark = resolveWatermark(asOfDate);

        final StringBuilder sql = new StringBuilder();
        final List<Object> params = new ArrayList<>();

        sql.append("SELECT o.id AS officeId, o.name AS officeName, aga.id AS glAccountId, aga.gl_code AS glCode, ");
        sql.append("aga.name AS glAccountName, aga.classification_enum AS classification, aga.disabled AS disabled, ");
        sql.append("balances.currency_code AS currencyCode, ");
        sql.append("COALESCE(balances.signed_net, 0) AS signedNet ");
        sql.append("FROM ( ");
        sql.append("  SELECT grain.office_id, grain.gl_account_id, grain.currency_code, ");
        sql.append("         COALESCE(snap.net_balance, 0) + COALESCE(delta.net_balance, 0) AS signed_net ");
        sql.append("  FROM ( ");
        sql.append("    SELECT office_id, gl_account_id, currency_code FROM m_gl_balance_snapshot ");
        sql.append("    UNION ");
        sql.append("    SELECT office_id, account_id AS gl_account_id, currency_code FROM acc_gl_journal_entry ");
        sql.append("  ) grain ");
        sql.append("  LEFT JOIN ( ");
        sql.append("    SELECT s.office_id, s.gl_account_id, s.currency_code, SUM(s.closing_balance_foreign) AS net_balance ");
        sql.append("    FROM m_gl_balance_snapshot s ");
        sql.append("    WHERE 1 = 1 ");
        if (watermark.baselineDate() != null) {
            sql.append("      AND s.snapshot_date = ? AND s.snapshot_granularity = ? ");
            params.add(java.sql.Date.valueOf(watermark.baselineDate()));
            params.add(watermark.granularity());
        } else {
            sql.append("      AND 1 = 0 ");
        }
        sql.append("    GROUP BY s.office_id, s.gl_account_id, s.currency_code ");
        sql.append("  ) snap ON snap.office_id = grain.office_id AND snap.gl_account_id = grain.gl_account_id ");
        sql.append("         AND snap.currency_code = grain.currency_code ");
        sql.append("  LEFT JOIN ( ");
        sql.append("    SELECT je.office_id, je.account_id AS gl_account_id, je.currency_code, ");
        sql.append("           SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END) AS net_balance ");
        sql.append("    FROM acc_gl_journal_entry je ");
        sql.append("    WHERE je.entry_date <= ? ");
        params.add(java.sql.Date.valueOf(asOfDate));
        if (watermark.baselineDate() != null) {
            sql.append("      AND je.entry_date > ? ");
            params.add(java.sql.Date.valueOf(watermark.baselineDate()));
        }
        sql.append("    GROUP BY je.office_id, je.account_id, je.currency_code ");
        sql.append("  ) delta ON delta.office_id = grain.office_id AND delta.gl_account_id = grain.gl_account_id ");
        sql.append("         AND delta.currency_code = grain.currency_code ");
        sql.append(") balances ");
        sql.append("INNER JOIN acc_gl_account aga ON aga.id = balances.gl_account_id ");
        sql.append("INNER JOIN m_office o ON o.id = balances.office_id ");
        sql.append("WHERE 1 = 1 ");

        appendAccountAndDimensionFilters(sql, params, request);
        sql.append("ORDER BY o.name, aga.gl_code, balances.currency_code ");

        return this.jdbcTemplate.query(sql.toString(), new GlAccountEnquiryMapper(), params.toArray());
    }

    private void appendAccountAndDimensionFilters(final StringBuilder sql, final List<Object> params,
            final GlAccountEnquiryRequest request) {
        if (StringUtils.isNotBlank(request.getGlPrefix())) {
            sql.append(" AND aga.gl_code LIKE ? ");
            params.add(request.getGlPrefix().trim() + "%");
        }
        if (StringUtils.isNotBlank(request.getLedgerNumber())) {
            sql.append(" AND aga.gl_code LIKE ? ");
            params.add("%" + request.getLedgerNumber().trim() + "%");
        }
        if (request.getOfficeId() != null) {
            sql.append(" AND balances.office_id = ? ");
            params.add(request.getOfficeId());
        }
        if (StringUtils.isNotBlank(request.getCurrencyCode())) {
            sql.append(" AND balances.currency_code = ? ");
            params.add(request.getCurrencyCode().trim());
        }
        if (request.getDisabled() != null) {
            sql.append(" AND aga.disabled = ? ");
            params.add(request.getDisabled());
        }
    }

    private SnapshotWatermark resolveWatermark(final LocalDate asOfDate) {
        final LocalDate dailyFloor = asOfDate.minusDays(GlBalanceSnapshotConstants.DAILY_RETENTION_DAYS);
        final LocalDate dailyBaseline = queryMaxSnapshotDate(GlBalanceSnapshotConstants.GRANULARITY_DAILY, asOfDate, dailyFloor);
        if (dailyBaseline != null) {
            return new SnapshotWatermark(dailyBaseline, GlBalanceSnapshotConstants.GRANULARITY_DAILY);
        }
        final LocalDate monthlyBaseline = queryMaxSnapshotDate(GlBalanceSnapshotConstants.GRANULARITY_MONTHLY, asOfDate, null);
        if (monthlyBaseline != null) {
            return new SnapshotWatermark(monthlyBaseline, GlBalanceSnapshotConstants.GRANULARITY_MONTHLY);
        }
        return new SnapshotWatermark(null, null);
    }

    private LocalDate queryMaxSnapshotDate(final String granularity, final LocalDate asOfDate, final LocalDate notBefore) {
        final StringBuilder sql = new StringBuilder();
        sql.append("SELECT MAX(snapshot_date) FROM m_gl_balance_snapshot ");
        sql.append("WHERE snapshot_granularity = ? AND snapshot_date <= ? ");
        final List<Object> params = new ArrayList<>();
        params.add(granularity);
        params.add(java.sql.Date.valueOf(asOfDate));
        if (notBefore != null) {
            sql.append("AND snapshot_date >= ? ");
            params.add(java.sql.Date.valueOf(notBefore));
        }
        try {
            return this.jdbcTemplate.queryForObject(sql.toString(), (rs, rowNum) -> {
                final java.sql.Date value = rs.getDate(1);
                return value == null ? null : value.toLocalDate();
            }, params.toArray());
        } catch (final EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private record SnapshotWatermark(LocalDate baselineDate, String granularity) {}

    private static final class GlAccountEnquiryMapper implements RowMapper<GlAccountEnquiryData> {

        @Override
        public GlAccountEnquiryData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final Integer classification = JdbcSupport.getInteger(rs, "classification");
            final BigDecimal signedNet = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "signedNet");
            final BigDecimal balance = flipForPresentation(classification, signedNet);
            return GlAccountEnquiryData.builder() //
                    .officeId(JdbcSupport.getLong(rs, "officeId")) //
                    .officeName(rs.getString("officeName")) //
                    .glAccountId(JdbcSupport.getLong(rs, "glAccountId")) //
                    .glCode(rs.getString("glCode")) //
                    .glAccountName(rs.getString("glAccountName")) //
                    .currencyCode(rs.getString("currencyCode")) //
                    .balance(balance) //
                    .disabled(rs.getBoolean("disabled")) //
                    .build();
        }

        private static BigDecimal flipForPresentation(final Integer classification, final BigDecimal signedNet) {
            // Asset(1) and Expense(5): keep signed net; Liability(2), Equity(3), Income(4): negate
            if (classification != null && (classification == 2 || classification == 3 || classification == 4)) {
                return signedNet.negate();
            }
            return signedNet;
        }
    }
}
