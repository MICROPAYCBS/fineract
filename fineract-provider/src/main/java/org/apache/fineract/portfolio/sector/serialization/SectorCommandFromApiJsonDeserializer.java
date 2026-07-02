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
package org.apache.fineract.portfolio.sector.serialization;

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
public final class SectorCommandFromApiJsonDeserializer {

    public static final String SECTOR_CODE = "sectorCode";
    public static final String SECTOR_NAME = "sectorName";
    public static final String DESCRIPTION = "description";
    public static final String PARENT_ID = "parentId";
    public static final String RISK_LEVEL = "riskLevel";
    public static final String REGULATORY_CODE = "regulatoryCode";
    public static final String STATUS = "status";
    public static final String RESOURCE = "Sector";

    private static final Set<String> SUPPORTED_PARAMETERS = new HashSet<>(Arrays.asList(SECTOR_CODE, SECTOR_NAME, DESCRIPTION, PARENT_ID,
            RISK_LEVEL, REGULATORY_CODE, STATUS));
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

    public void validateForUpdate(final Long sectorId, final String json) {
        validateSupported(json);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(RESOURCE);
        validator.reset().parameter("id").value(sectorId).notNull().integerGreaterThanZero();
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        validateCommonFields(element, validator, false);
        throwValidationErrors(errors);
    }

    private void validateCommonFields(final JsonElement element, final DataValidatorBuilder validator, final boolean create) {
        if (create || this.fromApiJsonHelper.parameterExists(SECTOR_CODE, element)) {
            final String sectorCode = this.fromApiJsonHelper.extractStringNamed(SECTOR_CODE, element);
            validator.reset().parameter(SECTOR_CODE).value(sectorCode).notBlank().notExceedingLengthOf(20);
        }
        if (create || this.fromApiJsonHelper.parameterExists(SECTOR_NAME, element)) {
            final String sectorName = this.fromApiJsonHelper.extractStringNamed(SECTOR_NAME, element);
            validator.reset().parameter(SECTOR_NAME).value(sectorName).notBlank().notExceedingLengthOf(100);
        }
        if (this.fromApiJsonHelper.parameterExists(DESCRIPTION, element)) {
            final String description = this.fromApiJsonHelper.extractStringNamed(DESCRIPTION, element);
            validator.reset().parameter(DESCRIPTION).value(description).ignoreIfNull().notExceedingLengthOf(255);
        }
        if (this.fromApiJsonHelper.parameterExists(PARENT_ID, element)) {
            final Long parentId = this.fromApiJsonHelper.extractLongNamed(PARENT_ID, element);
            validator.reset().parameter(PARENT_ID).value(parentId).ignoreIfNull().integerGreaterThanZero();
        }
        if (this.fromApiJsonHelper.parameterExists(RISK_LEVEL, element)) {
            final String riskLevel = this.fromApiJsonHelper.extractStringNamed(RISK_LEVEL, element);
            validator.reset().parameter(RISK_LEVEL).value(riskLevel).ignoreIfNull().notExceedingLengthOf(20);
        }
        if (this.fromApiJsonHelper.parameterExists(REGULATORY_CODE, element)) {
            final String regulatoryCode = this.fromApiJsonHelper.extractStringNamed(REGULATORY_CODE, element);
            validator.reset().parameter(REGULATORY_CODE).value(regulatoryCode).ignoreIfNull().notExceedingLengthOf(20);
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
