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
package org.apache.fineract.infrastructure.dataqueries.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.dataqueries.data.DatatableColumnValidationData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetColumnHeaderData;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DatatableColumnValidationReadService {

    private final JdbcTemplate jdbcTemplate;

    public List<ResultsetColumnHeaderData> enrichColumnHeaders(final String registeredTableName,
            final List<ResultsetColumnHeaderData> columnHeaders) {
        final Map<String, DatatableColumnValidationData> validations = retrieveColumnValidationsForTable(registeredTableName);
        if (validations.isEmpty()) {
            return columnHeaders;
        }
        return columnHeaders.stream().map(columnHeader -> {
            final DatatableColumnValidationData validation = validations.get(columnHeader.getColumnName());
            return validation != null ? columnHeader.withValidation(validation) : columnHeader;
        }).toList();
    }

    public Map<String, DatatableColumnValidationData> retrieveColumnValidationsForTable(final String registeredTableName) {
        final Map<String, DatatableColumnValidationData> validations = new HashMap<>();
        if (StringUtils.isBlank(registeredTableName)) {
            return validations;
        }

        final String sql = """
                SELECT column_name, validation_regex, validation_example, validation_message
                FROM x_table_column_validation
                WHERE registered_table_name = ?
                """;
        try {
            jdbcTemplate.query(sql, rs -> {
                final String columnName = rs.getString("column_name");
                final String validationRegex = rs.getString("validation_regex");
                final String validationExample = rs.getString("validation_example");
                final String validationMessage = rs.getString("validation_message");
                validations.put(columnName,
                        new DatatableColumnValidationData(validationRegex, validationExample, validationMessage));
            }, registeredTableName);
        } catch (final DataAccessException e) {
            log.debug("Datatable column validation metadata unavailable for {}: {}", registeredTableName, e.getMessage());
        }

        return validations;
    }
}
