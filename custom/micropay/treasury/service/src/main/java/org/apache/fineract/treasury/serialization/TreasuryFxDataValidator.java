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

import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.ACTIVE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.ADJUSTMENT_GL_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.AMOUNT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.AS_OF_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.BASE_CURRENCY_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.BATCH_RATE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.CLIENT_TYPE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.CONFIG_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.CORE_CAPITAL_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.CURRENCY_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.DATE_FORMAT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.EFFECTIVE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.FX_MAX_RATE_AGE_DAYS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.GAIN_GL_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.GL_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.LOSS_GL_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.NOP_CAPITAL_BASIS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.NOTE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.NOTE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.QUOTE_CURRENCY_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.RATES_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.RATE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.RATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.RATE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.RATE_TYPE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.RUN_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.SOURCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.TENANT_SETTING_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.TREASURY_CORE_CAPITAL_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.TREASURY_FX_RATE_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.TREASURY_FX_REVALUATION_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.TREASURY_FX_REVAL_CONFIG_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.TREASURY_TENANT_SETTING_RESOURCE_NAME;

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
import org.apache.fineract.treasury.fx.FxRateSource;
import org.apache.fineract.treasury.fx.FxRateType;
import org.apache.fineract.treasury.fx.NopCapitalBasis;
import org.apache.fineract.treasury.setup.ClientType;
import org.springframework.stereotype.Component;

/**
 * FX rates (one, or a batch as {@code rates}), revaluation settings, revaluation runs and core capital. Dates are ISO
 * unless a {@code dateFormat} is sent; a rate is units of the base currency for one unit of the quote currency.
 */
@Component
@RequiredArgsConstructor
public class TreasuryFxDataValidator {

    static final int MAX_BATCH = 500;

    private final FromJsonHelper fromApiJsonHelper;

    public record RateRequest(String baseCurrency, String quoteCurrency, LocalDate rateDate, FxRateType rateType, BigDecimal rate,
            FxRateSource source, String note) {
    }

    public record ConfigRequest(Long glAccountId, String currencyCode, Long adjustmentGlAccountId, Long gainGlAccountId,
            Long lossGlAccountId, boolean active, String note) {
    }

    public record RunRequest(LocalDate asOfDate, String currencyCode) {
    }

    public record CoreCapitalRequest(LocalDate effectiveDate, BigDecimal amount, String note) {
    }

    /** The settings sent; a setting sent as null goes back to the deployment default, one not sent stays. */
    public record TenantSettingRequest(boolean baseCurrencySent, String baseCurrency, boolean fxMaxRateAgeDaysSent,
            Integer fxMaxRateAgeDays, boolean nopCapitalBasisSent, NopCapitalBasis nopCapitalBasis, boolean clientTypeSent,
            ClientType clientType) {
    }

