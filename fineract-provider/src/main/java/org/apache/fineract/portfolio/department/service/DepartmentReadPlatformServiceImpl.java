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
package org.apache.fineract.portfolio.department.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.department.data.DepartmentData;
import org.apache.fineract.portfolio.department.exception.DepartmentNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DepartmentReadPlatformServiceImpl implements DepartmentReadPlatformService {

    private static final String RESOURCE_NAME = "DEPARTMENT";

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    private static final class DepartmentMapper implements RowMapper<DepartmentData> {

        public String schema() {
            return " d.id AS id, d.department_code AS departmentCode, d.department_name AS departmentName,"
                    + " d.office_id AS officeId, o.name AS officeName, d.active AS active FROM m_department d"
                    + " LEFT JOIN m_office o ON o.id = d.office_id";
        }

        @Override
        public DepartmentData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return DepartmentData.builder().id(rs.getLong("id")).departmentCode(rs.getString("departmentCode"))
                    .departmentName(rs.getString("departmentName")).officeId(JdbcSupport.getLong(rs, "officeId"))
                    .officeName(rs.getString("officeName")).active(rs.getBoolean("active")).build();
        }
    }

    @Override
    public List<DepartmentData> retrieveAll() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final DepartmentMapper mapper = new DepartmentMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " ORDER BY d.department_name", mapper);
    }

    @Override
    public DepartmentData retrieveOne(final Long departmentId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final DepartmentMapper mapper = new DepartmentMapper();
        final List<DepartmentData> results = this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE d.id = ?", mapper, departmentId);
        if (results.isEmpty()) {
            throw new DepartmentNotFoundException(departmentId);
        }
        return results.get(0);
    }

    @Override
    public List<DepartmentData> retrieveActiveForDropdown() {
        this.context.authenticatedUser();
        final DepartmentMapper mapper = new DepartmentMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE d.active = true ORDER BY d.department_name", mapper);
    }
}
