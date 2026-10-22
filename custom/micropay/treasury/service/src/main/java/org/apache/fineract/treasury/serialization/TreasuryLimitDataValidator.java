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

import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.AS_OF_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.COMMENT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.COMMENT_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.COMPARISON_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.CURRENCY_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.DATE_FORMAT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.DESCRIPTION_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.EFFECTIVE_FROM_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.EFFECTIVE_TO_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.EVALUATE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.GAP_BASIS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.GAP_TYPE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.HARD_LEVEL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.LIMIT_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.METRIC_KEY_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.METRIC_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.NOTE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.NOTE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.NOTIFY_ROLE_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.OFFICE_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.RUN_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.SCOPE_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.SCOPE_TYPE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.TOP_N_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.TREASURY_BREACH_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.TREASURY_LIMIT_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.UNIT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.USE_OWN_LEVELS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.WARNING_LEVEL_PARAM;

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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.limits.GapBasis;
import org.apache.fineract.treasury.limits.GapType;
import org.apache.fineract.treasury.limits.LimitComparison;
import org.apache.fineract.treasury.limits.LimitMetric;
import org.apache.fineract.treasury.limits.LimitScopeType;
import org.apache.fineract.treasury.limits.LimitUnit;
import org.springframework.stereotype.Component;

/**
 * Limits, evaluations and breach actions. A limit is checked as a whole: on an update the fields sent are laid over the
 * limit as it is (a JSON null clears one), so the same rules hold. Dates are ISO ({@code 2026-10-16}) unless a
 * {@code dateFormat} is sent; levels are percentages, amounts in the limit's currency, or days.
 */
@Component
@RequiredArgsConstructor
public class TreasuryLimitDataValidator {

    private static final int MAX_TOP_N = 1000;

    private final FromJsonHelper fromApiJsonHelper;

    public record LimitRequest(String code, String name, String description, LimitMetric metric, String metricKey, GapType gapType,
            GapBasis gapBasis, Integer topN, LimitScopeType scopeType, Long scopeId, String currencyCode, LimitComparison comparison,
            BigDecimal warningLevel, BigDecimal hardLevel, LimitUnit unit, boolean useOwnLevels, LocalDate effectiveFrom,
            LocalDate effectiveTo, Long notifyRoleId) {
    }

    public record EvaluateRequest(LocalDate asOfDate, String currencyCode, Long officeId, Long runId) {
    }

