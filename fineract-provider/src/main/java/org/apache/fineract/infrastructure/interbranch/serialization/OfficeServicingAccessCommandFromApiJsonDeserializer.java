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
package org.apache.fineract.infrastructure.interbranch.serialization;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public final class OfficeServicingAccessCommandFromApiJsonDeserializer {

    public static final String SERVICING_OFFICE_ID = "servicingOfficeId";
    public static final String BOOK_OFFICE_ID = "bookOfficeId";
    public static final String EFFECTIVE_FROM = "effectiveFrom";
    public static final String EFFECTIVE_TO = "effectiveTo";
    public static final String STATUS = "status";
    public static final String RESOURCE = "OfficeServicingAccess";

    private static final Set<String> SUPPORTED_PARAMETERS = new HashSet<>(
            Arrays.asList(SERVICING_OFFICE_ID, BOOK_OFFICE_ID, EFFECTIVE_FROM, EFFECTIVE_TO, STATUS));
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

    public void validateForUpdate(final Long accessId, final String json) {
        validateSupported(json);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(RESOURCE);
        validator.reset().parameter("id").value(accessId).notNull().integerGreaterThanZero();
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        validateCommonFields(element, validator, false);
        throwValidationErrors(errors);
    }

    private void validateCommonFields(final JsonElement element, final DataValidatorBuilder validator, final boolean create) {
        if (create || this.fromApiJsonHelper.parameterExists(SERVICING_OFFICE_ID, element)) {
            final Long servicingOfficeId = this.fromApiJsonHelper.extractLongNamed(SERVICING_OFFICE_ID, element);
            validator.reset().parameter(SERVICING_OFFICE_ID).value(servicingOfficeId).notNull().integerGreaterThanZero();
        }
        if (create || this.fromApiJsonHelper.parameterExists(BOOK_OFFICE_ID, element)) {
            final Long bookOfficeId = this.fromApiJsonHelper.extractLongNamed(BOOK_OFFICE_ID, element);
            validator.reset().parameter(BOOK_OFFICE_ID).value(bookOfficeId).notNull().integerGreaterThanZero();
        }
        if (create || this.fromApiJsonHelper.parameterExists(EFFECTIVE_FROM, element)) {
            final LocalDate effectiveFrom = this.fromApiJsonHelper.extractLocalDateNamed(EFFECTIVE_FROM, element);
            validator.reset().parameter(EFFECTIVE_FROM).value(effectiveFrom).notNull();
        }
        if (this.fromApiJsonHelper.parameterExists(EFFECTIVE_TO, element)) {
            final LocalDate effectiveTo = this.fromApiJsonHelper.extractLocalDateNamed(EFFECTIVE_TO, element);
            validator.reset().parameter(EFFECTIVE_TO).value(effectiveTo).ignoreIfNull();
        }
        if (create || this.fromApiJsonHelper.parameterExists(STATUS, element)) {
            final String status = this.fromApiJsonHelper.extractStringNamed(STATUS, element);
            validator.reset().parameter(STATUS).value(status).ignoreIfNull().isOneOfTheseStringValues(STATUSES);
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
