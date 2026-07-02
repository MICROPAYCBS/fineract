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
package org.apache.fineract.portfolio.sector.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.sector.data.SectorData;
import org.apache.fineract.portfolio.sector.exception.SectorNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SectorReadPlatformServiceImpl implements SectorReadPlatformService {

    private static final String RESOURCE_NAME = "SECTOR";

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    private static final class SectorMapper implements RowMapper<SectorData> {

        public String schema() {
            return " s.id AS id, s.sector_code AS sectorCode, s.sector_name AS sectorName, s.description AS description,"
                    + " s.parent_id AS parentId, ps.sector_name AS parentSectorName, s.risk_level AS riskLevel,"
                    + " s.regulatory_code AS regulatoryCode, s.status AS status FROM m_sector s"
                    + " LEFT JOIN m_sector ps ON ps.id = s.parent_id";
        }

        @Override
        public SectorData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return SectorData.builder().id(rs.getLong("id")).sectorCode(rs.getString("sectorCode"))
                    .sectorName(rs.getString("sectorName")).description(rs.getString("description"))
                    .parentId(JdbcSupport.getLong(rs, "parentId")).parentSectorName(rs.getString("parentSectorName"))
                    .riskLevel(rs.getString("riskLevel")).regulatoryCode(rs.getString("regulatoryCode"))
                    .status(rs.getString("status")).build();
        }
    }

    @Override
    public List<SectorData> retrieveAll() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final SectorMapper mapper = new SectorMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " ORDER BY s.sector_name", mapper);
    }

    @Override
    public SectorData retrieveOne(final Long sectorId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final SectorMapper mapper = new SectorMapper();
        final List<SectorData> results = this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE s.id = ?", mapper, sectorId);
        if (results.isEmpty()) {
            throw new SectorNotFoundException(sectorId);
        }
        return results.get(0);
    }

    @Override
    public List<SectorData> retrieveActiveForDropdown() {
        this.context.authenticatedUser();
        final SectorMapper mapper = new SectorMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE s.status = 'ACTIVE' ORDER BY s.sector_name", mapper);
    }
}
