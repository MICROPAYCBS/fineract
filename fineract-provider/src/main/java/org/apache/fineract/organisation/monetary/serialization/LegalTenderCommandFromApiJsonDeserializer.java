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
package org.apache.fineract.organisation.monetary.serialization;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.math.BigDecimal;
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
import org.apache.fineract.organisation.teller.domain.LegalTenderType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class LegalTenderCommandFromApiJsonDeserializer {

    public static final String VALUE = "value";
    public static final String TENDER_TYPE = "tenderType";
    public static final String LABEL = "label";
    public static final String DISPLAY_ORDER = "displayOrder";
    public static final String ACTIVE = "active";
    public static final String RESOURCE = "LegalTender";

    private static final Set<String> SUPPORTED_PARAMETERS = new HashSet<>(
            Arrays.asList(VALUE, TENDER_TYPE, LABEL, DISPLAY_ORDER, ACTIVE));
    private static final List<String> TENDER_TYPES = List.of(LegalTenderType.NOTE.getCode(), LegalTenderType.COIN.getCode());

    private final FromJsonHelper fromApiJsonHelper;

    public void validateForCreate(final String json) {
        validateSupported(json);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(RESOURCE);
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        validateCommonFields(element, validator, true);
        throwValidationErrors(errors);
    }

    public void validateForUpdate(final String json) {
        validateSupported(json);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(RESOURCE);
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        validateCommonFields(element, validator, false);
        throwValidationErrors(errors);
    }

    private void validateCommonFields(final JsonElement element, final DataValidatorBuilder validator, final boolean create) {
        if (create || this.fromApiJsonHelper.parameterExists(VALUE, element)) {
            final BigDecimal value = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed(VALUE, element);
            validator.reset().parameter(VALUE).value(value).notNull().positiveAmount();
        }
        if (create || this.fromApiJsonHelper.parameterExists(TENDER_TYPE, element)) {
            final String tenderType = this.fromApiJsonHelper.extractStringNamed(TENDER_TYPE, element);
            validator.reset().parameter(TENDER_TYPE).value(tenderType).notBlank().isOneOfTheseValues(TENDER_TYPES.toArray(new String[0]));
        }
        if (create || this.fromApiJsonHelper.parameterExists(LABEL, element)) {
            final String label = this.fromApiJsonHelper.extractStringNamed(LABEL, element);
            validator.reset().parameter(LABEL).value(label).notBlank().notExceedingLengthOf(100);
        }
        if (create || this.fromApiJsonHelper.parameterExists(DISPLAY_ORDER, element)) {
            final Integer displayOrder = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(DISPLAY_ORDER, element);
            validator.reset().parameter(DISPLAY_ORDER).value(displayOrder).notNull().integerZeroOrGreater();
        }
        if (this.fromApiJsonHelper.parameterExists(ACTIVE, element)) {
            final Boolean active = this.fromApiJsonHelper.extractBooleanNamed(ACTIVE, element);
            validator.reset().parameter(ACTIVE).value(active).notNull();
        }
    }

    private void validateSupported(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {

        }.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, SUPPORTED_PARAMETERS);
    }

    private void throwValidationErrors(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.", errors);
        }
    }
}
