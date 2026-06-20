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

package org.apache.fineract.portfolio.client.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;
import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.ClientIncomeSourceData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientIncomeSourcesReadPlatformServiceImpl implements ClientIncomeSourcesReadPlatformService {

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;
    private final CodeValueReadPlatformService codeValueReadPlatformService;

    private static final class ClientIncomeSourceMapper implements RowMapper<ClientIncomeSourceData> {

        public String schema() {
            return " cis.id AS id, cis.client_id AS clientId,"
                    + " cis.income_source_type_id AS incomeSourceTypeId, ist.code_value AS incomeSourceType,"
                    + " cis.source_of_funds_id AS sourceOfFundsId, sof.code_value AS sourceOfFunds,"
                    + " cis.employer_business_name AS employerBusinessName, cis.occupation AS occupation,"
                    + " cis.sub_industry_id AS subIndustryId, si.sub_industry_name AS subIndustryName,"
                    + " cis.monthly_income AS monthlyIncome, cis.income_currency_code AS incomeCurrencyCode,"
                    + " cis.income_frequency_id AS incomeFrequencyId, ifreq.code_value AS incomeFrequency,"
                    + " cis.start_date AS startDate, cis.end_date AS endDate,"
                    + " cis.is_primary_source AS isPrimarySourceFlag,"
                    + " cis.verification_status_id AS verificationStatusId, vstat.code_value AS verificationStatus,"
                    + " cis.verified_by AS verifiedBy, vu.username AS verifiedByUsername, cis.verified_on_utc AS verifiedOnUtc,"
                    + " cis.supporting_document AS supportingDocument, cis.remarks AS remarks, cis.status AS status"
                    + " FROM m_client_income_source cis"
                    + " LEFT JOIN m_code_value ist ON cis.income_source_type_id = ist.id"
                    + " LEFT JOIN m_code_value sof ON cis.source_of_funds_id = sof.id"
                    + " LEFT JOIN m_code_value ifreq ON cis.income_frequency_id = ifreq.id"
                    + " LEFT JOIN m_code_value vstat ON cis.verification_status_id = vstat.id"
                    + " LEFT JOIN m_sub_industry si ON cis.sub_industry_id = si.id"
                    + " LEFT JOIN m_appuser vu ON cis.verified_by = vu.id";
        }

        @Override
        public ClientIncomeSourceData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final String primaryFlag = rs.getString("isPrimarySourceFlag");
            return ClientIncomeSourceData.builder().id(rs.getLong("id")).clientId(rs.getLong("clientId"))
                    .incomeSourceTypeId(JdbcSupport.getLong(rs, "incomeSourceTypeId"))
                    .incomeSourceType(rs.getString("incomeSourceType"))
                    .sourceOfFundsId(JdbcSupport.getLong(rs, "sourceOfFundsId")).sourceOfFunds(rs.getString("sourceOfFunds"))
                    .employerBusinessName(rs.getString("employerBusinessName")).occupation(rs.getString("occupation"))
                    .subIndustryId(JdbcSupport.getLong(rs, "subIndustryId")).subIndustryName(rs.getString("subIndustryName"))
                    .monthlyIncome(rs.getBigDecimal("monthlyIncome")).incomeCurrencyCode(rs.getString("incomeCurrencyCode"))
                    .incomeFrequencyId(JdbcSupport.getLong(rs, "incomeFrequencyId"))
                    .incomeFrequency(rs.getString("incomeFrequency")).startDate(JdbcSupport.getLocalDate(rs, "startDate"))
                    .endDate(JdbcSupport.getLocalDate(rs, "endDate")).isPrimarySource("Y".equalsIgnoreCase(primaryFlag))
                    .verificationStatusId(JdbcSupport.getLong(rs, "verificationStatusId"))
                    .verificationStatus(rs.getString("verificationStatus")).verifiedBy(JdbcSupport.getLong(rs, "verifiedBy"))
                    .verifiedByUsername(rs.getString("verifiedByUsername"))
                    .verifiedOnUtc(rs.getObject("verifiedOnUtc", OffsetDateTime.class))
                    .supportingDocument(rs.getString("supportingDocument")).remarks(rs.getString("remarks"))
                    .status(rs.getString("status")).build();
        }
    }

    @Override
    public List<ClientIncomeSourceData> getClientIncomeSources(final long clientId) {
        this.context.authenticatedUser();
        final ClientIncomeSourceMapper rm = new ClientIncomeSourceMapper();
        final String sql = "SELECT " + rm.schema() + " WHERE cis.client_id = ? ORDER BY cis.is_primary_source DESC, cis.id";
        return this.jdbcTemplate.query(sql, rm, clientId);
    }

    @Override
    public ClientIncomeSourceData getClientIncomeSource(final long incomeSourceId) {
        this.context.authenticatedUser();
        final ClientIncomeSourceMapper rm = new ClientIncomeSourceMapper();
        final String sql = "SELECT " + rm.schema() + " WHERE cis.id = ?";
        return this.jdbcTemplate.queryForObject(sql, rm, incomeSourceId);
    }

    @Override
    public ClientIncomeSourceData retrieveTemplate() {
        final List<CodeValueData> incomeSourceTypeOptions = new ArrayList<>(
                this.codeValueReadPlatformService.retrieveCodeValuesByCode("IncomeSourceType"));
        final List<CodeValueData> sourceOfFundsOptions = new ArrayList<>(
                this.codeValueReadPlatformService.retrieveCodeValuesByCode("SourceOfFunds"));
        final List<CodeValueData> incomeFrequencyOptions = new ArrayList<>(
                this.codeValueReadPlatformService.retrieveCodeValuesByCode("IncomeFrequency"));
        final List<CodeValueData> verificationStatusOptions = new ArrayList<>(
                this.codeValueReadPlatformService.retrieveCodeValuesByCode("IncomeVerificationStatus"));
        return ClientIncomeSourceData.builder().incomeSourceTypeOptions(incomeSourceTypeOptions)
                .sourceOfFundsOptions(sourceOfFundsOptions).incomeFrequencyOptions(incomeFrequencyOptions)
                .verificationStatusOptions(verificationStatusOptions).build();
    }
}
