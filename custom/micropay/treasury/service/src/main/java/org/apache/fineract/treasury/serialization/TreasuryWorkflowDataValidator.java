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

import static org.apache.fineract.treasury.api.TreasuryApiConstants.ACTION_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.AGENT_FLOAT_CREATE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.AGENT_FLOAT_UPDATE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.ALLOW_INSUFFICIENT_BALANCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.AMOUNT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.CASH_IN_TRANSIT_GL_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.CLIENT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.CURRENCY_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.DATE_FORMAT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.DESCRIPTION_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.FLOAT_PAYMENT_TYPE_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.FLOAT_REQUEST_CREATE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.FROM_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.LOCALE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MAX_FLOAT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MIN_FLOAT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.NOTE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.REASON_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.RECON_REFERENCE_RULE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.RECON_SAME_DATE_RULE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.RECON_WINDOW_DAYS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.RECON_WINDOW_RULE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.REFERENCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.REQUEST_TYPE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.SAVINGS_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.SETTING_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TO_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TRANSACTION_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TRANSFER_CREATE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TRANSFER_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_AGENT_FLOAT_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_FLOAT_REQUEST_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_SETTING_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_TRANSFER_RESOURCE_NAME;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.domain.TreasuryFloatRequestType;
import org.springframework.stereotype.Component;

/**
 * Field-level validation of transfer, setting, agent float and float request payloads. Dates are ISO
 * ({@code 2026-10-16}) unless a {@code dateFormat} (and optionally {@code locale}) is sent, as elsewhere in Fineract.
 */
@Component
@RequiredArgsConstructor
public class TreasuryWorkflowDataValidator {

    private static final String CURRENCY_PATTERN = "[A-Za-z]{3}";
    private static final List<String> REQUEST_TYPES = Arrays.stream(TreasuryFloatRequestType.values()).map(Enum::name).toList();

    private final FromJsonHelper fromApiJsonHelper;

    public record TransferRequest(Long fromAccountId, Long toAccountId, BigDecimal amount, LocalDate transferDate, String reference,
            String description) {
    }

    /** Body of a workflow step; both fields are optional. */
    public record ActionRequest(LocalDate transactionDate, String note, String reason) {

        public static final ActionRequest EMPTY = new ActionRequest(null, null, null);
    }

    /** Only the fields present are changed. */
    public record SettingRequest(String currencyCode, Set<String> parametersPassed, Long cashInTransitGlAccountId, Long floatPaymentTypeId,
            Boolean allowInsufficientBalance, Boolean reconReferenceRule, Boolean reconSameDateRule, Boolean reconWindowRule,
            Integer reconWindowDays) {

        public boolean has(final String parameterName) {
            return this.parametersPassed.contains(parameterName);
        }
    }

    public record AgentFloatRequest(Set<String> parametersPassed, Long clientId, Long savingsAccountId, Long treasuryAccountId,
            BigDecimal minFloat, BigDecimal maxFloat) {

        public boolean has(final String parameterName) {
            return this.parametersPassed.contains(parameterName);
        }
    }

    public record FloatRequestRequest(TreasuryFloatRequestType requestType, BigDecimal amount, String note) {
    }

