/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.ACCOUNT_TYPE;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.AMOUNT;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.CLEARING_GL_ACCOUNT_ID;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.CODE_PREFIX;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.CURRENCIES;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.CURRENCY_CODE;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.DESCRIPTION;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.EFFECTIVE_DATE;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.EXPIRY_DATE;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.FREQUENCY_NUMBER;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.FREQUENCY_UNIT;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.GL_ACCOUNT_ID;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.IMPORT_ROW_CAP;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.NARRATIVE;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.NEXT_POSTING_DATE;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.RECURRING;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.REFERENCE;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.SAVINGS_ACCOUNT_ID;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.SIDE;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.SUSPENSE_GL_ACCOUNT_ID;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.VALUE_DATE;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.transactionbatch.domain.TransactionBatchFrequency;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchValidator {

    private final FromJsonHelper jsonHelper;

    public Header parseHeader(final String json) {
        final JsonElement element = parse(json);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource("transactionbatch");
        final String description = this.jsonHelper.extractStringNamed(DESCRIPTION, element);
        validator.reset().parameter(DESCRIPTION).value(description).notBlank().notExceedingLengthOf(500);
        final Boolean recurring = this.jsonHelper.extractBooleanNamed(RECURRING, element);
        validator.reset().parameter(RECURRING).value(recurring).notNull();
        LocalDate effectiveDate = null;
        if (this.jsonHelper.parameterExists(EFFECTIVE_DATE, element) && !element.getAsJsonObject().get(EFFECTIVE_DATE).isJsonNull()) {
            effectiveDate = this.jsonHelper.extractLocalDateNamed(EFFECTIVE_DATE, element);
        }
        Integer frequencyNumber = null;
        String frequencyUnit = null;
        LocalDate nextPostingDate = null;
        LocalDate expiryDate = null;
        if (Boolean.TRUE.equals(recurring)) {
            frequencyNumber = this.jsonHelper.extractIntegerSansLocaleNamed(FREQUENCY_NUMBER, element);
            validator.reset().parameter(FREQUENCY_NUMBER).value(frequencyNumber).notNull().integerGreaterThanZero();
            frequencyUnit = this.jsonHelper.extractStringNamed(FREQUENCY_UNIT, element);
            validator.reset().parameter(FREQUENCY_UNIT).value(frequencyUnit).notBlank();
            nextPostingDate = this.jsonHelper.extractLocalDateNamed(NEXT_POSTING_DATE, element);
            validator.reset().parameter(NEXT_POSTING_DATE).value(nextPostingDate).notNull();
            expiryDate = this.jsonHelper.extractLocalDateNamed(EXPIRY_DATE, element);
            validator.reset().parameter(EXPIRY_DATE).value(expiryDate).notNull();
            if (frequencyUnit != null) {
                try {
                    TransactionBatchFrequency.fromCode(frequencyUnit);
                } catch (final IllegalArgumentException ex) {
                    validator.reset().parameter(FREQUENCY_UNIT).value(frequencyUnit).failWithCode("invalid");
                }
            }
            if (nextPostingDate != null && expiryDate != null && expiryDate.isBefore(nextPostingDate)) {
                validator.reset().parameter(EXPIRY_DATE).value(expiryDate).failWithCode("before.next.posting.date");
            }
        }
        throwIf(errors);
        return new Header(description.trim(), Boolean.TRUE.equals(recurring), effectiveDate, frequencyNumber,
                frequencyUnit == null ? null : TransactionBatchFrequency.fromCode(frequencyUnit), nextPostingDate, expiryDate);
    }

    public ItemFields parseItem(final String json) {
        final JsonElement element = parse(json);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource("transactionbatchitem");
        final String accountType = this.jsonHelper.extractStringNamed(ACCOUNT_TYPE, element);
        validator.reset().parameter(ACCOUNT_TYPE).value(accountType).notBlank();
        final Long glAccountId = this.jsonHelper.extractLongNamed(GL_ACCOUNT_ID, element);
        final Long savingsAccountId = this.jsonHelper.extractLongNamed(SAVINGS_ACCOUNT_ID, element);
        final String side = this.jsonHelper.extractStringNamed(SIDE, element);
        validator.reset().parameter(SIDE).value(side).notBlank();
        final String currency = this.jsonHelper.extractStringNamed(CURRENCY_CODE, element);
        validator.reset().parameter(CURRENCY_CODE).value(currency).notBlank().notExceedingLengthOf(3);
        final BigDecimal amount = this.jsonHelper.extractBigDecimalWithLocaleNamed(AMOUNT, element);
        validator.reset().parameter(AMOUNT).value(amount).notNull().positiveAmount();
        final String narrative = this.jsonHelper.parameterExists(NARRATIVE, element) ? this.jsonHelper.extractStringNamed(NARRATIVE, element)
                : null;
        if (narrative != null) {
            validator.reset().parameter(NARRATIVE).value(narrative).notExceedingLengthOf(500);
        }
        final String reference = this.jsonHelper.parameterExists(REFERENCE, element) ? this.jsonHelper.extractStringNamed(REFERENCE, element)
                : null;
        if (StringUtils.isNotBlank(reference)) {
            validator.reset().parameter(REFERENCE).value(reference).notExceedingLengthOf(100);
        }
        final LocalDate valueDate = this.jsonHelper.parameterExists(VALUE_DATE, element)
                && !element.getAsJsonObject().get(VALUE_DATE).isJsonNull() ? this.jsonHelper.extractLocalDateNamed(VALUE_DATE, element) : null;
        throwIf(errors);
        return new ItemFields(accountType, glAccountId, savingsAccountId, side, currency, amount,
                StringUtils.isBlank(narrative) ? null : narrative.trim(), StringUtils.isBlank(reference) ? null : reference.trim(), valueDate);
    }

    public Settings parseSettings(final String json) {
        final JsonElement element = parse(json);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource("transactionbatchsetting");
        final String prefix = this.jsonHelper.extractStringNamed(CODE_PREFIX, element);
        validator.reset().parameter(CODE_PREFIX).value(prefix).notBlank().notExceedingLengthOf(10);
        final Integer cap = this.jsonHelper.extractIntegerSansLocaleNamed(IMPORT_ROW_CAP, element);
        validator.reset().parameter(IMPORT_ROW_CAP).value(cap).notNull().integerGreaterThanZero();
        if (!this.jsonHelper.parameterExists(CURRENCIES, element) || !element.getAsJsonObject().get(CURRENCIES).isJsonArray()) {
            validator.reset().parameter(CURRENCIES).value(null).failWithCode("required");
            throwIf(errors);
        }
        final JsonArray currencies = element.getAsJsonObject().get(CURRENCIES).getAsJsonArray();
        final List<CurrencySetting> rows = new ArrayList<>();
        for (final JsonElement row : currencies) {
            final String currency = this.jsonHelper.extractStringNamed(CURRENCY_CODE, row);
            final Long clearing = this.jsonHelper.extractLongNamed(CLEARING_GL_ACCOUNT_ID, row);
            final Long suspense = this.jsonHelper.extractLongNamed(SUSPENSE_GL_ACCOUNT_ID, row);
            validator.reset().parameter(CURRENCY_CODE).value(currency).notBlank();
            validator.reset().parameter(CLEARING_GL_ACCOUNT_ID).value(clearing).notNull().longGreaterThanZero();
            validator.reset().parameter(SUSPENSE_GL_ACCOUNT_ID).value(suspense).notNull().longGreaterThanZero();
            if (clearing != null && clearing.equals(suspense)) {
                validator.reset().parameter(SUSPENSE_GL_ACCOUNT_ID).value(suspense).failWithCode("same.as.clearing");
            }
            if (currency != null && clearing != null && suspense != null && !clearing.equals(suspense)) {
                rows.add(new CurrencySetting(currency, clearing, suspense));
            }
        }
        throwIf(errors);
        return new Settings(prefix.trim(), cap, rows);
    }

    private JsonElement parse(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        return this.jsonHelper.parse(json);
    }

    private static void throwIf(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }

    public record Header(String description, boolean recurring, LocalDate effectiveDate, Integer frequencyNumber,
            TransactionBatchFrequency frequencyUnit, LocalDate nextPostingDate, LocalDate expiryDate) {}

    public record ItemFields(String accountType, Long glAccountId, Long savingsAccountId, String side, String currencyCode, BigDecimal amount,
            String narrative, String reference, LocalDate valueDate) {}

    public record CurrencySetting(String currencyCode, Long clearingGlAccountId, Long suspenseGlAccountId) {}

    public record Settings(String codePrefix, int importRowCap, List<CurrencySetting> currencies) {}
}
