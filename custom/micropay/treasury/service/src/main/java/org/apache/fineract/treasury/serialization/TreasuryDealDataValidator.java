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

import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.ACTIVE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.AMORTISATION_METHOD_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.AMORTISING_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.AMOUNT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.BREAK_COST_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.CAPITALISE_INTEREST_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.CASHFLOW_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.CLASSIFICATION_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.CLEAN_PRICE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.CONFIRMATION_REFERENCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.COUNTERPARTY_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.COUNTERPARTY_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.COUNTERPARTY_TYPE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DATE_FORMAT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DAY_COUNT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DEAL_ACTION_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DEAL_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DEAL_TYPE_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DEAL_TYPE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DEFAULT_DAY_COUNT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DEFAULT_FREQUENCY_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DISCOUNT_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.EXPOSURE_LIMIT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.EXTERNAL_REFERENCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.FEES_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.FEE_EXPENSE_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.FEE_INCOME_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.FEE_METHOD_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.FREQUENCY_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.INSTRUMENT_KIND_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.INTEREST_EXPENSE_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.INTEREST_INCOME_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.INTEREST_METHOD_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.INTEREST_PAYABLE_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.INTEREST_RECEIVABLE_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.ISIN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.ISSUE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.LIMIT_CURRENCY_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.MATURITY_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.NOTES_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.NOTE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.PREMIUM_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.PRINCIPAL_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.PROCEEDS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.RATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.RATING_CODE_VALUE_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.RATING_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.REASON_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.ROLLOVER_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.ROLLOVER_PRE_APPROVED_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.SETTLEMENT_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.SETTLE_CASHFLOW_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.SHORT_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.TERMINATE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.TRADE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.TRANSACTION_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.TREASURY_COUNTERPARTY_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.TREASURY_DEAL_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.TREASURY_DEAL_TYPE_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.VALUE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.WARNING_PERCENT_PARAM;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.deal.AmortisationMethod;
import org.apache.fineract.treasury.deal.DayCount;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.FeeMethod;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.deal.InterestMethod;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyType;
import org.springframework.stereotype.Component;

/**
 * Field-level validation of counterparties, deal types and deals. Dates are ISO ({@code 2026-10-16}) unless a
 * {@code dateFormat} is sent; rates, yields and prices are percentages (12.5 is 12.5%).
 */
@Component
@RequiredArgsConstructor
public class TreasuryDealDataValidator {

    private static final String CODE_PATTERN = "[A-Za-z0-9_\\-]{1,50}";
    private static final String CURRENCY_PATTERN = "[A-Za-z]{3}";
    private static final String ISIN_PATTERN = "[A-Za-z]{2}[A-Za-z0-9]{9}[0-9]";
    private static final Set<Integer> FREQUENCIES = Set.of(0, 1, 2, 4, 12);

    /** The GL account parameters of a deal type. */
    public static final List<String> GL_PARAMS = List.of(PRINCIPAL_GL_PARAM, INTEREST_RECEIVABLE_GL_PARAM, INTEREST_PAYABLE_GL_PARAM,
            INTEREST_INCOME_GL_PARAM, INTEREST_EXPENSE_GL_PARAM, PREMIUM_GL_PARAM, DISCOUNT_GL_PARAM, FEE_INCOME_GL_PARAM,
            FEE_EXPENSE_GL_PARAM);

    private final FromJsonHelper fromApiJsonHelper;

    /** Values sent; on update only the fields present are changed. */
    public record Request(Set<String> parametersPassed, Map<String, Object> values) {

        public boolean has(final String parameterName) {
            return this.parametersPassed.contains(parameterName);
        }

        @SuppressWarnings("unchecked")
        public <T> T get(final String parameterName) {
            return (T) this.values.get(parameterName);
        }
    }

    public record ActionRequest(String confirmationReference, String reason, String note) {
    }

    // ---------- counterparties ----------