    /** One rate, or {@code rates}: a batch, each rate IMPORT unless it names its source. */
    public List<RateRequest> validateRates(final String json) {
        final JsonObject object = parse(json, RATE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_FX_RATE_RESOURCE_NAME);
        final List<RateRequest> rates = new ArrayList<>();
        final JsonElement batch = object.get(RATES_PARAM);
        if (batch != null && !batch.isJsonNull()) {
            if (object.keySet().stream().anyMatch(k -> !RATES_PARAM.equals(k) && !DATE_FORMAT_PARAM.equals(k) && !"locale".equals(k))) {
                v.reset().parameter(RATES_PARAM).value(null).failWithCode("alone");
            } else if (!batch.isJsonArray() || batch.getAsJsonArray().isEmpty()) {
                v.reset().parameter(RATES_PARAM).value(null).failWithCode("cannot.be.empty");
            } else if (batch.getAsJsonArray().size() > MAX_BATCH) {
                v.reset().parameter(RATES_PARAM).value(batch.getAsJsonArray().size()).failWithCode("too.many", MAX_BATCH);
            } else {
                final JsonArray array = batch.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    if (!array.get(i).isJsonObject()) {
                        v.reset().parameter(RATES_PARAM + "[" + i + "]").failWithCode("not.an.object");
                        continue;
                    }
                    final JsonObject rate = array.get(i).getAsJsonObject();
                    this.fromApiJsonHelper.checkForUnsupportedParameters(rate, BATCH_RATE_PARAMS);
                    rates.add(rate(rate, v, RATES_PARAM + "[" + i + "].", FxRateSource.IMPORT, text(object, DATE_FORMAT_PARAM)));
                }
            }
        } else {
            rates.add(rate(object, v, "", FxRateSource.MANUAL, text(object, DATE_FORMAT_PARAM)));
        }
        throwIfAny(errors);
        return rates;
    }

    private static RateRequest rate(final JsonObject o, final DataValidatorBuilder v, final String p, final FxRateSource defaultSource,
            final String format) {
        final String base = upper(text(o, BASE_CURRENCY_PARAM));
        v.reset().parameter(p + BASE_CURRENCY_PARAM).value(base).ignoreIfNull().matchesRegularExpression("^[A-Z]{3}$",
                "The parameter `baseCurrency` is a three-letter code");
        final String quote = upper(text(o, QUOTE_CURRENCY_PARAM));
        v.reset().parameter(p + QUOTE_CURRENCY_PARAM).value(quote).notBlank().matchesRegularExpression("^[A-Z]{3}$",
                "The parameter `quoteCurrency` is a three-letter code");
        final LocalDate date = date(o, RATE_DATE_PARAM, v, p, format);
        v.reset().parameter(p + RATE_DATE_PARAM).value(date).notNull();
        final FxRateType type = enumValue(o, RATE_TYPE_PARAM, FxRateType.class, v, p);
        final FxRateSource source = enumValue(o, SOURCE_PARAM, FxRateSource.class, v, p);
        final BigDecimal rate = amount(o, RATE_PARAM, v, p);
        v.reset().parameter(p + RATE_PARAM).value(rate).notNull().positiveAmount();
        if (rate != null && rate.scale() > 10) {
            v.reset().parameter(p + RATE_PARAM).value(rate).failWithCode("more.than.10.decimals");
        }
        final String note = text(o, NOTE_PARAM);
        v.reset().parameter(p + NOTE_PARAM).value(note).ignoreIfNull().notExceedingLengthOf(500);
        if (base != null && base.equals(quote)) {
            v.reset().parameter(p + QUOTE_CURRENCY_PARAM).value(quote).failWithCode("same.as.base");
        }
        return new RateRequest(base, quote, date, type == null ? FxRateType.MID : type, rate, source == null ? defaultSource : source,
                note);
    }

    public ConfigRequest validateConfig(final String json) {
        final JsonObject object = parse(json, CONFIG_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_FX_REVAL_CONFIG_RESOURCE_NAME);
        final Long gl = id(object, GL_ACCOUNT_ID_PARAM, v);
        final String currency = upper(text(object, CURRENCY_CODE_PARAM));
        v.reset().parameter(CURRENCY_CODE_PARAM).value(currency).notBlank().matchesRegularExpression("^[A-Z]{3}$",
                "The parameter `currencyCode` is a three-letter code");
        final Long adjustment = id(object, ADJUSTMENT_GL_ACCOUNT_ID_PARAM, v);
        final Long gain = id(object, GAIN_GL_ACCOUNT_ID_PARAM, v);
        final Long loss = id(object, LOSS_GL_ACCOUNT_ID_PARAM, v);
        final JsonElement active = object.get(ACTIVE_PARAM);
        boolean isActive = true;
        if (active != null && !active.isJsonNull()) {
            if (!active.isJsonPrimitive() || !active.getAsJsonPrimitive().isBoolean()) {
                v.reset().parameter(ACTIVE_PARAM).value(active.toString()).failWithCode("not.a.boolean");
            } else {
                isActive = active.getAsBoolean();
            }
        }
        final String note = text(object, NOTE_PARAM);
        v.reset().parameter(NOTE_PARAM).value(note).ignoreIfNull().notExceedingLengthOf(500);
        if (gain != null && gain.equals(loss) || adjustment != null && (adjustment.equals(gain) || adjustment.equals(loss))) {
            v.reset().parameter(ADJUSTMENT_GL_ACCOUNT_ID_PARAM).value(adjustment).failWithCode("accounts.must.differ");
        }
        throwIfAny(errors);
        return new ConfigRequest(gl, currency, adjustment, gain, loss, isActive, note);
    }

    public RunRequest validateRun(final String json) {
        final JsonObject object = parse(StringUtils.isBlank(json) ? "{}" : json, RUN_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_FX_REVALUATION_RESOURCE_NAME);
        final LocalDate asOf = date(object, AS_OF_DATE_PARAM, v, "", text(object, DATE_FORMAT_PARAM));
        final String currency = upper(text(object, CURRENCY_CODE_PARAM));
        v.reset().parameter(CURRENCY_CODE_PARAM).value(currency).ignoreIfNull().matchesRegularExpression("^[A-Z]{3}$",
                "The parameter `currencyCode` is a three-letter code");
        throwIfAny(errors);
        return new RunRequest(asOf, currency);
    }

    public CoreCapitalRequest validateCoreCapital(final String json) {
        final JsonObject object = parse(json, CORE_CAPITAL_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_CORE_CAPITAL_RESOURCE_NAME);
        final LocalDate effective = date(object, EFFECTIVE_DATE_PARAM, v, "", text(object, DATE_FORMAT_PARAM));
        v.reset().parameter(EFFECTIVE_DATE_PARAM).value(effective).notNull();
        final BigDecimal amount = amount(object, AMOUNT_PARAM, v, "");
        v.reset().parameter(AMOUNT_PARAM).value(amount).notNull().positiveAmount();
        final String note = text(object, NOTE_PARAM);
        v.reset().parameter(NOTE_PARAM).value(note).ignoreIfNull().notExceedingLengthOf(500);
        throwIfAny(errors);
        return new CoreCapitalRequest(effective, amount, note);
    }

    public TenantSettingRequest validateTenantSetting(final String json, final List<String> organisationCurrencies) {
        final JsonObject object = parse(json, TENANT_SETTING_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_TENANT_SETTING_RESOURCE_NAME);
        final String base = upper(text(object, BASE_CURRENCY_PARAM));
        v.reset().parameter(BASE_CURRENCY_PARAM).value(base).ignoreIfNull().isOneOfTheseStringValues(organisationCurrencies);
        Integer age = null;
        if (object.has(FX_MAX_RATE_AGE_DAYS_PARAM) && !object.get(FX_MAX_RATE_AGE_DAYS_PARAM).isJsonNull()) {
            age = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(FX_MAX_RATE_AGE_DAYS_PARAM, object);
            v.reset().parameter(FX_MAX_RATE_AGE_DAYS_PARAM).value(age).notNull().inMinMaxRange(0, 30);
        }
        final NopCapitalBasis basis = enumValue(object, NOP_CAPITAL_BASIS_PARAM, NopCapitalBasis.class, v, "");
        final ClientType clientType = enumValue(object, CLIENT_TYPE_PARAM, ClientType.class, v, "");
        throwIfAny(errors);
        return new TenantSettingRequest(object.has(BASE_CURRENCY_PARAM), base, object.has(FX_MAX_RATE_AGE_DAYS_PARAM), age,
                object.has(NOP_CAPITAL_BASIS_PARAM), basis, object.has(CLIENT_TYPE_PARAM), clientType);
    }

    public String validateNote(final String json) {
        final JsonObject object = parse(StringUtils.isBlank(json) ? "{}" : json, NOTE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final String note = text(object, NOTE_PARAM);
        new DataValidatorBuilder(errors).resource(TREASURY_FX_REVALUATION_RESOURCE_NAME).parameter(NOTE_PARAM).value(note).ignoreIfNull()
                .notExceedingLengthOf(500);
        throwIfAny(errors);
        return note;
    }

    // ---------- helpers ----------

    private Long id(final JsonObject object, final String parameterName, final DataValidatorBuilder v) {
        final Long id = this.fromApiJsonHelper.extractLongNamed(parameterName, object);
        v.reset().parameter(parameterName).value(id).notNull().longGreaterThanZero();
        return id;
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

    private static <E extends Enum<E>> E enumValue(final JsonObject object, final String parameterName, final Class<E> type,
            final DataValidatorBuilder v, final String prefix) {
        final String value = upper(text(object, parameterName));
        if (value == null) {
            return null;
        }
        final List<String> names = Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        if (!names.contains(value)) {
            v.reset().parameter(prefix + parameterName).value(value).isOneOfTheseStringValues(names);
            return null;
        }
        return Enum.valueOf(type, value);
    }

    private static BigDecimal amount(final JsonObject object, final String parameterName, final DataValidatorBuilder v,
            final String prefix) {
        final JsonElement value = object.get(parameterName);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsBigDecimal();
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException e) {
            v.reset().parameter(prefix + parameterName).value(value.toString()).failWithCode("not.a.number");
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

    private static LocalDate date(final JsonObject object, final String parameterName, final DataValidatorBuilder v, final String prefix,
            final String format) {
        final String value = text(object, parameterName);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value,
                    format == null ? DateTimeFormatter.ISO_LOCAL_DATE : DateTimeFormatter.ofPattern(format, Locale.ENGLISH));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            v.reset().parameter(prefix + parameterName).value(value).failWithCode("invalid.date.format", format);
            return null;
        }
    }

    private static void throwIfAny(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }
}
