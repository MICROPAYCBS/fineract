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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetColumnHeaderData;

public final class DatatableColumnValidationValidator {

    private DatatableColumnValidationValidator() {}

    public static void validateStringValue(final ResultsetColumnHeaderData columnHeader, final String rawValue) {
        if (columnHeader == null || StringUtils.isBlank(rawValue)) {
            return;
        }
        if (!columnHeader.isStringDisplayType() && !columnHeader.isTextDisplayType()) {
            return;
        }

        final String value = rawValue.trim();
        if (columnHeader.getColumnLength() != null && value.length() > columnHeader.getColumnLength()) {
            throw validationException(columnHeader.getColumnName(), "validation.msg.datatable.column.exceeds.max.length",
                    "Value exceeds maximum length of " + columnHeader.getColumnLength(), "maxLength", columnHeader.getColumnLength());
        }

        final String regex = columnHeader.getValidationRegex();
        if (StringUtils.isBlank(regex)) {
            return;
        }

        try {
            if (!Pattern.compile(regex).matcher(value).matches()) {
                final String message = StringUtils.defaultIfBlank(columnHeader.getValidationMessage(),
                        "Value does not match the required format");
                throw validationException(columnHeader.getColumnName(), "validation.msg.datatable.column.invalid.format", message, "regex",
                        regex);
            }
        } catch (final PatternSyntaxException e) {
            throw validationException(columnHeader.getColumnName(), "validation.msg.datatable.column.invalid.regex.config",
                    "Column validation regex is invalid", "regex", regex);
        }
    }

    private static PlatformApiDataValidationException validationException(final String columnName, final String globalisationCode,
            final String defaultUserMessage, final String valueName, final Object value) {
        final List<ApiParameterError> errors = new ArrayList<>();
        errors.add(ApiParameterError.parameterError(globalisationCode, defaultUserMessage, columnName, valueName, value));
        throw new PlatformApiDataValidationException(errors);
    }
}
