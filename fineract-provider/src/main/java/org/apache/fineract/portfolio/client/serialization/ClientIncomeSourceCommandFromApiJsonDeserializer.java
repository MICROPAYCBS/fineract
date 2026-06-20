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
import java.math.BigDecimal;
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
public final class ClientIncomeSourceCommandFromApiJsonDeserializer {

    public static final String INCOME_SOURCE_TYPE_ID = "incomeSourceTypeId";
    public static final String SOURCE_OF_FUNDS_ID = "sourceOfFundsId";
    public static final String EMPLOYER_BUSINESS_NAME = "employerBusinessName";
    public static final String OCCUPATION = "occupation";
    public static final String SUB_INDUSTRY_ID = "subIndustryId";
    public static final String MONTHLY_INCOME = "monthlyIncome";
    public static final String INCOME_CURRENCY_CODE = "incomeCurrencyCode";
    public static final String INCOME_FREQUENCY_ID = "incomeFrequencyId";
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";
    public static final String IS_PRIMARY_SOURCE = "isPrimarySource";
    public static final String VERIFICATION_STATUS_ID = "verificationStatusId";
    public static final String SUPPORTING_DOCUMENT = "supportingDocument";
    public static final String REMARKS = "remarks";
    public static final String STATUS = "status";
    public static final String LOCALE = "locale";
    public static final String DATE_FORMAT = "dateFormat";
    public static final String INCOME_SOURCES = "incomeSources";
    public static final String RESOURCE = "IncomeSources";

    private static final Set<String> SUPPORTED_PARAMETERS = new HashSet<>(Arrays.asList(INCOME_SOURCE_TYPE_ID, SOURCE_OF_FUNDS_ID,
            EMPLOYER_BUSINESS_NAME, OCCUPATION, SUB_INDUSTRY_ID, MONTHLY_INCOME, INCOME_CURRENCY_CODE, INCOME_FREQUENCY_ID, START_DATE,
            END_DATE, IS_PRIMARY_SOURCE, VERIFICATION_STATUS_ID, SUPPORTING_DOCUMENT, REMARKS, STATUS, LOCALE, DATE_FORMAT, INCOME_SOURCES,
            "id", "clientId"));

    private final FromJsonHelper fromApiJsonHelper;

