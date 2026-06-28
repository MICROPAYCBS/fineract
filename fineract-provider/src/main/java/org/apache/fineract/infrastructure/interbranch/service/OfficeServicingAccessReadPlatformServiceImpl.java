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
import org.apache.fineract.infrastructure.interbranch.data.OfficeServicingAccessData;
import org.apache.fineract.infrastructure.interbranch.exception.OfficeServicingAccessNotFoundException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OfficeServicingAccessReadPlatformServiceImpl implements OfficeServicingAccessReadPlatformService {

    private static final String RESOURCE_NAME = "SERVICINGACCESS";

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    private static final class OfficeServicingAccessMapper implements RowMapper<OfficeServicingAccessData> {

        public String schema() {
            return " osa.id AS id, osa.servicing_office_id AS servicingOfficeId, so.name AS servicingOfficeName,"
                    + " osa.book_office_id AS bookOfficeId, bo.name AS bookOfficeName,"
                    + " osa.effective_from AS effectiveFrom, osa.effective_to AS effectiveTo, osa.status AS status"
                    + " FROM m_office_servicing_access osa"
                    + " INNER JOIN m_office so ON osa.servicing_office_id = so.id"
                    + " INNER JOIN m_office bo ON osa.book_office_id = bo.id";
        }

        @Override
        public OfficeServicingAccessData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return OfficeServicingAccessData.builder().id(rs.getLong("id")).servicingOfficeId(rs.getLong("servicingOfficeId"))
                    .servicingOfficeName(rs.getString("servicingOfficeName")).bookOfficeId(rs.getLong("bookOfficeId"))
                    .bookOfficeName(rs.getString("bookOfficeName")).effectiveFrom(JdbcSupport.getLocalDate(rs, "effectiveFrom"))
                    .effectiveTo(JdbcSupport.getLocalDate(rs, "effectiveTo")).status(rs.getString("status")).build();
        }
    }

    @Override
    public List<OfficeServicingAccessData> retrieveAll() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final OfficeServicingAccessMapper mapper = new OfficeServicingAccessMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " ORDER BY so.name, bo.name, osa.effective_from", mapper);
    }

    @Override
    public List<OfficeServicingAccessData> retrieveByServicingOffice(final Long servicingOfficeId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final OfficeServicingAccessMapper mapper = new OfficeServicingAccessMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE osa.servicing_office_id = ? ORDER BY bo.name, osa.effective_from",
                mapper, servicingOfficeId);
    }

    @Override
    public OfficeServicingAccessData retrieveOne(final Long accessId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final OfficeServicingAccessMapper mapper = new OfficeServicingAccessMapper();
        final List<OfficeServicingAccessData> results = this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE osa.id = ?", mapper,
                accessId);
        if (results.isEmpty()) {
            throw new OfficeServicingAccessNotFoundException(accessId);
        }
        return results.getFirst();
    }
}