    /**
     * @param current
     *            the limit as it is (the fields of {@code GET /treasury/limits/{id}}), null for a new limit
     */
    public LimitRequest validateLimit(final String json, final JsonObject current) {
        final JsonObject request = parse(json, LIMIT_PARAMS);
        final JsonObject object = current == null ? request : merge(current, request);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_LIMIT_RESOURCE_NAME);
        final String code = upper(text(object, CODE_PARAM));
        v.reset().parameter(CODE_PARAM).value(code).notBlank().notExceedingLengthOf(50).matchesRegularExpression("^[A-Z0-9_]*$",
                "The parameter `code` has letters, digits and underscores only");
        if (current != null && request.has(CODE_PARAM) && !Objects.equals(code, upper(text(current, CODE_PARAM)))) {
            v.reset().parameter(CODE_PARAM).value(code).failWithCode("cannot.be.changed");
        }
        final String name = text(object, NAME_PARAM);
        v.reset().parameter(NAME_PARAM).value(name).notBlank().notExceedingLengthOf(150);
        final String description = text(object, DESCRIPTION_PARAM);
        v.reset().parameter(DESCRIPTION_PARAM).value(description).ignoreIfNull().notExceedingLengthOf(1000);
        final LimitMetric metric = enumValue(object, METRIC_PARAM, LimitMetric.class, v, true);
        final LimitScopeType scope = enumValue(object, SCOPE_TYPE_PARAM, LimitScopeType.class, v, true);
        final Long scopeId = this.fromApiJsonHelper.extractLongNamed(SCOPE_ID_PARAM, object);
        final String currency = upper(text(object, CURRENCY_CODE_PARAM));
        v.reset().parameter(CURRENCY_CODE_PARAM).value(currency).ignoreIfNull().matchesRegularExpression("^[A-Z]{3}$",
                "The parameter `currencyCode` is a three-letter code");
        final boolean own = bool(object, USE_OWN_LEVELS_PARAM, v);
        LimitComparison comparison = enumValue(object, COMPARISON_PARAM, LimitComparison.class, v, false);
        final BigDecimal warning = amount(object, WARNING_LEVEL_PARAM, v);
        final BigDecimal hard = amount(object, HARD_LEVEL_PARAM, v);
        LimitUnit unit = enumValue(object, UNIT_PARAM, LimitUnit.class, v, false);
        final String metricKey = upper(text(object, METRIC_KEY_PARAM));
        GapType gapType = enumValue(object, GAP_TYPE_PARAM, GapType.class, v, false);
        final GapBasis gapBasis = enumValue(object, GAP_BASIS_PARAM, GapBasis.class, v, false);
        Integer topN = integer(object, TOP_N_PARAM, v);
        final LocalDate from = date(object, EFFECTIVE_FROM_PARAM, v);
        final LocalDate to = date(object, EFFECTIVE_TO_PARAM, v);
        final Long role = this.fromApiJsonHelper.extractLongNamed(NOTIFY_ROLE_ID_PARAM, object);
        v.reset().parameter(NOTIFY_ROLE_ID_PARAM).value(role).ignoreIfNull().longGreaterThanZero();
        if (from != null && to != null && to.isBefore(from)) {
            v.reset().parameter(EFFECTIVE_TO_PARAM).value(to).failWithCode("before.effective.from", from);
        }
        if (scope != null) {
            if (scope.needsScopeId()) {
                v.reset().parameter(SCOPE_ID_PARAM).value(scopeId).notNull().longGreaterThanZero();
            } else if (scopeId != null) {
                v.reset().parameter(SCOPE_ID_PARAM).value(scopeId).failWithCode("not.allowed.for.scope", scope.name());
            }
            if (scope == LimitScopeType.CURRENCY && currency == null) {
                v.reset().parameter(CURRENCY_CODE_PARAM).value(currency).failWithCode("required.for.currency.scope");
            }
        }
        if (metric != null) {
            if (scope != null && !metric.allows(scope)) {
                v.reset().parameter(SCOPE_TYPE_PARAM).value(scope.name()).failWithCode("not.allowed.for.metric", metric.name(),
                        metric.scopes().toString());
            }
            if (own) {
                if (!metric.canUseOwnLevels()) {
                    v.reset().parameter(USE_OWN_LEVELS_PARAM).value(true).failWithCode("not.supported.for.metric", metric.name());
                }
                for (final Map.Entry<String, Object> set : Arrays.asList(Map.entry(COMPARISON_PARAM, nullable(comparison)),
                        Map.entry(WARNING_LEVEL_PARAM, nullable(warning)), Map.entry(HARD_LEVEL_PARAM, nullable(hard)),
                        Map.entry(UNIT_PARAM, nullable(unit)))) {
                    if (set.getValue() != NONE) {
                        v.reset().parameter(set.getKey()).value(set.getValue()).failWithCode("not.allowed.with.own.levels");
                    }
                }
                comparison = null;
                unit = metric.ownUnit();
            } else {
                v.reset().parameter(COMPARISON_PARAM).value(comparison).notNull();
                v.reset().parameter(HARD_LEVEL_PARAM).value(hard).notNull();
                v.reset().parameter(UNIT_PARAM).value(unit).notNull();
                if (unit != null && !metric.allows(unit)) {
                    v.reset().parameter(UNIT_PARAM).value(unit.name()).failWithCode("not.allowed.for.metric", metric.name(),
                            metric.units().toString());
                }
                if (comparison != null && warning != null && hard != null
                        && (comparison == LimitComparison.MIN ? warning.compareTo(hard) <= 0 : warning.compareTo(hard) >= 0)) {
                    v.reset().parameter(WARNING_LEVEL_PARAM).value(warning).failWithCode("not.before.hard.level", comparison.name(), hard);
                }
                if (unit == LimitUnit.AMOUNT && currency == null) {
                    v.reset().parameter(CURRENCY_CODE_PARAM).value(currency).failWithCode("required.for.amounts");
                }
            }
            if (metric.isKeyed()) {
                v.reset().parameter(METRIC_KEY_PARAM).value(metricKey).notBlank().notExceedingLengthOf(50);
            } else if (metricKey != null) {
                v.reset().parameter(METRIC_KEY_PARAM).value(metricKey).failWithCode("not.allowed.for.metric", metric.name());
            }
            if (metric == LimitMetric.BUCKET_GAP) {
                gapType = gapType == null ? GapType.CUMULATIVE : gapType;
                if (unit == LimitUnit.PERCENT) {
                    v.reset().parameter(GAP_BASIS_PARAM).value(gapBasis).notNull();
                } else if (gapBasis != null) {
                    v.reset().parameter(GAP_BASIS_PARAM).value(gapBasis.name()).failWithCode("only.for.percent");
                }
            } else {
                for (final Map.Entry<String, Object> set : Arrays.asList(Map.entry(GAP_TYPE_PARAM, nullable(gapType)),
                        Map.entry(GAP_BASIS_PARAM, nullable(gapBasis)))) {
                    if (set.getValue() != NONE) {
                        v.reset().parameter(set.getKey()).value(set.getValue()).failWithCode("not.allowed.for.metric", metric.name());
                    }
                }
            }
            if (metric.isConcentration()) {
                topN = topN == null ? 1 : topN;
                v.reset().parameter(TOP_N_PARAM).value(topN).inMinMaxRange(1, MAX_TOP_N);
            } else if (topN != null) {
                v.reset().parameter(TOP_N_PARAM).value(topN).failWithCode("not.allowed.for.metric", metric.name());
            }
        }
        throwIfAny(errors);
        return new LimitRequest(code, name, description, metric, metric.isKeyed() ? metricKey : null,
                metric == LimitMetric.BUCKET_GAP ? gapType : null, metric == LimitMetric.BUCKET_GAP ? gapBasis : null,
                metric.isConcentration() ? topN : null, scope, scope.needsScopeId() ? scopeId : null, currency, own ? null : comparison,
                own ? null : warning, own ? null : hard, unit, own, from, to, role);
    }

    public String validateNote(final String json) {
        final JsonObject object = parse(StringUtils.isBlank(json) ? "{}" : json, NOTE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final String note = text(object, NOTE_PARAM);
        new DataValidatorBuilder(errors).resource(TREASURY_LIMIT_RESOURCE_NAME).parameter(NOTE_PARAM).value(note).ignoreIfNull()
                .notExceedingLengthOf(1000);
        throwIfAny(errors);
        return note;
    }

    /** A breach acknowledgement (comment optional), resolution or comment (comment required). */
    public String validateBreachAction(final String json, final boolean commentRequired) {
        final JsonObject object = parse(StringUtils.isBlank(json) ? "{}" : json, COMMENT_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_BREACH_RESOURCE_NAME);
        final String comment = text(object, COMMENT_PARAM);
        if (commentRequired) {
            v.reset().parameter(COMMENT_PARAM).value(comment).notBlank().notExceedingLengthOf(2000);
        } else {
            v.reset().parameter(COMMENT_PARAM).value(comment).ignoreIfNull().notExceedingLengthOf(2000);
        }
        throwIfAny(errors);
        return comment;
    }

    public EvaluateRequest validateEvaluate(final String json) {
        final JsonObject object = parse(StringUtils.isBlank(json) ? "{}" : json, EVALUATE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_LIMIT_RESOURCE_NAME);
        final LocalDate asOf = date(object, AS_OF_DATE_PARAM, v);
        final String currency = upper(text(object, CURRENCY_CODE_PARAM));
        final Long office = this.fromApiJsonHelper.extractLongNamed(OFFICE_ID_PARAM, object);
        final Long run = this.fromApiJsonHelper.extractLongNamed(RUN_ID_PARAM, object);
        v.reset().parameter(RUN_ID_PARAM).value(run).ignoreIfNull().longGreaterThanZero();
        v.reset().parameter(OFFICE_ID_PARAM).value(office).ignoreIfNull().longGreaterThanZero();
        if (run == null) {
            v.reset().parameter(CURRENCY_CODE_PARAM).value(currency).notBlank();
        }
        v.reset().parameter(CURRENCY_CODE_PARAM).value(currency).ignoreIfNull().matchesRegularExpression("^[A-Z]{3}$",
                "The parameter `currencyCode` is a three-letter code");
        throwIfAny(errors);
        return new EvaluateRequest(asOf, currency, office, run);
    }

    // ---------- helpers ----------

    private static final Object NONE = new Object();

    private static Object nullable(final Object value) {
        return value == null ? NONE : value;
    }

    private static JsonObject merge(final JsonObject current, final JsonObject request) {
        final JsonObject merged = current.deepCopy();
        for (final Map.Entry<String, JsonElement> entry : request.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isJsonNull()) {
                merged.remove(entry.getKey());
            } else {
                merged.add(entry.getKey(), entry.getValue());
            }
        }
        return merged;
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
            final DataValidatorBuilder v, final boolean required) {
        final String value = upper(text(object, parameterName));
        if (value == null) {
            if (required) {
                v.reset().parameter(parameterName).value(value).notBlank();
            }
            return null;
        }
        final List<String> names = Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        if (!names.contains(value)) {
            v.reset().parameter(parameterName).value(value).isOneOfTheseStringValues(names);
            return null;
        }
        return Enum.valueOf(type, value);
    }

    /** False when absent; an error when not a boolean. */
    private static boolean bool(final JsonObject object, final String parameterName, final DataValidatorBuilder v) {
        final JsonElement value = object.get(parameterName);
        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            v.reset().parameter(parameterName).value(value.toString()).failWithCode("not.a.boolean");
            return false;
        }
        return value.getAsBoolean();
    }

    private static BigDecimal amount(final JsonObject object, final String parameterName, final DataValidatorBuilder v) {
        final JsonElement value = object.get(parameterName);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsBigDecimal();
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException e) {
            v.reset().parameter(parameterName).value(value.toString()).failWithCode("not.a.number");
            return null;
        }
    }

    private static Integer integer(final JsonObject object, final String parameterName, final DataValidatorBuilder v) {
        final JsonElement value = object.get(parameterName);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (NumberFormatException | ArithmeticException | UnsupportedOperationException | IllegalStateException e) {
            v.reset().parameter(parameterName).value(value.toString()).failWithCode("not.a.whole.number");
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

    private static void throwIfAny(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }
}
