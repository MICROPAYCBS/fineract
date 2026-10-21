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
package org.apache.fineract.portfolio.department.serialization;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
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
public final class DepartmentCommandFromApiJsonDeserializer {

    public static final String DEPARTMENT_CODE = "departmentCode";
    public static final String DEPARTMENT_NAME = "departmentName";
    public static final String OFFICE_ID = "officeId";
    public static final String ACTIVE = "active";
    public static final String RESOURCE = "Department";

    private static final Set<String> SUPPORTED_PARAMETERS = new HashSet<>(
            Arrays.asList(DEPARTMENT_CODE, DEPARTMENT_NAME, OFFICE_ID, ACTIVE));

    private final FromJsonHelper fromApiJsonHelper;

    public void validateForCreate(final String json) {
        validateSupported(json);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(RESOURCE);
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        validateCommonFields(element, validator, true);
        throwValidationErrors(errors);
    }

    public void validateForUpdate(final Long departmentId, final String json) {
        validateSupported(json);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(RESOURCE);
        validator.reset().parameter("id").value(departmentId).notNull().integerGreaterThanZero();
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        validateCommonFields(element, validator, false);
        throwValidationErrors(errors);
    }

    private void validateCommonFields(final JsonElement element, final DataValidatorBuilder validator, final boolean create) {
        if (create || this.fromApiJsonHelper.parameterExists(DEPARTMENT_CODE, element)) {
            final String departmentCode = this.fromApiJsonHelper.extractStringNamed(DEPARTMENT_CODE, element);
            validator.reset().parameter(DEPARTMENT_CODE).value(departmentCode).notBlank().notExceedingLengthOf(20);
        }
        if (create || this.fromApiJsonHelper.parameterExists(DEPARTMENT_NAME, element)) {
            final String departmentName = this.fromApiJsonHelper.extractStringNamed(DEPARTMENT_NAME, element);
            validator.reset().parameter(DEPARTMENT_NAME).value(departmentName).notBlank().notExceedingLengthOf(100);
        }
        if (this.fromApiJsonHelper.parameterExists(OFFICE_ID, element)) {
            final Long officeId = this.fromApiJsonHelper.extractLongNamed(OFFICE_ID, element);
            validator.reset().parameter(OFFICE_ID).value(officeId).ignoreIfNull().integerGreaterThanZero();
        }
        if (create || this.fromApiJsonHelper.parameterExists(ACTIVE, element)) {
            final Boolean active = this.fromApiJsonHelper.extractBooleanNamed(ACTIVE, element);
            validator.reset().parameter(ACTIVE).value(active).ignoreIfNull().notNull();
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
