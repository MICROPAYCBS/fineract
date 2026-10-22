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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.treasury.data.TreasuryAccountData;
import org.apache.fineract.treasury.data.TreasuryPositionData;
import org.apache.fineract.treasury.data.TreasuryPositionSummaryData;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.exception.TreasuryAccountNotFoundException;
import org.apache.fineract.treasury.service.TreasuryPositionCalculator.LastSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryAccountReadPlatformServiceImpl implements TreasuryAccountReadPlatformService {

    private static final String ACCOUNT_SQL = """
            SELECT t.id, t.code, t.name, t.account_type, t.gl_account_id, gl.gl_code, gl.name AS gl_name, t.office_id,
                   o.name AS office_name, t.currency_code, t.counterparty_name, t.external_account_number, t.min_balance,
                   t.max_balance, t.status, t.created_on_utc, t.last_modified_on_utc
            FROM m_treasury_account t
            JOIN acc_gl_account gl ON gl.id = t.gl_account_id
            JOIN m_office o ON o.id = t.office_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TreasuryBalanceReader balanceReader;

    @Override
    public List<TreasuryAccountData> retrieveAccounts(final Long officeId, final String currencyCode, final String accountType,
            final String status) {
        final List<String> conditions = new ArrayList<>();
        final MapSqlParameterSource params = new MapSqlParameterSource();
        if (officeId != null) {
            conditions.add("t.office_id = :officeId");
            params.addValue("officeId", officeId);
        }
        if (StringUtils.isNotBlank(currencyCode)) {
            conditions.add("t.currency_code = :currencyCode");
            params.addValue("currencyCode", currencyCode.trim().toUpperCase());
        }
        if (StringUtils.isNotBlank(accountType)) {
            conditions.add("t.account_type = :accountType");
            params.addValue("accountType", accountType.trim().toUpperCase());
        }
        if (StringUtils.isNotBlank(status)) {
            conditions.add("t.status = :status");
            params.addValue("status", status.trim().toUpperCase());
        }
        final String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return this.jdbcTemplate.query(ACCOUNT_SQL + where + " ORDER BY t.office_id, t.currency_code, t.code", params,
                (rs, rowNum) -> mapAccount(rs));
    }

    @Override
    public TreasuryAccountData retrieveAccount(final Long accountId) {
        final List<TreasuryAccountData> rows = this.jdbcTemplate.query(ACCOUNT_SQL + " WHERE t.id = :id",
                new MapSqlParameterSource("id", accountId), (rs, rowNum) -> mapAccount(rs));
        if (rows.isEmpty()) {
            throw new TreasuryAccountNotFoundException(accountId);
        }
        return rows.getFirst();
    }

    @Override
    public TreasuryPositionData retrievePosition(final Long accountId) {
        return positions(List.of(retrieveAccount(accountId))).getFirst();
    }

    @Override
    public List<TreasuryPositionSummaryData> retrievePositions(final Long officeId, final String currencyCode) {
        return TreasuryPositionCalculator
                .summarise(positions(retrieveAccounts(officeId, currencyCode, null, TreasuryAccountStatus.ACTIVE.name())));
    }

    private List<TreasuryPositionData> positions(final List<TreasuryAccountData> accounts) {
        final List<Long> ids = accounts.stream().map(TreasuryAccountData::id).toList();
        final Map<Long, BigDecimal> balances = this.balanceReader.glBalances(ids, null);
        final Map<Long, LastSnapshot> snapshots = this.balanceReader.lastSnapshots(ids);
        // pending in and out are the transfers in transit, which come with funding transfers (P1.2)
        return accounts.stream().map(account -> TreasuryPositionCalculator.position(account, balances.get(account.id()), BigDecimal.ZERO,
                BigDecimal.ZERO, snapshots.get(account.id()))).toList();
    }

    private static TreasuryAccountData mapAccount(final ResultSet rs) throws SQLException {
        return new TreasuryAccountData(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("account_type"),
                rs.getLong("gl_account_id"), rs.getString("gl_code"), rs.getString("gl_name"), rs.getLong("office_id"),
                rs.getString("office_name"), rs.getString("currency_code"), rs.getString("counterparty_name"),
                rs.getString("external_account_number"), rs.getBigDecimal("min_balance"), rs.getBigDecimal("max_balance"),
                rs.getString("status"), JdbcSupport.getOffsetDateTime(rs, "created_on_utc"),
                JdbcSupport.getOffsetDateTime(rs, "last_modified_on_utc"));
    }
}
