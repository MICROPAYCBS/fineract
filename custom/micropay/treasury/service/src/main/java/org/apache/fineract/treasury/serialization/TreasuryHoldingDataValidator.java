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

import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.TREASURY_DEAL_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.AMOUNT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.CLEAN_PRICE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.CLEAR_OVERRIDES_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.DATE_FORMAT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.ECL_PARAMETER_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.ECL_STAGE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.EXPECTED_END_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.INSTRUMENT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.LGD_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.LINKED_DEAL_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.MANUAL_ECL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.MANUAL_ECL_REASON_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.MANUAL_LGD_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.MANUAL_PD_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.NOTE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.PD_12_MONTH_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.PD_LIFETIME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.PLEDGEE_COUNTERPARTY_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.PLEDGE_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.PLEDGE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.PRICES_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.PRICE_BATCH_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.PRICE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.PRICE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.PURPOSE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.RATING_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.REASON_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.RECALCULATE_ECL_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.RELEASE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.RELEASE_PLEDGE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.SOURCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.STAGE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.START_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.TREASURY_ECL_PARAMETER_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.TREASURY_PRICE_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.YIELD_PARAM;

import com.google.gson.JsonArray;
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
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.deal.PledgePurpose;
import org.apache.fineract.treasury.deal.PriceSource;
import org.springframework.stereotype.Component;

/**
 * Field-level validation of pledges, expected credit loss commands and parameters, and market prices. Dates are ISO
 * ({@code 2026-10-16}) unless a {@code dateFormat} is sent; PD, LGD, prices and yields are percentages.
 */