    public Request validateCounterparty(final String json, final boolean create) {
        final JsonObject object = parse(json, COUNTERPARTY_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_COUNTERPARTY_RESOURCE_NAME);
        final Map<String, Object> values = new HashMap<>();
        final String name = text(object, NAME_PARAM);
        final String shortCode = text(object, SHORT_CODE_PARAM);
        final String type = text(object, COUNTERPARTY_TYPE_PARAM);
        final BigDecimal limit = amount(object, EXPOSURE_LIMIT_PARAM, v);
        final String limitCurrency = text(object, LIMIT_CURRENCY_CODE_PARAM);
        final BigDecimal warning = amount(object, WARNING_PERCENT_PARAM, v);
        if (create || object.has(NAME_PARAM)) {
            v.reset().parameter(NAME_PARAM).value(name).notBlank().notExceedingLengthOf(150);
        }
        if (create) {
            v.reset().parameter(SHORT_CODE_PARAM).value(shortCode).notBlank().matchesRegularExpression("[A-Za-z0-9_\\-]{1,30}",
                    "The parameter `shortCode` may hold letters, digits, - and _ only, 30 at most");
        } else if (object.has(SHORT_CODE_PARAM)) {
            v.reset().parameter(SHORT_CODE_PARAM).failWithCode("cannot.be.changed");
        }
        if (create || object.has(COUNTERPARTY_TYPE_PARAM)) {
            v.reset().parameter(COUNTERPARTY_TYPE_PARAM).value(upper(type)).notBlank()
                    .isOneOfTheseStringValues(names(TreasuryCounterpartyType.values()));
        }
        v.reset().parameter(RATING_PARAM).value(text(object, RATING_PARAM)).ignoreIfNull().notExceedingLengthOf(50);
        v.reset().parameter(RATING_CODE_VALUE_ID_PARAM).value(this.fromApiJsonHelper.extractLongNamed(RATING_CODE_VALUE_ID_PARAM, object))
                .ignoreIfNull().longGreaterThanZero();
        v.reset().parameter(EXPOSURE_LIMIT_PARAM).value(limit).ignoreIfNull().zeroOrPositiveAmount();
        v.reset().parameter(LIMIT_CURRENCY_CODE_PARAM).value(limitCurrency).ignoreIfNull().matchesRegularExpression(CURRENCY_PATTERN,
                "The parameter `limitCurrencyCode` must be a three-letter currency code");
        if (create && limit != null && limitCurrency == null) {
            v.reset().parameter(LIMIT_CURRENCY_CODE_PARAM).failWithCode("required.with.exposureLimit");
        }
        if (warning != null && (warning.signum() <= 0 || warning.compareTo(BigDecimal.valueOf(100)) > 0)) {
            v.reset().parameter(WARNING_PERCENT_PARAM).value(warning).failWithCode("must.be.between.0.and.100");
        }
        if (!create && object.keySet().isEmpty()) {
            v.reset().anyOfNotNull();
        }
        throwIfAny(errors);
        values.put(NAME_PARAM, name);
        values.put(SHORT_CODE_PARAM, upper(shortCode));
        values.put(COUNTERPARTY_TYPE_PARAM, type == null ? null : TreasuryCounterpartyType.valueOf(upper(type)));
        values.put(RATING_PARAM, text(object, RATING_PARAM));
        values.put(RATING_CODE_VALUE_ID_PARAM, this.fromApiJsonHelper.extractLongNamed(RATING_CODE_VALUE_ID_PARAM, object));
        values.put(EXPOSURE_LIMIT_PARAM, limit);
        values.put(LIMIT_CURRENCY_CODE_PARAM, upper(limitCurrency));
        values.put(WARNING_PERCENT_PARAM, warning);
        return new Request(Set.copyOf(object.keySet()), values);
    }

    // ---------- deal types ----------