    public TransferRequest validateTransferForCreate(final String json) {
        final JsonObject object = parse(json, TRANSFER_CREATE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(TREASURY_TRANSFER_RESOURCE_NAME);
        final Long fromAccountId = this.fromApiJsonHelper.extractLongNamed(FROM_ACCOUNT_ID_PARAM, object);
        final Long toAccountId = this.fromApiJsonHelper.extractLongNamed(TO_ACCOUNT_ID_PARAM, object);
        final BigDecimal amount = amount(object, AMOUNT_PARAM);
        final LocalDate transferDate = date(object, TRANSFER_DATE_PARAM, validator);
        final String reference = trimmed(object, REFERENCE_PARAM);
        final String description = trimmed(object, DESCRIPTION_PARAM);

        validator.reset().parameter(FROM_ACCOUNT_ID_PARAM).value(fromAccountId).notNull().longGreaterThanZero();
        validator.reset().parameter(TO_ACCOUNT_ID_PARAM).value(toAccountId).notNull().longGreaterThanZero();
        if (fromAccountId != null && Objects.equals(fromAccountId, toAccountId)) {
            validator.reset().parameter(TO_ACCOUNT_ID_PARAM).value(toAccountId).failWithCode("same.as.fromAccountId");
        }
        validator.reset().parameter(AMOUNT_PARAM).value(amount).notNull().positiveAmount();
        validator.reset().parameter(REFERENCE_PARAM).value(reference).ignoreIfNull().notExceedingLengthOf(100);
        validator.reset().parameter(DESCRIPTION_PARAM).value(description).ignoreIfNull().notExceedingLengthOf(500);
        throwIfAny(errors);
        return new TransferRequest(fromAccountId, toAccountId, amount, transferDate, reference, description);
    }

    public ActionRequest validateAction(final String resource, final String json) {
        if (StringUtils.isBlank(json)) {
            return ActionRequest.EMPTY;
        }
        final JsonObject object = parse(json, ACTION_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(resource);
        final LocalDate transactionDate = date(object, TRANSACTION_DATE_PARAM, validator);
        final String note = trimmed(object, NOTE_PARAM);
        validator.reset().parameter(NOTE_PARAM).value(note).ignoreIfNull().notExceedingLengthOf(500);
        final String reason = trimmed(object, REASON_PARAM);
        validator.reset().parameter(REASON_PARAM).value(reason).ignoreIfNull().notExceedingLengthOf(500);
        throwIfAny(errors);
        return new ActionRequest(transactionDate, note, reason);
    }

    public SettingRequest validateSetting(final String json) {
        final JsonObject object = parse(json, SETTING_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(TREASURY_SETTING_RESOURCE_NAME);
        final String currencyCode = trimmed(object, CURRENCY_CODE_PARAM);
        final Long inTransitGl = this.fromApiJsonHelper.extractLongNamed(CASH_IN_TRANSIT_GL_ACCOUNT_ID_PARAM, object);
        final Long paymentTypeId = this.fromApiJsonHelper.extractLongNamed(FLOAT_PAYMENT_TYPE_ID_PARAM, object);
        final Boolean allowInsufficient = this.fromApiJsonHelper.extractBooleanNamed(ALLOW_INSUFFICIENT_BALANCE_PARAM, object);

        validator.reset().parameter(CURRENCY_CODE_PARAM).value(currencyCode).notBlank().matchesRegularExpression(CURRENCY_PATTERN,
                "The parameter `currencyCode` must be a three-letter currency code");
        validator.reset().parameter(CASH_IN_TRANSIT_GL_ACCOUNT_ID_PARAM).value(inTransitGl).ignoreIfNull().longGreaterThanZero();
        validator.reset().parameter(FLOAT_PAYMENT_TYPE_ID_PARAM).value(paymentTypeId).ignoreIfNull().longGreaterThanZero();
        if (object.has(ALLOW_INSUFFICIENT_BALANCE_PARAM)) {
            validator.reset().parameter(ALLOW_INSUFFICIENT_BALANCE_PARAM).value(allowInsufficient).notNull();
        }
        final Boolean referenceRule = booleanIfPresent(object, RECON_REFERENCE_RULE_PARAM, validator);
        final Boolean sameDateRule = booleanIfPresent(object, RECON_SAME_DATE_RULE_PARAM, validator);
        final Boolean windowRule = booleanIfPresent(object, RECON_WINDOW_RULE_PARAM, validator);
        final Integer windowDays = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(RECON_WINDOW_DAYS_PARAM, object);
        if (object.has(RECON_WINDOW_DAYS_PARAM)) {
            validator.reset().parameter(RECON_WINDOW_DAYS_PARAM).value(windowDays).notNull().integerZeroOrGreater().notGreaterThanMax(31);
        }
        if (object.keySet().size() == 1) {
            validator.reset().parameter(CURRENCY_CODE_PARAM).failWithCode("no.settings.passed");
        }
        throwIfAny(errors);
        return new SettingRequest(currencyCode.toUpperCase(), Set.copyOf(object.keySet()), inTransitGl, paymentTypeId, allowInsufficient,
                referenceRule, sameDateRule, windowRule, windowDays);
    }

    public AgentFloatRequest validateAgentFloatForCreate(final String json) {
        final JsonObject object = parse(json, AGENT_FLOAT_CREATE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(TREASURY_AGENT_FLOAT_RESOURCE_NAME);
        final AgentFloatRequest request = agentFloat(object);
        validator.reset().parameter(CLIENT_ID_PARAM).value(request.clientId()).notNull().longGreaterThanZero();
        validator.reset().parameter(SAVINGS_ACCOUNT_ID_PARAM).value(request.savingsAccountId()).notNull().longGreaterThanZero();
        validator.reset().parameter(TREASURY_ACCOUNT_ID_PARAM).value(request.treasuryAccountId()).notNull().longGreaterThanZero();
        validateFloatRange(validator, request.minFloat(), request.maxFloat());
        throwIfAny(errors);
        return request;
    }

    public AgentFloatRequest validateAgentFloatForUpdate(final String json) {
        final JsonObject object = parse(json, AGENT_FLOAT_UPDATE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(TREASURY_AGENT_FLOAT_RESOURCE_NAME);
        final AgentFloatRequest request = agentFloat(object);
        if (object.keySet().isEmpty()) {
            validator.reset().anyOfNotNull();
        }
        if (request.has(TREASURY_ACCOUNT_ID_PARAM)) {
            validator.reset().parameter(TREASURY_ACCOUNT_ID_PARAM).value(request.treasuryAccountId()).notNull().longGreaterThanZero();
        }
        validateFloatRange(validator, request.minFloat(), request.maxFloat());
        throwIfAny(errors);
        return request;
    }

    /** The minimum may not be above the maximum; also used on the merged values of an update. */
    public void validateFloatRange(final BigDecimal minFloat, final BigDecimal maxFloat) {
        final List<ApiParameterError> errors = new ArrayList<>();
        validateFloatRange(new DataValidatorBuilder(errors).resource(TREASURY_AGENT_FLOAT_RESOURCE_NAME), minFloat, maxFloat);
        throwIfAny(errors);
    }

    public FloatRequestRequest validateFloatRequestForCreate(final String json) {
        final JsonObject object = parse(json, FLOAT_REQUEST_CREATE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(TREASURY_FLOAT_REQUEST_RESOURCE_NAME);
        final String type = trimmed(object, REQUEST_TYPE_PARAM);
        final BigDecimal amount = amount(object, AMOUNT_PARAM);
        final String note = trimmed(object, NOTE_PARAM);
        validator.reset().parameter(REQUEST_TYPE_PARAM).value(type == null ? null : type.toUpperCase()).notBlank()
                .isOneOfTheseStringValues(REQUEST_TYPES);
        validator.reset().parameter(AMOUNT_PARAM).value(amount).notNull().positiveAmount();
        validator.reset().parameter(NOTE_PARAM).value(note).ignoreIfNull().notExceedingLengthOf(500);
        throwIfAny(errors);
        return new FloatRequestRequest(TreasuryFloatRequestType.valueOf(type.toUpperCase()), amount, note);
    }

    private AgentFloatRequest agentFloat(final JsonObject object) {
        return new AgentFloatRequest(Set.copyOf(object.keySet()), this.fromApiJsonHelper.extractLongNamed(CLIENT_ID_PARAM, object),
                this.fromApiJsonHelper.extractLongNamed(SAVINGS_ACCOUNT_ID_PARAM, object),
                this.fromApiJsonHelper.extractLongNamed(TREASURY_ACCOUNT_ID_PARAM, object), amount(object, MIN_FLOAT_PARAM),
                amount(object, MAX_FLOAT_PARAM));
    }

    private static void validateFloatRange(final DataValidatorBuilder validator, final BigDecimal min, final BigDecimal max) {
        validator.reset().parameter(MIN_FLOAT_PARAM).value(min).ignoreIfNull().zeroOrPositiveAmount();
        validator.reset().parameter(MAX_FLOAT_PARAM).value(max).ignoreIfNull().zeroOrPositiveAmount();
        if (min != null && max != null && min.compareTo(max) > 0) {
            validator.reset().parameter(MIN_FLOAT_PARAM).value(min).failWithCode("greater.than." + MAX_FLOAT_PARAM, max);
        }
    }

    private JsonObject parse(final String json, final Set<String> supportedParameters) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        if (element == null || !element.isJsonObject()) {
            throw new InvalidJsonException();
        }
        final JsonObject object = element.getAsJsonObject();
        this.fromApiJsonHelper.checkForUnsupportedParameters(object, supportedParameters);
        return object;
    }

    private static BigDecimal amount(final JsonObject object, final String parameterName) {
        final JsonElement value = object.get(parameterName);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsBigDecimal();
        } catch (NumberFormatException | UnsupportedOperationException e) {
            final List<ApiParameterError> errors = new ArrayList<>();
            new DataValidatorBuilder(errors).parameter(parameterName).value(value.toString()).failWithCode("not.a.number");
            throw new PlatformApiDataValidationException(errors, e);
        }
    }

    private static String trimmed(final JsonObject object, final String parameterName) {
        final JsonElement value = object.get(parameterName);
        return value == null || value.isJsonNull() ? null : StringUtils.trimToNull(value.getAsString());
    }

    private static LocalDate date(final JsonObject object, final String parameterName, final DataValidatorBuilder validator) {
        final String value = trimmed(object, parameterName);
        if (value == null) {
            return null;
        }
        final String format = trimmed(object, DATE_FORMAT_PARAM);
        final String locale = trimmed(object, LOCALE_PARAM);
        try {
            final DateTimeFormatter formatter = format == null ? DateTimeFormatter.ISO_LOCAL_DATE
                    : DateTimeFormatter.ofPattern(format, locale == null ? Locale.ENGLISH : Locale.forLanguageTag(locale));
            return LocalDate.parse(value, formatter);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            validator.reset().parameter(parameterName).value(value).failWithCode("invalid.date.format", format);
            return null;
        }
    }

    private Boolean booleanIfPresent(final JsonObject object, final String parameterName, final DataValidatorBuilder validator) {
        final Boolean value = this.fromApiJsonHelper.extractBooleanNamed(parameterName, object);
        if (object.has(parameterName)) {
            validator.reset().parameter(parameterName).value(value).notNull();
        }
        return value;
    }

    private static void throwIfAny(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }
}
