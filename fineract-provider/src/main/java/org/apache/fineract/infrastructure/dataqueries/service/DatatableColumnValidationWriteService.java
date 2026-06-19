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

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.dataqueries.data.DatatableColumnValidationData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DatatableColumnValidationWriteService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void upsertColumnValidation(final String registeredTableName, final String columnName, final String validationRegex,
            final String validationExample, final String validationMessage) {
        validateRegex(validationRegex, columnName);

        final String regex = StringUtils.trimToNull(validationRegex);
        final String example = StringUtils.trimToNull(validationExample);
        final String message = StringUtils.trimToNull(validationMessage);

        if (regex == null && example == null && message == null) {
            deleteColumnValidation(registeredTableName, columnName);
            return;
        }

        final String sql = """
                INSERT INTO x_table_column_validation
                    (registered_table_name, column_name, validation_regex, validation_example, validation_message)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    validation_regex = VALUES(validation_regex),
                    validation_example = VALUES(validation_example),
                    validation_message = VALUES(validation_message)
                """;
        jdbcTemplate.update(sql, registeredTableName, columnName, regex, example, message);
    }

    @Transactional
    public void deleteColumnValidation(final String registeredTableName, final String columnName) {
        jdbcTemplate.update("DELETE FROM x_table_column_validation WHERE registered_table_name = ? AND column_name = ?",
                registeredTableName, columnName);
    }

    @Transactional
    public void renameColumnValidation(final String registeredTableName, final String columnName, final String newColumnName) {
        if (StringUtils.isBlank(columnName) || StringUtils.isBlank(newColumnName) || columnName.equals(newColumnName)) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE x_table_column_validation SET column_name = ? WHERE registered_table_name = ? AND column_name = ?",
                newColumnName, registeredTableName, columnName);
    }

    @Transactional
    public void syncColumnValidations(final String registeredTableName,
            final List<DatatableColumnValidationEntry> columnValidations) {
        if (columnValidations == null) {
            return;
        }
        for (final DatatableColumnValidationEntry entry : columnValidations) {
            upsertColumnValidation(registeredTableName, entry.columnName(), entry.validationRegex(), entry.validationExample(),
                    entry.validationMessage());
        }
    }

    private static void validateRegex(final String validationRegex, final String columnName) {
        if (StringUtils.isBlank(validationRegex)) {
            return;
        }
        try {
            Pattern.compile(validationRegex.trim());
        } catch (final PatternSyntaxException e) {
            throw new PlatformApiDataValidationException(List.of(ApiParameterError.parameterError(
                    "validation.msg.datatable.column.invalid.regex.config", "Validation regex is invalid", columnName, "validationRegex",
                    validationRegex)));
        }
    }

    public record DatatableColumnValidationEntry(String columnName, String validationRegex, String validationExample,
            String validationMessage) {

        public static DatatableColumnValidationEntry fromData(final String columnName, final DatatableColumnValidationData data) {
            if (data == null) {
                return new DatatableColumnValidationEntry(columnName, null, null, null);
            }
            return new DatatableColumnValidationEntry(columnName, data.getValidationRegex(), data.getValidationExample(),
                    data.getValidationMessage());
        }
    }
}