    public Request validateDealType(final String json, final boolean create) {
        final JsonObject object = parse(json, DEAL_TYPE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_DEAL_TYPE_RESOURCE_NAME);
        final Map<String, Object> values = new HashMap<>();
        final String code = text(object, CODE_PARAM);
        final String kind = text(object, INSTRUMENT_KIND_PARAM);
        final String method = text(object, INTEREST_METHOD_PARAM);
        final String dayCount = text(object, DEFAULT_DAY_COUNT_PARAM);
        final Integer frequency = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(DEFAULT_FREQUENCY_PARAM, object);
        final String feeMethod = text(object, FEE_METHOD_PARAM);
        final String amortisationMethod = text(object, AMORTISATION_METHOD_PARAM);
        v.reset().parameter(FEE_METHOD_PARAM).value(upper(feeMethod)).ignoreIfNull().isOneOfTheseStringValues(names(FeeMethod.values()));
        v.reset().parameter(AMORTISATION_METHOD_PARAM).value(upper(amortisationMethod)).ignoreIfNull()
                .isOneOfTheseStringValues(names(AmortisationMethod.values()));
        if (create) {
            v.reset().parameter(CODE_PARAM).value(code).notBlank().matchesRegularExpression(CODE_PATTERN,
                    "The parameter `code` may hold letters, digits, - and _ only, 50 at most");
            v.reset().parameter(INSTRUMENT_KIND_PARAM).value(upper(kind)).notBlank()
                    .isOneOfTheseStringValues(names(InstrumentKind.values()));
        } else {
            for (final String fixed : List.of(CODE_PARAM, INSTRUMENT_KIND_PARAM)) {
                if (object.has(fixed)) {
                    v.reset().parameter(fixed).failWithCode("cannot.be.changed");
                }
            }
            if (object.keySet().isEmpty()) {
                v.reset().anyOfNotNull();
            }
        }
        if (create || object.has(NAME_PARAM)) {
            v.reset().parameter(NAME_PARAM).value(text(object, NAME_PARAM)).notBlank().notExceedingLengthOf(150);
        }
        v.reset().parameter(INTEREST_METHOD_PARAM).value(upper(method)).ignoreIfNull()
                .isOneOfTheseStringValues(names(InterestMethod.values()));
        v.reset().parameter(DEFAULT_DAY_COUNT_PARAM).value(upper(dayCount)).ignoreIfNull()
                .isOneOfTheseStringValues(names(DayCount.values()));
        if (frequency != null && !FREQUENCIES.contains(frequency)) {
            v.reset().parameter(DEFAULT_FREQUENCY_PARAM).value(frequency).failWithCode("must.be.0.1.2.4.or.12");
        }
        for (final String gl : GL_PARAMS) {
            v.reset().parameter(gl).value(this.fromApiJsonHelper.extractLongNamed(gl, object)).ignoreIfNull().longGreaterThanZero();
        }
        throwIfAny(errors);
        values.put(CODE_PARAM, upper(code));
        values.put(NAME_PARAM, text(object, NAME_PARAM));
        values.put(INSTRUMENT_KIND_PARAM, kind == null ? null : InstrumentKind.valueOf(upper(kind)));
        values.put(INTEREST_METHOD_PARAM, method == null ? null : InterestMethod.valueOf(upper(method)));
        values.put(DEFAULT_DAY_COUNT_PARAM, dayCount == null ? null : DayCount.valueOf(upper(dayCount)));
        values.put(DEFAULT_FREQUENCY_PARAM, frequency);
        for (final String gl : GL_PARAMS) {
            values.put(gl, this.fromApiJsonHelper.extractLongNamed(gl, object));
        }
        values.put(ACTIVE_PARAM, this.fromApiJsonHelper.extractBooleanNamed(ACTIVE_PARAM, object));
        values.put(FEE_METHOD_PARAM, feeMethod == null ? null : FeeMethod.valueOf(upper(feeMethod)));
        values.put(AMORTISATION_METHOD_PARAM, amortisationMethod == null ? null : AmortisationMethod.valueOf(upper(amortisationMethod)));
        return new Request(Set.copyOf(object.keySet()), values);
    }

    // ---------- deals ----------

