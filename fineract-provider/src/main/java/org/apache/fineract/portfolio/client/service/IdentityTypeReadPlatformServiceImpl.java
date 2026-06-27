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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.IdentityTypeData;
import org.apache.fineract.portfolio.client.exception.IdentityTypeNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdentityTypeReadPlatformServiceImpl implements IdentityTypeReadPlatformService {

    private static final String RESOURCE_NAME = "IDENTITYTYPE";

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    private static final class IdentityTypeMapper implements RowMapper<IdentityTypeData> {

        public String schema() {
            return " it.id AS id, it.code_value_id AS codeValueId, cv.code_value AS codeValueName, it.example AS example,"
                    + " it.format_description AS formatDescription, it.validation_message AS validationMessage,"
                    + " it.validation_regex AS validationRegex, it.display_order AS displayOrder, it.status AS status"
                    + " FROM m_identity_type it INNER JOIN m_code_value cv ON it.code_value_id = cv.id";
        }

        @Override
        public IdentityTypeData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return IdentityTypeData.builder().id(rs.getLong("id")).codeValueId(rs.getLong("codeValueId"))
                    .codeValueName(rs.getString("codeValueName")).example(rs.getString("example"))
                    .formatDescription(rs.getString("formatDescription")).validationMessage(rs.getString("validationMessage"))
                    .validationRegex(rs.getString("validationRegex")).displayOrder(JdbcSupport.getInteger(rs, "displayOrder"))
                    .status(rs.getString("status")).build();
        }
    }

    @Override
    public List<IdentityTypeData> retrieveAll() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final IdentityTypeMapper mapper = new IdentityTypeMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " ORDER BY it.display_order, cv.code_value", mapper);
    }

    @Override
    public IdentityTypeData retrieveOne(final Long identityTypeId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final IdentityTypeMapper mapper = new IdentityTypeMapper();
        final List<IdentityTypeData> results = this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE it.id = ?", mapper,
                identityTypeId);
        if (results.isEmpty()) {
            throw new IdentityTypeNotFoundException(identityTypeId);
        }
        return results.get(0);
    }

    @Override
    public List<IdentityTypeData> retrieveActiveForDropdown() {
        this.context.authenticatedUser();
        final IdentityTypeMapper mapper = new IdentityTypeMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE it.status = 'ACTIVE' ORDER BY it.display_order, cv.code_value",
                mapper);
    }
}
