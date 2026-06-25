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
import org.apache.fineract.portfolio.client.data.ClientContactData;
import org.apache.fineract.portfolio.client.exception.ClientContactNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientContactReadPlatformServiceImpl implements ClientContactReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;

    @Override
    public List<ClientContactData> retrieveClientContacts(final Long clientId) {
        final AppUser currentUser = this.context.authenticatedUser();
        final String hierarchySearchString = currentUser.getOffice().getHierarchy() + "%";
        final ClientContactMapper mapper = new ClientContactMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " ORDER BY cc.id", mapper, clientId, hierarchySearchString);
    }

    @Override
    public ClientContactData retrieveClientContact(final Long clientId, final Long clientContactId) {
        try {
            final AppUser currentUser = this.context.authenticatedUser();
            final String hierarchySearchString = currentUser.getOffice().getHierarchy() + "%";
            final ClientContactMapper mapper = new ClientContactMapper();
            return this.jdbcTemplate.queryForObject("SELECT " + mapper.schema() + " AND cc.id = ?", mapper, clientId,
                    hierarchySearchString, clientContactId);
        } catch (final EmptyResultDataAccessException e) {
            throw new ClientContactNotFoundException(clientContactId, e);
        }
    }

    private static final class ClientContactMapper implements RowMapper<ClientContactData> {

        public String schema() {
            return " cc.id AS id, cc.client_id AS clientId, cc.contact_type_id AS contactTypeId, ct.type_code AS contactTypeCode,"
                    + " ct.type_name AS contactTypeName, ct.example AS example, ct.validation_regex AS validationRegex,"
                    + " ct.mandatory_ind AS mandatoryInd, cc.contact_value AS contactValue, cc.is_primary AS isPrimary"
                    + " FROM m_client_contact cc"
                    + " INNER JOIN m_contact_type ct ON cc.contact_type_id = ct.id"
                    + " INNER JOIN m_client c ON cc.client_id = c.id"
                    + " INNER JOIN m_office o ON c.office_id = o.id"
                    + " WHERE cc.client_id = ? AND o.hierarchy LIKE ?";
        }

        @Override
        public ClientContactData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return ClientContactData.builder().id(JdbcSupport.getLong(rs, "id")).clientId(JdbcSupport.getLong(rs, "clientId"))
                    .contactTypeId(JdbcSupport.getLong(rs, "contactTypeId")).contactTypeCode(rs.getString("contactTypeCode"))
                    .contactTypeName(rs.getString("contactTypeName")).example(rs.getString("example"))
                    .validationRegex(rs.getString("validationRegex")).mandatory("Y".equalsIgnoreCase(rs.getString("mandatoryInd")))
                    .contactValue(rs.getString("contactValue")).primary("Y".equalsIgnoreCase(rs.getString("isPrimary"))).build();
        }
    }
}