    public Request validateDeal(final String json, final boolean create) {
        final JsonObject object = parse(json, DEAL_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_DEAL_RESOURCE_NAME);
        final Map<String, Object> values = new HashMap<>();
        for (final String id : List.of(DEAL_TYPE_ID_PARAM, COUNTERPARTY_ID_PARAM, SETTLEMENT_ACCOUNT_ID_PARAM)) {
            final Long value = this.fromApiJsonHelper.extractLongNamed(id, object);
            if (create) {
                v.reset().parameter(id).value(value).notNull().longGreaterThanZero();
            } else if (object.has(id)) {
                v.reset().parameter(id).value(value).notNull().longGreaterThanZero();
            }
            values.put(id, value);
        }
        for (final String date : List.of(TRADE_DATE_PARAM, VALUE_DATE_PARAM, MATURITY_DATE_PARAM, ISSUE_DATE_PARAM)) {
            final LocalDate value = date(object, date, v);
            if ((create || object.has(date)) && !ISSUE_DATE_PARAM.equals(date)) {
                v.reset().parameter(date).value(value).notNull();
            }
            values.put(date, value);
        }
        for (final String number : List.of(AMOUNT_PARAM, RATE_PARAM, CLEAN_PRICE_PARAM, FEES_PARAM)) {
            values.put(number, amount(object, number, v));
        }
        if (create || object.has(AMOUNT_PARAM)) {
            v.reset().parameter(AMOUNT_PARAM).value((BigDecimal) values.get(AMOUNT_PARAM)).notNull().positiveAmount();
        }
        if (create || object.has(RATE_PARAM)) {
            v.reset().parameter(RATE_PARAM).value((BigDecimal) values.get(RATE_PARAM)).notNull().zeroOrPositiveAmount();
        }
        v.reset().parameter(CLEAN_PRICE_PARAM).value((BigDecimal) values.get(CLEAN_PRICE_PARAM)).ignoreIfNull().positiveAmount();
        v.reset().parameter(FEES_PARAM).value((BigDecimal) values.get(FEES_PARAM)).ignoreIfNull().zeroOrPositiveAmount();
        final String dayCount = text(object, DAY_COUNT_PARAM);
        v.reset().parameter(DAY_COUNT_PARAM).value(upper(dayCount)).ignoreIfNull().isOneOfTheseStringValues(names(DayCount.values()));
        values.put(DAY_COUNT_PARAM, dayCount == null ? null : DayCount.valueOf(upper(dayCount)));
        final Integer frequency = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(FREQUENCY_PARAM, object);
        if (frequency != null && !FREQUENCIES.contains(frequency)) {
            v.reset().parameter(FREQUENCY_PARAM).value(frequency).failWithCode("must.be.0.1.2.4.or.12");
        }
        values.put(FREQUENCY_PARAM, frequency);
        values.put(AMORTISING_PARAM, this.fromApiJsonHelper.extractBooleanNamed(AMORTISING_PARAM, object));
        values.put(ROLLOVER_PRE_APPROVED_PARAM, this.fromApiJsonHelper.extractBooleanNamed(ROLLOVER_PRE_APPROVED_PARAM, object));
        final String classification = text(object, CLASSIFICATION_PARAM);
        v.reset().parameter(CLASSIFICATION_PARAM).value(upper(classification)).ignoreIfNull()
                .isOneOfTheseStringValues(names(DealClassification.values()));
        values.put(CLASSIFICATION_PARAM, classification == null ? null : DealClassification.valueOf(upper(classification)));
        final String isin = text(object, ISIN_PARAM);
        v.reset().parameter(ISIN_PARAM).value(isin).ignoreIfNull().matchesRegularExpression(ISIN_PATTERN,
                "The parameter `isin` must be a 12-character ISIN");
        values.put(ISIN_PARAM, upper(isin));
        values.put(EXTERNAL_REFERENCE_PARAM, text(object, EXTERNAL_REFERENCE_PARAM));
        v.reset().parameter(EXTERNAL_REFERENCE_PARAM).value(text(object, EXTERNAL_REFERENCE_PARAM)).ignoreIfNull()
                .notExceedingLengthOf(100);
        values.put(NOTES_PARAM, text(object, NOTES_PARAM));
        v.reset().parameter(NOTES_PARAM).value(text(object, NOTES_PARAM)).ignoreIfNull().notExceedingLengthOf(1000);
        if (!create && object.keySet().isEmpty()) {
            v.reset().anyOfNotNull();
        }
        throwIfAny(errors);
        return new Request(Set.copyOf(object.keySet()), values);
    }

    /** The deal's own terms once merged with its type's defaults. */
    public record Terms(InstrumentKind kind, LocalDate tradeDate, LocalDate valueDate, LocalDate maturityDate, LocalDate issueDate,
            BigDecimal amount, BigDecimal rate, BigDecimal cleanPrice, int frequency, boolean amortising) {
    }

