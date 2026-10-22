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
package org.apache.fineract.treasury.serialization;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.ACCOUNT_CREATE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.ACCOUNT_TYPE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.ACCOUNT_UPDATE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.COUNTERPARTY_NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.CURRENCY_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.EXTERNAL_ACCOUNT_NUMBER_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.GL_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MAX_BALANCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MIN_BALANCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.OFFICE_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_ACCOUNT_RESOURCE_NAME;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.data.TreasuryAccountRequest;
import org.apache.fineract.treasury.domain.TreasuryAccountType;
import org.springframework.stereotype.Component;

/**
 * Field-level validation of treasury account payloads. Checks against other data (GL account, office, currency) are
 * made by the write service.
 */
@Component
@RequiredArgsConstructor
public class TreasuryAccountDataValidator {

    private static final String CODE_PATTERN = "[A-Za-z0-9_-]{1,50}";
    private static final String CURRENCY_PATTERN = "[A-Za-z]{3}";
    private static final List<String> ACCOUNT_TYPES = Arrays.stream(TreasuryAccountType.values()).map(Enum::name).toList();

    private final FromJsonHelper fromApiJsonHelper;

    /**
     * Also upper-cases the code, account type and currency.
     */
    public TreasuryAccountRequest validateForCreate(final String json) {
        final TreasuryAccountRequest request = parse(json, ACCOUNT_CREATE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(TREASURY_ACCOUNT_RESOURCE_NAME);

        validator.reset().parameter(CODE_PARAM).value(request.getCode()).notBlank().matchesRegularExpression(CODE_PATTERN,
                "The parameter `code` must be 1 to 50 letters, digits, underscores or hyphens");
        validator.reset().parameter(NAME_PARAM).value(request.getName()).notBlank().notExceedingLengthOf(150);
        validator.reset().parameter(ACCOUNT_TYPE_PARAM).value(upper(request.getAccountType())).notBlank()
                .isOneOfTheseStringValues(ACCOUNT_TYPES);
        validator.reset().parameter(GL_ACCOUNT_ID_PARAM).value(request.getGlAccountId()).notNull().longGreaterThanZero();
        validator.reset().parameter(OFFICE_ID_PARAM).value(request.getOfficeId()).notNull().longGreaterThanZero();
        validator.reset().parameter(CURRENCY_CODE_PARAM).value(request.getCurrencyCode()).notBlank()
                .matchesRegularExpression(CURRENCY_PATTERN, "The parameter `currencyCode` must be a three-letter currency code");
        validateOptionalFields(validator, request);
        validateBalanceRange(validator, request.getMinBalance(), request.getMaxBalance());
        throwIfAny(errors);
        normalise(request);
        return request;
    }

    /**
     * Validates only the parameters present; {@link TreasuryAccountRequest#has(String)} tells the caller which to
     * apply.
     */
    public TreasuryAccountRequest validateForUpdate(final String json) {
        final TreasuryAccountRequest request = parse(json, ACCOUNT_UPDATE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(TREASURY_ACCOUNT_RESOURCE_NAME);

        if (request.getParametersPassed().isEmpty()) {
            validator.reset().anyOfNotNull(request.getName(), request.getAccountType(), request.getGlAccountId(), request.getOfficeId(),
                    request.getCurrencyCode(), request.getCounterpartyName(), request.getExternalAccountNumber(), request.getMinBalance(),
                    request.getMaxBalance());
        }
        if (request.has(NAME_PARAM)) {
            validator.reset().parameter(NAME_PARAM).value(request.getName()).notBlank().notExceedingLengthOf(150);
        }
        if (request.has(ACCOUNT_TYPE_PARAM)) {
            validator.reset().parameter(ACCOUNT_TYPE_PARAM).value(upper(request.getAccountType())).notBlank()
                    .isOneOfTheseStringValues(ACCOUNT_TYPES);
        }
        if (request.has(GL_ACCOUNT_ID_PARAM)) {
            validator.reset().parameter(GL_ACCOUNT_ID_PARAM).value(request.getGlAccountId()).notNull().longGreaterThanZero();
        }
        if (request.has(OFFICE_ID_PARAM)) {
            validator.reset().parameter(OFFICE_ID_PARAM).value(request.getOfficeId()).notNull().longGreaterThanZero();
        }
        if (request.has(CURRENCY_CODE_PARAM)) {
            validator.reset().parameter(CURRENCY_CODE_PARAM).value(request.getCurrencyCode()).notBlank()
                    .matchesRegularExpression(CURRENCY_PATTERN, "The parameter `currencyCode` must be a three-letter currency code");
        }
        validateOptionalFields(validator, request);
        validateBalanceRange(validator, request.getMinBalance(), request.getMaxBalance());
        throwIfAny(errors);
        normalise(request);
        return request;
    }

    /**
     * The minimum may not be above the maximum. Used again by the write service on the merged values of an update.
     */
    public void validateBalanceRange(final BigDecimal minBalance, final BigDecimal maxBalance) {
        final List<ApiParameterError> errors = new ArrayList<>();
        validateBalanceRange(new DataValidatorBuilder(errors).resource(TREASURY_ACCOUNT_RESOURCE_NAME), minBalance, maxBalance);
        throwIfAny(errors);
    }

    private TreasuryAccountRequest parse(final String json, final Set<String> supportedParameters) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final JsonObject object = this.fromApiJsonHelper.parse(json).getAsJsonObject();
        this.fromApiJsonHelper.checkForUnsupportedParameters(object, supportedParameters);
        final TreasuryAccountRequest request = this.fromApiJsonHelper.fromJson(json, TreasuryAccountRequest.class);
        request.setParametersPassed(new HashSet<>(object.keySet()));
        return request;
    }

    private static void validateOptionalFields(final DataValidatorBuilder validator, final TreasuryAccountRequest request) {
        validator.reset().parameter(COUNTERPARTY_NAME_PARAM).value(request.getCounterpartyName()).ignoreIfNull().notExceedingLengthOf(150);
        validator.reset().parameter(EXTERNAL_ACCOUNT_NUMBER_PARAM).value(request.getExternalAccountNumber()).ignoreIfNull()
                .notExceedingLengthOf(100);
    }

    private static void validateBalanceRange(final DataValidatorBuilder validator, final BigDecimal min, final BigDecimal max) {
        validator.reset().parameter(MIN_BALANCE_PARAM).value(min).ignoreIfNull().zeroOrPositiveAmount();
        validator.reset().parameter(MAX_BALANCE_PARAM).value(max).ignoreIfNull().zeroOrPositiveAmount();
        if (min != null && max != null && min.compareTo(max) > 0) {
            validator.reset().parameter(MIN_BALANCE_PARAM).value(min).failWithCode("greater.than." + MAX_BALANCE_PARAM, max);
        }
    }

    private static void normalise(final TreasuryAccountRequest request) {
        request.setCode(upper(request.getCode()));
        request.setAccountType(upper(request.getAccountType()));
        request.setCurrencyCode(upper(request.getCurrencyCode()));
        request.setName(StringUtils.trimToNull(request.getName()));
        request.setCounterpartyName(StringUtils.trimToNull(request.getCounterpartyName()));
        request.setExternalAccountNumber(StringUtils.trimToNull(request.getExternalAccountNumber()));
    }

    private static String upper(final String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private static void throwIfAny(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }
}
