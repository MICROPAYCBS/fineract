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
import org.apache.fineract.portfolio.client.data.ClientTitleData;
import org.apache.fineract.portfolio.client.exception.ClientTitleNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientTitleReadPlatformServiceImpl implements ClientTitleReadPlatformService {

    private static final String RESOURCE_NAME = "CLIENTTITLE";

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    private static final class ClientTitleMapper implements RowMapper<ClientTitleData> {

        public String schema() {
            return " ct.id AS id, ct.title_code AS titleCode, ct.title_name AS titleName, ct.gender_enum AS genderId,"
                    + " ct.display_order AS displayOrder, ct.status AS status FROM m_client_title ct";
        }

        @Override
        public ClientTitleData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return ClientTitleData.builder().id(rs.getLong("id")).titleCode(rs.getString("titleCode"))
                    .titleName(rs.getString("titleName")).genderId(JdbcSupport.getInteger(rs, "genderId"))
                    .displayOrder(JdbcSupport.getInteger(rs, "displayOrder")).status(rs.getString("status")).build();
        }
    }

    @Override
    public List<ClientTitleData> retrieveAll() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final ClientTitleMapper mapper = new ClientTitleMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " ORDER BY ct.display_order, ct.title_name", mapper);
    }

    @Override
    public ClientTitleData retrieveOne(final Long clientTitleId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final ClientTitleMapper mapper = new ClientTitleMapper();
        final List<ClientTitleData> results = this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE ct.id = ?", mapper,
                clientTitleId);
        if (results.isEmpty()) {
            throw new ClientTitleNotFoundException(clientTitleId);
        }
        return results.get(0);
    }

    @Override
    public List<ClientTitleData> retrieveActiveForClientDropdown() {
        this.context.authenticatedUser();
        final ClientTitleMapper mapper = new ClientTitleMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE ct.status = 'ACTIVE' ORDER BY ct.display_order, ct.title_name",
                mapper);
    }
}