    public void validateForCreate(final long clientId, final String json) {
        validateSupported(json);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE);
        baseDataValidator.reset().value(clientId).notBlank().integerGreaterThanZero();
        validateCommonFields(json, baseDataValidator, true);
        throwValidationErrors(dataValidationErrors);
    }

    public void validateForUpdate(final long incomeSourceId, final String json) {
        validateSupported(json);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE);
        baseDataValidator.reset().value(incomeSourceId).notBlank().integerGreaterThanZero();
        validateCommonFields(json, baseDataValidator, false);
        throwValidationErrors(dataValidationErrors);
    }

    public void validateForDelete(final long incomeSourceId) {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE);
        baseDataValidator.reset().value(incomeSourceId).notBlank().integerGreaterThanZero();
        throwValidationErrors(dataValidationErrors);
    }

    private void validateSupported(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, SUPPORTED_PARAMETERS);
    }

    private void validateCommonFields(final String json, final DataValidatorBuilder baseDataValidator, final boolean requireType) {
        final JsonElement element = this.fromApiJsonHelper.parse(json);

        if (requireType || this.fromApiJsonHelper.parameterExists(INCOME_SOURCE_TYPE_ID, element)) {
            final Long incomeSourceTypeId = this.fromApiJsonHelper.extractLongNamed(INCOME_SOURCE_TYPE_ID, element);
            if (requireType) {
                baseDataValidator.reset().parameter(INCOME_SOURCE_TYPE_ID).value(incomeSourceTypeId).notNull().integerGreaterThanZero();
            } else {
                baseDataValidator.reset().parameter(INCOME_SOURCE_TYPE_ID).value(incomeSourceTypeId).ignoreIfNull()
                        .integerGreaterThanZero();
            }
        }

        if (this.fromApiJsonHelper.parameterExists(SOURCE_OF_FUNDS_ID, element)) {
            final Long sourceOfFundsId = this.fromApiJsonHelper.extractLongNamed(SOURCE_OF_FUNDS_ID, element);
            baseDataValidator.reset().parameter(SOURCE_OF_FUNDS_ID).value(sourceOfFundsId).ignoreIfNull().integerGreaterThanZero();
        }

        if (this.fromApiJsonHelper.parameterExists(EMPLOYER_BUSINESS_NAME, element)) {
            final String value = this.fromApiJsonHelper.extractStringNamed(EMPLOYER_BUSINESS_NAME, element);
            baseDataValidator.reset().parameter(EMPLOYER_BUSINESS_NAME).value(value).ignoreIfNull().notExceedingLengthOf(200);
        }

        if (this.fromApiJsonHelper.parameterExists(OCCUPATION, element)) {
            final String value = this.fromApiJsonHelper.extractStringNamed(OCCUPATION, element);
            baseDataValidator.reset().parameter(OCCUPATION).value(value).ignoreIfNull().notExceedingLengthOf(100);
        }

        if (this.fromApiJsonHelper.parameterExists(SUB_INDUSTRY_ID, element)) {
            final Long subIndustryId = this.fromApiJsonHelper.extractLongNamed(SUB_INDUSTRY_ID, element);
            baseDataValidator.reset().parameter(SUB_INDUSTRY_ID).value(subIndustryId).ignoreIfNull().integerGreaterThanZero();
        }

        if (this.fromApiJsonHelper.parameterExists(MONTHLY_INCOME, element)) {
            final BigDecimal monthlyIncome = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed(MONTHLY_INCOME, element);
            baseDataValidator.reset().parameter(MONTHLY_INCOME).value(monthlyIncome).ignoreIfNull().zeroOrPositiveAmount();
        }

        if (this.fromApiJsonHelper.parameterExists(INCOME_CURRENCY_CODE, element)) {
            final String value = this.fromApiJsonHelper.extractStringNamed(INCOME_CURRENCY_CODE, element);
            baseDataValidator.reset().parameter(INCOME_CURRENCY_CODE).value(value).ignoreIfNull().notExceedingLengthOf(3);
        }

        if (this.fromApiJsonHelper.parameterExists(INCOME_FREQUENCY_ID, element)) {
            final Long incomeFrequencyId = this.fromApiJsonHelper.extractLongNamed(INCOME_FREQUENCY_ID, element);
            baseDataValidator.reset().parameter(INCOME_FREQUENCY_ID).value(incomeFrequencyId).ignoreIfNull().integerGreaterThanZero();
        }

        final LocalDate startDate = this.fromApiJsonHelper.extractLocalDateNamed(START_DATE, element);
        final LocalDate endDate = this.fromApiJsonHelper.extractLocalDateNamed(END_DATE, element);
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            baseDataValidator.reset().parameter(END_DATE).failWithCode("must.be.on.or.after.start.date");
        }

        if (this.fromApiJsonHelper.parameterExists(SUPPORTING_DOCUMENT, element)) {
            final String value = this.fromApiJsonHelper.extractStringNamed(SUPPORTING_DOCUMENT, element);
            baseDataValidator.reset().parameter(SUPPORTING_DOCUMENT).value(value).ignoreIfNull().notExceedingLengthOf(255);
        }

        if (this.fromApiJsonHelper.parameterExists(REMARKS, element)) {
            final String value = this.fromApiJsonHelper.extractStringNamed(REMARKS, element);
            baseDataValidator.reset().parameter(REMARKS).value(value).ignoreIfNull().notExceedingLengthOf(500);
        }
    }

    private void throwValidationErrors(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }
}