@Component
@RequiredArgsConstructor
public class TreasuryHoldingDataValidator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_PRICE = BigDecimal.valueOf(1000);
    private static final String INSTRUMENT_PATTERN = "^[A-Za-z0-9_./-]{1,100}$";

    private final FromJsonHelper fromApiJsonHelper;

    public record PledgeRequest(Long pledgeeCounterpartyId, PledgePurpose purpose, BigDecimal amount, Long linkedDealId,
            LocalDate startDate, LocalDate expectedEndDate, String reason) {
    }

    public record ReleasePledgeRequest(Long pledgeId, LocalDate releaseDate, String reason) {
    }

    /**
     * @param manualPd
     *            percent; replaces the parameter table's PD (null leaves the deal's current override)
     * @param clearOverrides
     *            drops the deal's manual PD, LGD and ECL before applying any sent with it
     */
    public record EclStageRequest(int stage, String reason, BigDecimal manualPd, BigDecimal manualLgd, BigDecimal manualEcl,
            String manualEclReason, boolean clearOverrides) {
    }

    public record PriceRequest(String instrumentId, LocalDate priceDate, BigDecimal cleanPrice, BigDecimal yield, PriceSource source) {
    }

    public record EclParameterRequest(String ratingCode, int stage, BigDecimal pd12Month, BigDecimal pdLifetime, BigDecimal lgd) {
    }

    // ---------- pledges ----------

    public PledgeRequest validatePledge(final String json) {
        final JsonObject object = parse(json, PLEDGE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_DEAL_RESOURCE_NAME);
        final Long pledgee = this.fromApiJsonHelper.extractLongNamed(PLEDGEE_COUNTERPARTY_ID_PARAM, object);
        final String purpose = upper(text(object, PURPOSE_PARAM));
        final BigDecimal amount = amount(object, AMOUNT_PARAM, v);
        final Long linked = this.fromApiJsonHelper.extractLongNamed(LINKED_DEAL_ID_PARAM, object);
        final LocalDate start = date(object, START_DATE_PARAM, v);
        final LocalDate end = date(object, EXPECTED_END_DATE_PARAM, v);
        v.reset().parameter(PLEDGEE_COUNTERPARTY_ID_PARAM).value(pledgee).notNull().longGreaterThanZero();
        v.reset().parameter(PURPOSE_PARAM).value(purpose).notBlank().isOneOfTheseStringValues(names(PledgePurpose.values()));
        v.reset().parameter(AMOUNT_PARAM).value(amount).notNull().positiveAmount();
        v.reset().parameter(LINKED_DEAL_ID_PARAM).value(linked).ignoreIfNull().longGreaterThanZero();
        if (start != null && end != null && end.isBefore(start)) {
            v.reset().parameter(EXPECTED_END_DATE_PARAM).value(end).failWithCode("before.start.date", start);
        }
        v.reset().parameter(REASON_PARAM).value(text(object, REASON_PARAM)).ignoreIfNull().notExceedingLengthOf(500);
        throwIfAny(errors);
        return new PledgeRequest(pledgee, PledgePurpose.valueOf(purpose), amount, linked, start, end, text(object, REASON_PARAM));
    }

    public ReleasePledgeRequest validateReleasePledge(final String json) {
        final JsonObject object = parse(json, RELEASE_PLEDGE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_DEAL_RESOURCE_NAME);
        final Long pledgeId = this.fromApiJsonHelper.extractLongNamed(PLEDGE_ID_PARAM, object);
        final LocalDate date = date(object, RELEASE_DATE_PARAM, v);
        v.reset().parameter(PLEDGE_ID_PARAM).value(pledgeId).notNull().longGreaterThanZero();
        v.reset().parameter(REASON_PARAM).value(text(object, REASON_PARAM)).notBlank().notExceedingLengthOf(500);
        throwIfAny(errors);
        return new ReleasePledgeRequest(pledgeId, date, text(object, REASON_PARAM));
    }

    // ---------- expected credit loss ----------

    public EclStageRequest validateEclStage(final String json) {
        final JsonObject object = parse(json, ECL_STAGE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_DEAL_RESOURCE_NAME);
        final Integer stage = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(STAGE_PARAM, object);
        final BigDecimal pd = amount(object, MANUAL_PD_PARAM, v);
        final BigDecimal lgd = amount(object, MANUAL_LGD_PARAM, v);
        final BigDecimal ecl = amount(object, MANUAL_ECL_PARAM, v);
        final Boolean clear = this.fromApiJsonHelper.extractBooleanNamed(CLEAR_OVERRIDES_PARAM, object);
        v.reset().parameter(STAGE_PARAM).value(stage).notNull().inMinMaxRange(1, 3);
        v.reset().parameter(REASON_PARAM).value(text(object, REASON_PARAM)).notBlank().notExceedingLengthOf(500);
        percent(v, MANUAL_PD_PARAM, pd);
        percent(v, MANUAL_LGD_PARAM, lgd);
        v.reset().parameter(MANUAL_ECL_PARAM).value(ecl).ignoreIfNull().zeroOrPositiveAmount();
        if (ecl != null) {
            v.reset().parameter(MANUAL_ECL_REASON_PARAM).value(text(object, MANUAL_ECL_REASON_PARAM)).notBlank().notExceedingLengthOf(500);
        }
        throwIfAny(errors);
        return new EclStageRequest(stage, text(object, REASON_PARAM), pd, lgd, ecl, text(object, MANUAL_ECL_REASON_PARAM),
                Boolean.TRUE.equals(clear));
    }

    /** @return the note, if any */
    public String validateRecalculateEcl(final String json) {
        final JsonObject object = parse(StringUtils.isBlank(json) ? "{}" : json, RECALCULATE_ECL_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_DEAL_RESOURCE_NAME);
        v.reset().parameter(NOTE_PARAM).value(text(object, NOTE_PARAM)).ignoreIfNull().notExceedingLengthOf(1000);
        throwIfAny(errors);
        return text(object, NOTE_PARAM);
    }

    public EclParameterRequest validateEclParameter(final String json) {
        final JsonObject object = parse(json, ECL_PARAMETER_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_ECL_PARAMETER_RESOURCE_NAME);
        final String rating = upper(text(object, RATING_CODE_PARAM));
        final Integer stage = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(STAGE_PARAM, object);
        final BigDecimal pd12 = amount(object, PD_12_MONTH_PARAM, v);
        final BigDecimal pdLife = amount(object, PD_LIFETIME_PARAM, v);
        final BigDecimal lgd = amount(object, LGD_PARAM, v);
        v.reset().parameter(RATING_CODE_PARAM).value(rating).notBlank().notExceedingLengthOf(50);
        v.reset().parameter(STAGE_PARAM).value(stage).notNull().inMinMaxRange(1, 3);
        v.reset().parameter(PD_12_MONTH_PARAM).value(pd12).notNull().zeroOrPositiveAmount().notGreaterThanMax(HUNDRED);
        v.reset().parameter(PD_LIFETIME_PARAM).value(pdLife).notNull().zeroOrPositiveAmount().notGreaterThanMax(HUNDRED);
        v.reset().parameter(LGD_PARAM).value(lgd).notNull().zeroOrPositiveAmount().notGreaterThanMax(HUNDRED);
        if (pd12 != null && pdLife != null && pdLife.compareTo(pd12) < 0) {
            v.reset().parameter(PD_LIFETIME_PARAM).value(pdLife).failWithCode("less.than.12.month.pd", pd12);
        }
        throwIfAny(errors);
        return new EclParameterRequest(rating, stage, pd12, pdLife, lgd);
    }

    // ---------- prices ----------

    /** One price at the top level, or several under {@code prices}. */
    public List<PriceRequest> validatePrices(final String json) {
        final JsonObject object = parseObject(json);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_PRICE_RESOURCE_NAME);
        final List<PriceRequest> prices = new ArrayList<>();
        if (object.has(PRICES_PARAM)) {
            this.fromApiJsonHelper.checkForUnsupportedParameters(object, PRICE_BATCH_PARAMS);
            final JsonElement array = object.get(PRICES_PARAM);
            if (array == null || !array.isJsonArray() || array.getAsJsonArray().isEmpty()) {
                v.reset().parameter(PRICES_PARAM).failWithCode("must.be.a.non.empty.array");
                throwIfAny(errors);
            }
            final JsonArray items = array.getAsJsonArray();
            if (items.size() > 500) {
                v.reset().parameter(PRICES_PARAM).value(items.size()).failWithCode("at.most.500");
            }
            final String format = text(object, DATE_FORMAT_PARAM);
            for (int i = 0; i < items.size(); i++) {
                if (!items.get(i).isJsonObject()) {
                    v.reset().parameterAtIndexArray(PRICES_PARAM, i + 1).failWithCode("not.an.object");
                    continue;
                }
                final JsonObject item = items.get(i).getAsJsonObject();
                this.fromApiJsonHelper.checkForUnsupportedParameters(item, PRICE_PARAMS);
                prices.add(price(item, format, v, PRICES_PARAM + "[" + i + "]."));
            }
        } else {
            this.fromApiJsonHelper.checkForUnsupportedParameters(object, PRICE_PARAMS);
            prices.add(price(object, text(object, DATE_FORMAT_PARAM), v, ""));
        }
        throwIfAny(errors);
        return prices;
    }

    private static PriceRequest price(final JsonObject object, final String format, final DataValidatorBuilder v, final String prefix) {
        final String instrument = text(object, INSTRUMENT_ID_PARAM);
        final LocalDate date = date(object, PRICE_DATE_PARAM, format, v, prefix + PRICE_DATE_PARAM);
        final BigDecimal clean = amount(object, CLEAN_PRICE_PARAM, v, prefix + CLEAN_PRICE_PARAM);
        final BigDecimal yield = amount(object, YIELD_PARAM, v, prefix + YIELD_PARAM);
        final String source = upper(text(object, SOURCE_PARAM));
        v.reset().parameter(prefix + INSTRUMENT_ID_PARAM).value(instrument).notBlank().matchesRegularExpression(INSTRUMENT_PATTERN,
                "The parameter `instrumentId` may hold letters, digits, -, _, . and / only, 100 at most");
        if (object.get(PRICE_DATE_PARAM) == null || object.get(PRICE_DATE_PARAM).isJsonNull()) {
            v.reset().parameter(prefix + PRICE_DATE_PARAM).value(null).notNull();
        }
        if (clean == null && yield == null) {
            v.reset().parameter(prefix + CLEAN_PRICE_PARAM).failWithCode("clean.price.or.yield.required");
        }
        v.reset().parameter(prefix + CLEAN_PRICE_PARAM).value(clean).ignoreIfNull().positiveAmount().notGreaterThanMax(MAX_PRICE);
        v.reset().parameter(prefix + YIELD_PARAM).value(yield).ignoreIfNull().positiveAmount().notGreaterThanMax(HUNDRED);
        v.reset().parameter(prefix + SOURCE_PARAM).value(source).ignoreIfNull().isOneOfTheseStringValues(names(PriceSource.values()));
        final PriceSource parsed = source == null || Arrays.stream(PriceSource.values()).noneMatch(s -> s.name().equals(source))
                ? PriceSource.MANUAL
                : PriceSource.valueOf(source);
        return new PriceRequest(instrument, date, clean, yield, parsed);
    }

    // ---------- helpers ----------

    private static void percent(final DataValidatorBuilder v, final String parameter, final BigDecimal value) {
        v.reset().parameter(parameter).value(value).ignoreIfNull().zeroOrPositiveAmount().notGreaterThanMax(HUNDRED);
    }

    private JsonObject parse(final String json, final Set<String> supportedParameters) {
        final JsonObject object = parseObject(json);
        this.fromApiJsonHelper.checkForUnsupportedParameters(object, supportedParameters);
        return object;
    }

    private JsonObject parseObject(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        if (element == null || !element.isJsonObject()) {
            throw new InvalidJsonException();
        }
        return element.getAsJsonObject();
    }

    private static BigDecimal amount(final JsonObject object, final String parameterName, final DataValidatorBuilder v) {
        return amount(object, parameterName, v, parameterName);
    }

    private static BigDecimal amount(final JsonObject object, final String parameterName, final DataValidatorBuilder v,
            final String reportedName) {
        final JsonElement value = object.get(parameterName);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsBigDecimal();
        } catch (NumberFormatException | UnsupportedOperationException e) {
            v.reset().parameter(reportedName).value(value.toString()).failWithCode("not.a.number");
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
        return date(object, parameterName, text(object, DATE_FORMAT_PARAM), v, parameterName);
    }

    private static LocalDate date(final JsonObject object, final String parameterName, final String format, final DataValidatorBuilder v,
            final String reportedName) {
        final String value = text(object, parameterName);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value,
                    format == null ? DateTimeFormatter.ISO_LOCAL_DATE : DateTimeFormatter.ofPattern(format, Locale.ENGLISH));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            v.reset().parameter(reportedName).value(value).failWithCode("invalid.date.format", format);
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