    /**
     * Rules that depend on the instrument: dates in order; bills take no price or frequency; bonds need a clean price
     * and a coupon frequency; only borrowings and drawdowns amortise, and then by periods.
     */
    public static void validateTerms(final Terms terms) {
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_DEAL_RESOURCE_NAME);
        if (terms.valueDate().isBefore(terms.tradeDate())) {
            v.reset().parameter(VALUE_DATE_PARAM).value(terms.valueDate()).failWithCode("before.tradeDate");
        }
        if (!terms.maturityDate().isAfter(terms.valueDate())) {
            v.reset().parameter(MATURITY_DATE_PARAM).value(terms.maturityDate()).failWithCode("not.after.valueDate");
        }
        final InstrumentKind kind = terms.kind();
        if (kind != InstrumentKind.GOVERNMENT_BOND) {
            if (terms.cleanPrice() != null) {
                v.reset().parameter(CLEAN_PRICE_PARAM).failWithCode("only.for.bonds");
            }
            if (terms.issueDate() != null) {
                v.reset().parameter(ISSUE_DATE_PARAM).failWithCode("only.for.bonds");
            }
        }
        if (kind == InstrumentKind.TREASURY_BILL && terms.frequency() != 0) {
            v.reset().parameter(FREQUENCY_PARAM).value(terms.frequency()).failWithCode("not.for.treasury.bills");
        }
        if (kind == InstrumentKind.GOVERNMENT_BOND) {
            if (terms.cleanPrice() == null) {
                v.reset().parameter(CLEAN_PRICE_PARAM).failWithCode("required.for.bonds");
            }
            if (terms.frequency() == 0) {
                v.reset().parameter(FREQUENCY_PARAM).value(terms.frequency()).failWithCode("coupon.frequency.required");
            }
            if (terms.issueDate() != null && terms.issueDate().isAfter(terms.valueDate())) {
                v.reset().parameter(ISSUE_DATE_PARAM).value(terms.issueDate()).failWithCode("after.valueDate");
            }
        }
        if (terms.amortising()) {
            if (!kind.mayAmortise()) {
                v.reset().parameter(AMORTISING_PARAM).failWithCode("only.for.borrowings");
            } else if (terms.frequency() == 0) {
                v.reset().parameter(AMORTISING_PARAM).failWithCode("needs.a.frequency");
            }
        }
        throwIfAny(errors);
    }

    public record SettleCashflowRequest(Long cashflowId, BigDecimal amount, LocalDate transactionDate, String note) {
    }

    public record TerminateRequest(LocalDate transactionDate, BigDecimal proceeds, BigDecimal breakCost, String reason, String note) {
    }

    public record RolloverRequest(BigDecimal rate, LocalDate maturityDate, boolean capitaliseInterest, String externalReference,
            boolean rolloverPreApproved, String note) {
    }

    /** {@code amount} defaults to what is left of the cash flow, {@code transactionDate} to the business date. */
    public SettleCashflowRequest validateSettleCashflow(final String json) {
        final JsonObject object = parse(json, SETTLE_CASHFLOW_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_DEAL_RESOURCE_NAME);
        final Long cashflowId = this.fromApiJsonHelper.extractLongNamed(CASHFLOW_ID_PARAM, object);
        final BigDecimal amount = amount(object, AMOUNT_PARAM, v);
        final LocalDate date = date(object, TRANSACTION_DATE_PARAM, v);
        v.reset().parameter(CASHFLOW_ID_PARAM).value(cashflowId).notNull().longGreaterThanZero();
        v.reset().parameter(AMOUNT_PARAM).value(amount).ignoreIfNull().positiveAmount();
        v.reset().parameter(NOTE_PARAM).value(text(object, NOTE_PARAM)).ignoreIfNull().notExceedingLengthOf(1000);
        throwIfAny(errors);
        return new SettleCashflowRequest(cashflowId, amount, date, text(object, NOTE_PARAM));
    }

    public TerminateRequest validateTerminate(final String json) {
        final JsonObject object = parse(json, TERMINATE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_DEAL_RESOURCE_NAME);
        final LocalDate date = date(object, TRANSACTION_DATE_PARAM, v);
        final BigDecimal proceeds = amount(object, PROCEEDS_PARAM, v);
        final BigDecimal breakCost = amount(object, BREAK_COST_PARAM, v);
        v.reset().parameter(PROCEEDS_PARAM).value(proceeds).notNull().zeroOrPositiveAmount();
        v.reset().parameter(BREAK_COST_PARAM).value(breakCost).ignoreIfNull().zeroOrPositiveAmount();
        v.reset().parameter(REASON_PARAM).value(text(object, REASON_PARAM)).notBlank().notExceedingLengthOf(500);
        v.reset().parameter(NOTE_PARAM).value(text(object, NOTE_PARAM)).ignoreIfNull().notExceedingLengthOf(1000);
        throwIfAny(errors);
        return new TerminateRequest(date, proceeds, breakCost, text(object, REASON_PARAM), text(object, NOTE_PARAM));
    }

    public RolloverRequest validateRollover(final String json) {
        final JsonObject object = parse(json, ROLLOVER_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_DEAL_RESOURCE_NAME);
        final BigDecimal rate = amount(object, RATE_PARAM, v);
        final LocalDate maturity = date(object, MATURITY_DATE_PARAM, v);
        v.reset().parameter(RATE_PARAM).value(rate).notNull().zeroOrPositiveAmount();
        v.reset().parameter(MATURITY_DATE_PARAM).value(maturity).notNull();
        v.reset().parameter(EXTERNAL_REFERENCE_PARAM).value(text(object, EXTERNAL_REFERENCE_PARAM)).ignoreIfNull()
                .notExceedingLengthOf(100);
        v.reset().parameter(NOTE_PARAM).value(text(object, NOTE_PARAM)).ignoreIfNull().notExceedingLengthOf(1000);
        throwIfAny(errors);
        return new RolloverRequest(rate, maturity,
                Boolean.TRUE.equals(this.fromApiJsonHelper.extractBooleanNamed(CAPITALISE_INTEREST_PARAM, object)),
                text(object, EXTERNAL_REFERENCE_PARAM),
                Boolean.TRUE.equals(this.fromApiJsonHelper.extractBooleanNamed(ROLLOVER_PRE_APPROVED_PARAM, object)),
                text(object, NOTE_PARAM));
    }

    public ActionRequest validateAction(final String json) {
        if (StringUtils.isBlank(json)) {
            return new ActionRequest(null, null, null);
        }
        final JsonObject object = parse(json, DEAL_ACTION_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_DEAL_RESOURCE_NAME);
        final String confirmation = text(object, CONFIRMATION_REFERENCE_PARAM);
        final String reason = text(object, REASON_PARAM);
        final String note = text(object, NOTE_PARAM);
        v.reset().parameter(CONFIRMATION_REFERENCE_PARAM).value(confirmation).ignoreIfNull().notExceedingLengthOf(100);
        v.reset().parameter(REASON_PARAM).value(reason).ignoreIfNull().notExceedingLengthOf(500);
        v.reset().parameter(NOTE_PARAM).value(note).ignoreIfNull().notExceedingLengthOf(1000);
        throwIfAny(errors);
        return new ActionRequest(confirmation, reason, note);
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

    private static BigDecimal amount(final JsonObject object, final String parameterName, final DataValidatorBuilder v) {
        final JsonElement value = object.get(parameterName);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsBigDecimal();
        } catch (NumberFormatException | UnsupportedOperationException e) {
            v.reset().parameter(parameterName).value(value.toString()).failWithCode("not.a.number");
            return null;
        }
    }

    private static String text(final JsonObject object, final String parameterName) {
        final JsonElement value = object.get(parameterName);
        return value == null || value.isJsonNull() ? null : StringUtils.trimToNull(value.getAsString());
    }

    private static String upper(final String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static LocalDate date(final JsonObject object, final String parameterName, final DataValidatorBuilder v) {
        final String value = text(object, parameterName);
        if (value == null) {
            return null;
        }
        final String format = text(object, DATE_FORMAT_PARAM);
        try {
            return LocalDate.parse(value,
                    format == null ? DateTimeFormatter.ISO_LOCAL_DATE : DateTimeFormatter.ofPattern(format, Locale.ENGLISH));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            v.reset().parameter(parameterName).value(value).failWithCode("invalid.date.format", format);
            return null;
        }
    }

    private static <E extends Enum<E>> List<String> names(final E[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static void throwIfAny(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }
}
