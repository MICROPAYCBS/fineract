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
import org.apache.fineract.portfolio.client.data.ContactTypeData;
import org.apache.fineract.portfolio.client.exception.ContactTypeNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactTypeReadPlatformServiceImpl implements ContactTypeReadPlatformService {

    private static final String RESOURCE_NAME = "CONTACTTYPE";

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    private static final class ContactTypeMapper implements RowMapper<ContactTypeData> {

        public String schema() {
            return " ct.id AS id, ct.type_code AS typeCode, ct.type_name AS typeName, ct.example AS example,"
                    + " ct.validation_regex AS validationRegex, ct.mandatory_ind AS mandatoryInd,"
                    + " ct.display_order AS displayOrder, ct.status AS status FROM m_contact_type ct";
        }

        @Override
        public ContactTypeData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return ContactTypeData.builder().id(rs.getLong("id")).typeCode(rs.getString("typeCode"))
                    .typeName(rs.getString("typeName")).example(rs.getString("example"))
                    .validationRegex(rs.getString("validationRegex")).mandatory("Y".equalsIgnoreCase(rs.getString("mandatoryInd")))
                    .displayOrder(JdbcSupport.getInteger(rs, "displayOrder")).status(rs.getString("status")).build();
        }
    }

    @Override
    public List<ContactTypeData> retrieveAll() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final ContactTypeMapper mapper = new ContactTypeMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " ORDER BY ct.display_order, ct.type_name", mapper);
    }

    @Override
    public ContactTypeData retrieveOne(final Long contactTypeId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final ContactTypeMapper mapper = new ContactTypeMapper();
        final List<ContactTypeData> results = this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE ct.id = ?", mapper,
                contactTypeId);
        if (results.isEmpty()) {
            throw new ContactTypeNotFoundException(contactTypeId);
        }
        return results.get(0);
    }

    @Override
    public List<ContactTypeData> retrieveActiveForClientDropdown() {
        this.context.authenticatedUser();
        final ContactTypeMapper mapper = new ContactTypeMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE ct.status = 'ACTIVE' ORDER BY ct.display_order, ct.type_name",
                mapper);
    }
}
