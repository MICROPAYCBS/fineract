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
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.treasury.data.TreasuryAgentFloatData;
import org.apache.fineract.treasury.data.TreasuryFloatRequestData;
import org.apache.fineract.treasury.domain.TreasuryLevelStatus;
import org.apache.fineract.treasury.exception.TreasuryAgentFloatNotFoundException;
import org.apache.fineract.treasury.exception.TreasuryFloatRequestNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryAgentFloatReadPlatformService {

    private static final String FLOAT_SQL = """
            SELECT af.id, af.client_id, c.display_name, c.office_id, o.name AS office_name, af.savings_account_id, sa.account_no,
                   sa.currency_code, af.treasury_account_id, ta.code AS treasury_account_code, sa.account_balance_derived,
                   af.min_float, af.max_float, af.status,
                   (SELECT COALESCE(SUM(r.amount), 0) FROM m_treasury_float_request r
                     WHERE r.agent_float_id = af.id AND r.status = 'APPROVED' AND r.request_type = 'TOPUP') AS pending_topups,
                   (SELECT COALESCE(SUM(r.amount), 0) FROM m_treasury_float_request r
                     WHERE r.agent_float_id = af.id AND r.status = 'APPROVED' AND r.request_type = 'SWEEP') AS pending_sweeps
            FROM m_treasury_agent_float af
            JOIN m_client c ON c.id = af.client_id
            JOIN m_office o ON o.id = c.office_id
            JOIN m_savings_account sa ON sa.id = af.savings_account_id
            JOIN m_treasury_account ta ON ta.id = af.treasury_account_id
            """;

    private static final String REQUEST_SQL = """
            SELECT r.*, af.client_id, af.savings_account_id
            FROM m_treasury_float_request r
            JOIN m_treasury_agent_float af ON af.id = r.agent_float_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<TreasuryAgentFloatData> retrieveAgentFloats(final Long officeId, final Long clientId, final String status,
            final String levelStatus) {
        final List<String> conditions = new ArrayList<>();
        final MapSqlParameterSource params = new MapSqlParameterSource();
        if (officeId != null) {
            conditions.add("c.office_id = :officeId");
            params.addValue("officeId", officeId);
        }
        if (clientId != null) {
            conditions.add("af.client_id = :clientId");
            params.addValue("clientId", clientId);
        }
        if (StringUtils.isNotBlank(status)) {
            conditions.add("af.status = :status");
            params.addValue("status", status.trim().toUpperCase());
        }
        final String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        final List<TreasuryAgentFloatData> floats = this.jdbcTemplate.query(FLOAT_SQL + where + " ORDER BY c.office_id, af.id", params,
                (rs, rowNum) -> mapFloat(rs));
        if (StringUtils.isBlank(levelStatus)) {
            return floats;
        }
        return floats.stream().filter(agentFloat -> agentFloat.levelStatus().equalsIgnoreCase(levelStatus.trim())).toList();
    }

    public TreasuryAgentFloatData retrieveAgentFloat(final Long agentFloatId) {
        final List<TreasuryAgentFloatData> rows = this.jdbcTemplate.query(FLOAT_SQL + " WHERE af.id = :id",
                new MapSqlParameterSource("id", agentFloatId), (rs, rowNum) -> mapFloat(rs));
        if (rows.isEmpty()) {
            throw new TreasuryAgentFloatNotFoundException(agentFloatId);
        }
        return rows.getFirst();
    }

    public List<TreasuryFloatRequestData> retrieveRequests(final Long agentFloatId, final String status) {
        final List<String> conditions = new ArrayList<>();
        final MapSqlParameterSource params = new MapSqlParameterSource();
        if (agentFloatId != null) {
            conditions.add("r.agent_float_id = :agentFloatId");
            params.addValue("agentFloatId", agentFloatId);
        }
        if (StringUtils.isNotBlank(status)) {
            conditions.add("r.status = :status");
            params.addValue("status", status.trim().toUpperCase());
        }
        final String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return this.jdbcTemplate.query(REQUEST_SQL + where + " ORDER BY r.id DESC", params, (rs, rowNum) -> mapRequest(rs));
    }

    public TreasuryFloatRequestData retrieveRequest(final Long requestId) {
        final List<TreasuryFloatRequestData> rows = this.jdbcTemplate.query(REQUEST_SQL + " WHERE r.id = :id",
                new MapSqlParameterSource("id", requestId), (rs, rowNum) -> mapRequest(rs));
        if (rows.isEmpty()) {
            throw new TreasuryFloatRequestNotFoundException(requestId);
        }
        return rows.getFirst();
    }

    private static TreasuryAgentFloatData mapFloat(final ResultSet rs) throws SQLException {
        final BigDecimal balance = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "account_balance_derived");
        final BigDecimal minFloat = rs.getBigDecimal("min_float");
        final BigDecimal maxFloat = rs.getBigDecimal("max_float");
        return new TreasuryAgentFloatData(rs.getLong("id"), rs.getLong("client_id"), rs.getString("display_name"), rs.getLong("office_id"),
                rs.getString("office_name"), rs.getLong("savings_account_id"), rs.getString("account_no"), rs.getString("currency_code"),
                rs.getLong("treasury_account_id"), rs.getString("treasury_account_code"), balance, minFloat, maxFloat,
                TreasuryLevelStatus.of(balance, minFloat, maxFloat).name(), rs.getBigDecimal("pending_topups"),
                rs.getBigDecimal("pending_sweeps"), rs.getString("status"));
    }

    private static TreasuryFloatRequestData mapRequest(final ResultSet rs) throws SQLException {
        return new TreasuryFloatRequestData(rs.getLong("id"), rs.getLong("agent_float_id"), rs.getLong("client_id"),
                rs.getLong("savings_account_id"), rs.getString("request_type"), rs.getBigDecimal("amount"), rs.getString("status"),
                JdbcSupport.getLong(rs, "requested_by"), JdbcSupport.getLong(rs, "approved_by"),
                JdbcSupport.getLocalDate(rs, "approved_on_date"), JdbcSupport.getLong(rs, "executed_by"),
                JdbcSupport.getLocalDate(rs, "executed_on_date"), JdbcSupport.getLong(rs, "savings_transaction_id"),
                JdbcSupport.getLong(rs, "cancelled_by"), JdbcSupport.getLocalDate(rs, "cancelled_on_date"), rs.getString("note"));
    }
}
