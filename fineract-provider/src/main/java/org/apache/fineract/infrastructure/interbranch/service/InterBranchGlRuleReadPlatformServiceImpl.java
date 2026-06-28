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
package org.apache.fineract.infrastructure.interbranch.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.interbranch.data.InterBranchGlRuleData;
import org.apache.fineract.infrastructure.interbranch.exception.InterBranchGlRuleNotFoundException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterBranchGlRuleReadPlatformServiceImpl implements InterBranchGlRuleReadPlatformService {

    private static final String RESOURCE_NAME = "INTERBRANCHRULE";

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    private static final class InterBranchGlRuleMapper implements RowMapper<InterBranchGlRuleData> {

        public String schema() {
            return " r.id AS id, r.left_office_id AS leftOfficeId, lo.name AS leftOfficeName,"
                    + " r.right_office_id AS rightOfficeId, ro.name AS rightOfficeName,"
                    + " r.gl_account_id AS glAccountId, gl.name AS glAccountName, gl.gl_code AS glAccountCode,"
                    + " r.currency_code AS currencyCode, r.status AS status"
                    + " FROM m_inter_branch_gl_rule r"
                    + " INNER JOIN acc_gl_account gl ON r.gl_account_id = gl.id"
                    + " LEFT JOIN m_office lo ON r.left_office_id = lo.id"
                    + " LEFT JOIN m_office ro ON r.right_office_id = ro.id";
        }

        @Override
        public InterBranchGlRuleData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return InterBranchGlRuleData.builder().id(rs.getLong("id")).leftOfficeId(JdbcSupport.getLong(rs, "leftOfficeId"))
                    .leftOfficeName(rs.getString("leftOfficeName")).rightOfficeId(JdbcSupport.getLong(rs, "rightOfficeId"))
                    .rightOfficeName(rs.getString("rightOfficeName")).glAccountId(rs.getLong("glAccountId"))
                    .glAccountName(rs.getString("glAccountName")).glAccountCode(rs.getString("glAccountCode"))
                    .currencyCode(rs.getString("currencyCode")).status(rs.getString("status")).build();
        }
    }

    @Override
    public List<InterBranchGlRuleData> retrieveAll() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final InterBranchGlRuleMapper mapper = new InterBranchGlRuleMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " ORDER BY r.id", mapper);
    }

    @Override
    public InterBranchGlRuleData retrieveOne(final Long ruleId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final InterBranchGlRuleMapper mapper = new InterBranchGlRuleMapper();
        final List<InterBranchGlRuleData> results = this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE r.id = ?", mapper,
                ruleId);
        if (results.isEmpty()) {
            throw new InterBranchGlRuleNotFoundException(ruleId);
        }
        return results.getFirst();
    }
}
