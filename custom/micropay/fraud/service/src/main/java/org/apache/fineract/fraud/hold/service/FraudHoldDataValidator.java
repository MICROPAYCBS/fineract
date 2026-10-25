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
package org.apache.fineract.fraud.hold.service;

import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.ENTITY_ID_PARAM;
import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.ENTITY_TYPE_PARAM;
import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.EXTERNAL_REFERENCE_MAX_LENGTH;
import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.EXTERNAL_REFERENCE_PARAM;
import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.NOTE_PARAM;
import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.REASON_MAX_LENGTH;
import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.REASON_PARAM;
import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.RESOURCE_NAME;
import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.SOURCE_PARAM;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.fraud.hold.data.FraudHoldEntityType;
import org.apache.fineract.fraud.hold.data.FraudHoldSource;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FraudHoldDataValidator {

    private static final Set<String> CREATE_PARAMS = Set.of(ENTITY_TYPE_PARAM, ENTITY_ID_PARAM, REASON_PARAM, SOURCE_PARAM,
            EXTERNAL_REFERENCE_PARAM);
    private static final Set<String> RELEASE_PARAMS = Set.of(NOTE_PARAM);
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final FromJsonHelper fromJsonHelper;

    public void validateForCreate(String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        fromJsonHelper.checkForUnsupportedParameters(MAP_TYPE, json, CREATE_PARAMS);
        final JsonElement element = fromJsonHelper.parse(json);

        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(RESOURCE_NAME);

        final String entityType = fromJsonHelper.extractStringNamed(ENTITY_TYPE_PARAM, element);
        validator.reset().parameter(ENTITY_TYPE_PARAM).value(entityType).notBlank()
                .isOneOfTheseStringValues(Arrays.stream(FraudHoldEntityType.values()).map(Enum::name).toList());

        final Long entityId = fromJsonHelper.extractLongNamed(ENTITY_ID_PARAM, element);
        validator.reset().parameter(ENTITY_ID_PARAM).value(entityId).notNull().longGreaterThanZero();

        final String reason = fromJsonHelper.extractStringNamed(REASON_PARAM, element);
        validator.reset().parameter(REASON_PARAM).value(reason).notBlank().notExceedingLengthOf(REASON_MAX_LENGTH);

        final String source = fromJsonHelper.extractStringNamed(SOURCE_PARAM, element);
        validator.reset().parameter(SOURCE_PARAM).value(source).ignoreIfNull()
                .isOneOfTheseStringValues(Arrays.stream(FraudHoldSource.values()).map(Enum::name).toList());

        final String externalReference = fromJsonHelper.extractStringNamed(EXTERNAL_REFERENCE_PARAM, element);
        validator.reset().parameter(EXTERNAL_REFERENCE_PARAM).value(externalReference).ignoreIfNull()
                .notExceedingLengthOf(EXTERNAL_REFERENCE_MAX_LENGTH);

        validator.throwValidationErrors();
    }

    public void validateForRelease(String json) {
        if (StringUtils.isBlank(json)) {
            return;
        }
        fromJsonHelper.checkForUnsupportedParameters(MAP_TYPE, json, RELEASE_PARAMS);
        final JsonElement element = fromJsonHelper.parse(json);

        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(RESOURCE_NAME);
        final String note = fromJsonHelper.extractStringNamed(NOTE_PARAM, element);
        validator.reset().parameter(NOTE_PARAM).value(note).ignoreIfNull().notExceedingLengthOf(REASON_MAX_LENGTH);
        validator.throwValidationErrors();
    }
}
