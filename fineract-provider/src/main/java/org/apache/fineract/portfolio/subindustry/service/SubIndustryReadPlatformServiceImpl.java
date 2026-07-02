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
package org.apache.fineract.portfolio.subindustry.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.subindustry.data.SubIndustryData;
import org.apache.fineract.portfolio.subindustry.exception.SubIndustryNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubIndustryReadPlatformServiceImpl implements SubIndustryReadPlatformService {

    private static final String RESOURCE_NAME = "SUBINDUSTRY";

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    private static final class SubIndustryMapper implements RowMapper<SubIndustryData> {

        public String schema() {
            return " si.id AS id, si.sub_industry_code AS subIndustryCode, si.sub_industry_name AS subIndustryName,"
                    + " si.description AS description, si.industry_id AS industryId, i.industry_name AS industryName,"
                    + " si.regulatory_code AS regulatoryCode, si.risk_level AS riskLevel, si.aml_risk_level AS amlRiskLevel,"
                    + " si.credit_risk_level AS creditRiskLevel, si.priority_flag AS priorityFlag, si.prohibited_flag AS prohibitedFlag,"
                    + " si.requires_edd AS requiresEdd, si.exposure_limit AS exposureLimit,"
                    + " si.expected_turnover_min AS expectedTurnoverMin, si.expected_turnover_max AS expectedTurnoverMax,"
                    + " si.status AS status FROM m_sub_industry si LEFT JOIN m_industry i ON i.id = si.industry_id";
        }

        @Override
        public SubIndustryData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return SubIndustryData.builder().id(rs.getLong("id")).subIndustryCode(rs.getString("subIndustryCode"))
                    .subIndustryName(rs.getString("subIndustryName")).description(rs.getString("description"))
                    .industryId(JdbcSupport.getLong(rs, "industryId")).industryName(rs.getString("industryName"))
                    .regulatoryCode(rs.getString("regulatoryCode")).riskLevel(rs.getString("riskLevel"))
                    .amlRiskLevel(rs.getString("amlRiskLevel")).creditRiskLevel(rs.getString("creditRiskLevel"))
                    .priority(toBoolean(rs.getString("priorityFlag"))).prohibited(toBoolean(rs.getString("prohibitedFlag")))
                    .requiresEdd(toBoolean(rs.getString("requiresEdd"))).exposureLimit(rs.getBigDecimal("exposureLimit"))
                    .expectedTurnoverMin(rs.getBigDecimal("expectedTurnoverMin"))
                    .expectedTurnoverMax(rs.getBigDecimal("expectedTurnoverMax")).status(rs.getString("status")).build();
        }

        private Boolean toBoolean(final String flag) {
            return "Y".equalsIgnoreCase(flag);
        }
    }

    @Override
    public List<SubIndustryData> retrieveAll() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final SubIndustryMapper mapper = new SubIndustryMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " ORDER BY si.sub_industry_name", mapper);
    }

    @Override
    public SubIndustryData retrieveOne(final Long subIndustryId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final SubIndustryMapper mapper = new SubIndustryMapper();
        final List<SubIndustryData> results = this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE si.id = ?", mapper,
                subIndustryId);
        if (results.isEmpty()) {
            throw new SubIndustryNotFoundException(subIndustryId);
        }
        return results.get(0);
    }

    @Override
    public List<SubIndustryData> retrieveActiveForDropdown() {
        this.context.authenticatedUser();
        final SubIndustryMapper mapper = new SubIndustryMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE si.status = 'ACTIVE' ORDER BY si.sub_industry_name",
                mapper);
    }
}
