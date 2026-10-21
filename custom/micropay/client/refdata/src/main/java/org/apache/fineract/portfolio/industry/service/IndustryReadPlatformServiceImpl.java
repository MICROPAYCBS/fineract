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
package org.apache.fineract.portfolio.industry.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.industry.data.IndustryData;
import org.apache.fineract.portfolio.industry.exception.IndustryNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IndustryReadPlatformServiceImpl implements IndustryReadPlatformService {

    private static final String RESOURCE_NAME = "INDUSTRY";

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    private static final class IndustryMapper implements RowMapper<IndustryData> {

        public String schema() {
            return " i.id AS id, i.industry_code AS industryCode, i.industry_name AS industryName, i.description AS description,"
                    + " i.sector_id AS sectorId, s.sector_name AS sectorName, i.regulatory_code AS regulatoryCode,"
                    + " i.risk_level AS riskLevel, i.aml_risk_level AS amlRiskLevel, i.credit_risk_level AS creditRiskLevel,"
                    + " i.priority_industry_flag AS priorityIndustryFlag, i.prohibited_industry_flag AS prohibitedIndustryFlag,"
                    + " i.requires_edd AS requiresEdd, i.exposure_limit AS exposureLimit,"
                    + " i.expected_turnover_min AS expectedTurnoverMin, i.expected_turnover_max AS expectedTurnoverMax,"
                    + " i.status AS status FROM m_industry i LEFT JOIN m_sector s ON s.id = i.sector_id";
        }

        @Override
        public IndustryData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return IndustryData.builder().id(rs.getLong("id")).industryCode(rs.getString("industryCode"))
                    .industryName(rs.getString("industryName")).description(rs.getString("description"))
                    .sectorId(JdbcSupport.getLong(rs, "sectorId")).sectorName(rs.getString("sectorName"))
                    .regulatoryCode(rs.getString("regulatoryCode")).riskLevel(rs.getString("riskLevel"))
                    .amlRiskLevel(rs.getString("amlRiskLevel")).creditRiskLevel(rs.getString("creditRiskLevel"))
                    .priorityIndustry(toBoolean(rs.getString("priorityIndustryFlag")))
                    .prohibitedIndustry(toBoolean(rs.getString("prohibitedIndustryFlag")))
                    .requiresEdd(toBoolean(rs.getString("requiresEdd"))).exposureLimit(rs.getBigDecimal("exposureLimit"))
                    .expectedTurnoverMin(rs.getBigDecimal("expectedTurnoverMin"))
                    .expectedTurnoverMax(rs.getBigDecimal("expectedTurnoverMax")).status(rs.getString("status")).build();
        }

        private Boolean toBoolean(final String flag) {
            return "Y".equalsIgnoreCase(flag);
        }
    }

    @Override
    public List<IndustryData> retrieveAll() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final IndustryMapper mapper = new IndustryMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " ORDER BY i.industry_name", mapper);
    }

    @Override
    public IndustryData retrieveOne(final Long industryId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final IndustryMapper mapper = new IndustryMapper();
        final List<IndustryData> results = this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE i.id = ?", mapper, industryId);
        if (results.isEmpty()) {
            throw new IndustryNotFoundException(industryId);
        }
        return results.get(0);
    }

    @Override
    public List<IndustryData> retrieveActiveForDropdown() {
        this.context.authenticatedUser();
        final IndustryMapper mapper = new IndustryMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE i.status = 'ACTIVE' ORDER BY i.industry_name", mapper);
    }
}
