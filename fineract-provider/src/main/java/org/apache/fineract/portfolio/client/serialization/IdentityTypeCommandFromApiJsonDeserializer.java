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
package org.apache.fineract.portfolio.client.serialization;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class IdentityTypeCommandFromApiJsonDeserializer {

    public static final String CODE_VALUE_ID = "codeValueId";
    public static final String EXAMPLE = "example";
    public static final String FORMAT_DESCRIPTION = "formatDescription";
    public static final String VALIDATION_MESSAGE = "validationMessage";
    public static final String VALIDATION_REGEX = "validationRegex";
    public static final String DISPLAY_ORDER = "displayOrder";
    public static final String STATUS = "status";
    public static final String RESOURCE = "IdentityType";

    private static final Set<String> SUPPORTED_PARAMETERS = new HashSet<>(Arrays.asList(CODE_VALUE_ID, EXAMPLE, FORMAT_DESCRIPTION,
            VALIDATION_MESSAGE, VALIDATION_REGEX, DISPLAY_ORDER, STATUS));
    private static final List<String> STATUSES = List.of("ACTIVE", "INACTIVE");

    private final FromJsonHelper fromApiJsonHelper;

    public void validateForCreate(final String json) {
        validateSupported(json);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(RESOURCE);
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        validateCommonFields(element, validator, true);
        throwValidationErrors(errors);
    }

    public void validateForUpdate(final Long identityTypeId, final String json) {
        validateSupported(json);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(RESOURCE);
        validator.reset().parameter("id").value(identityTypeId).notNull().integerGreaterThanZero();
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        validateCommonFields(element, validator, false);
        throwValidationErrors(errors);
    }

    private void validateCommonFields(final JsonElement element, final DataValidatorBuilder validator, final boolean create) {
        if (create || this.fromApiJsonHelper.parameterExists(CODE_VALUE_ID, element)) {
            final Long codeValueId = this.fromApiJsonHelper.extractLongNamed(CODE_VALUE_ID, element);
            validator.reset().parameter(CODE_VALUE_ID).value(codeValueId).notNull().integerGreaterThanZero();
        }
        if (this.fromApiJsonHelper.parameterExists(EXAMPLE, element)) {
            final String example = this.fromApiJsonHelper.extractStringNamed(EXAMPLE, element);
            validator.reset().parameter(EXAMPLE).value(example).ignoreIfNull().notExceedingLengthOf(255);
        }
        if (this.fromApiJsonHelper.parameterExists(FORMAT_DESCRIPTION, element)) {
            final String formatDescription = this.fromApiJsonHelper.extractStringNamed(FORMAT_DESCRIPTION, element);
            validator.reset().parameter(FORMAT_DESCRIPTION).value(formatDescription).ignoreIfNull().notExceedingLengthOf(500);
        }
        if (this.fromApiJsonHelper.parameterExists(VALIDATION_MESSAGE, element)) {
            final String validationMessage = this.fromApiJsonHelper.extractStringNamed(VALIDATION_MESSAGE, element);
            validator.reset().parameter(VALIDATION_MESSAGE).value(validationMessage).ignoreIfNull().notExceedingLengthOf(500);
        }
        if (this.fromApiJsonHelper.parameterExists(VALIDATION_REGEX, element)) {
            final String validationRegex = this.fromApiJsonHelper.extractStringNamed(VALIDATION_REGEX, element);
            validator.reset().parameter(VALIDATION_REGEX).value(validationRegex).ignoreIfNull().notExceedingLengthOf(500);
            validateRegexCompiles(validationRegex, validator);
        }
        if (this.fromApiJsonHelper.parameterExists(DISPLAY_ORDER, element)) {
            final Integer displayOrder = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(DISPLAY_ORDER, element);
            validator.reset().parameter(DISPLAY_ORDER).value(displayOrder).ignoreIfNull().integerZeroOrGreater();
        }
        if (create || this.fromApiJsonHelper.parameterExists(STATUS, element)) {
            final String status = this.fromApiJsonHelper.extractStringNamed(STATUS, element);
            validator.reset().parameter(STATUS).value(status).ignoreIfNull().isOneOfTheseStringValues(STATUSES);
        }
    }

    private void validateRegexCompiles(final String validationRegex, final DataValidatorBuilder validator) {
        if (StringUtils.isNotBlank(validationRegex)) {
            try {
                Pattern.compile(validationRegex);
            } catch (final PatternSyntaxException e) {
                validator.reset().parameter(VALIDATION_REGEX).value(validationRegex).failWithCode("invalid.regex.pattern");
            }
        }
    }

    private void validateSupported(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        final Map<String, Object> request = this.fromApiJsonHelper.extractObjectMap(typeOfMap, json);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(RESOURCE);
        for (final String key : request.keySet()) {
            if (!SUPPORTED_PARAMETERS.contains(key)) {
                validator.reset().parameter(key).failWithCode("is.not.one.of.expected.parameters");
            }
        }
        throwValidationErrors(errors);
    }

    private void throwValidationErrors(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }
}
