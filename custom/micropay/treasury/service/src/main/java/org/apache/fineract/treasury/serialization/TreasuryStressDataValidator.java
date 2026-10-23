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

import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.ACTION_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.ACTION_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.COMMENT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.COMMENT_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.COPY_FROM_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.DATE_FORMAT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.DESCRIPTION_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.EFFECTIVE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.FUNDING_LINE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.NOTE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.NOTE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.SCENARIO_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.SCENARIO_TYPE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.SEVERITY_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.SHOCKS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.SHOCK_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.TREASURY_CFP_ACTIVATION_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.TREASURY_CFP_TRIGGER_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.TREASURY_FUNDING_LINE_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.TREASURY_SCENARIO_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.TRIGGER_PARAMS;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.limits.LimitLevel;
import org.apache.fineract.treasury.limits.LimitMetric;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.stress.CfpStage;
import org.apache.fineract.treasury.stress.FundingLineStatus;
import org.apache.fineract.treasury.stress.FundingSourceType;
import org.apache.fineract.treasury.stress.ScenarioType;
import org.apache.fineract.treasury.stress.Shock;
import org.apache.fineract.treasury.stress.ShockSide;
import org.apache.fineract.treasury.stress.ShockType;
import org.apache.fineract.treasury.stress.TriggerCondition;
import org.springframework.stereotype.Component;

/**
 * Scenarios and their shocks, funding lines, contingency funding plan triggers and their actions, and activation steps.
 * Funding lines and triggers are checked as a whole: on an update the fields sent are laid over the current ones (a
 * JSON null clears one). Dates are ISO unless a {@code dateFormat} is sent; factors are percentages.
 */
@Component
@RequiredArgsConstructor
public class TreasuryStressDataValidator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_MULTIPLIER = BigDecimal.valueOf(1000);
    private static final int MAX_DAYS = 3650;

    private final FromJsonHelper fromApiJsonHelper;

    public record ScenarioRequest(Set<String> parametersPassed, String code, String name, ScenarioType scenarioType, String severity,
            LocalDate effectiveDate, String description, List<Shock> shocks, Long copyFromId) {

        public boolean has(final String parameter) {
            return this.parametersPassed.contains(parameter);
        }
    }

    public record FundingLineRequest(String code, String name, FundingSourceType sourceType, Long counterpartyId, String currencyCode,
            BigDecimal committedAmount, BigDecimal drawnAmount, BigDecimal availableAmount, int daysToAccess, LocalDate expiryDate,
            Long ownerUserId, String ownerName, FundingLineStatus status, boolean includeInCapacity, String description) {
    }

    public record ActionSpec(String description, Long ownerRoleId, Long ownerUserId, Long fundingLineId) {
    }

    public record TriggerRequest(String code, String name, TriggerCondition conditionType, Long limitId, LimitMetric metric,
            LimitLevel level, BigDecimal survivalDays, String scenarioCode, CfpStage stage, Long notifyRoleId, String description,
            List<ActionSpec> actions) {
    }

    public record ActionStep(Long actionId, String comment) {
    }

    // ---------- scenarios ----------

    public ScenarioRequest validateScenario(final String json, final boolean create) {
        final JsonObject object = parse(json, SCENARIO_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_SCENARIO_RESOURCE_NAME);
        final Long copyFrom = this.fromApiJsonHelper.extractLongNamed(COPY_FROM_ID_PARAM, object);
        if (!create && object.has(COPY_FROM_ID_PARAM)) {
            v.reset().parameter(COPY_FROM_ID_PARAM).value(copyFrom).failWithCode("only.on.create");
        }
        v.reset().parameter(COPY_FROM_ID_PARAM).value(copyFrom).ignoreIfNull().longGreaterThanZero();
        final boolean whole = create && copyFrom == null;
        final String code = upper(text(object, CODE_PARAM));
        if (create || object.has(CODE_PARAM)) {
            if (!create) {
                v.reset().parameter(CODE_PARAM).value(code).failWithCode("cannot.be.changed");
            } else if (copyFrom == null || code != null) {
                v.reset().parameter(CODE_PARAM).value(code).notBlank().notExceedingLengthOf(50).matchesRegularExpression("^[A-Z0-9_]*$",
                        "The parameter `code` has letters, digits and underscores only");
            }
        }
        final String name = text(object, NAME_PARAM);
        if (whole || object.has(NAME_PARAM)) {
            v.reset().parameter(NAME_PARAM).value(name).notBlank().notExceedingLengthOf(150);
        }
        final ScenarioType type = enumValue(object, SCENARIO_TYPE_PARAM, ScenarioType.class, v, whole);
        final String severity = text(object, SEVERITY_PARAM);
        v.reset().parameter(SEVERITY_PARAM).value(severity).ignoreIfNull().notExceedingLengthOf(50);
        final LocalDate effective = date(object, EFFECTIVE_DATE_PARAM, v);
        final String description = text(object, DESCRIPTION_PARAM);
        v.reset().parameter(DESCRIPTION_PARAM).value(description).ignoreIfNull().notExceedingLengthOf(1000);
        List<Shock> shocks = null;
        if (object.has(SHOCKS_PARAM) || whole) {
            final JsonElement element = object.get(SHOCKS_PARAM);
            if (element == null || !element.isJsonArray() || element.getAsJsonArray().isEmpty()) {
                v.reset().parameter(SHOCKS_PARAM).value(null).failWithCode("cannot.be.empty");
            } else {
                shocks = shocks(element.getAsJsonArray(), v);
            }
        }
        throwIfAny(errors);
        return new ScenarioRequest(new HashSet<>(object.keySet()), code, name, type, severity, effective, description, shocks, copyFrom);
    }

    private List<Shock> shocks(final JsonArray array, final DataValidatorBuilder v) {
        final List<Shock> shocks = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            final String p = SHOCKS_PARAM + "[" + i + "].";
            if (!array.get(i).isJsonObject()) {
                v.reset().parameter(p.substring(0, p.length() - 1)).failWithCode("not.an.object");
                continue;
            }
            final JsonObject o = array.get(i).getAsJsonObject();
            this.fromApiJsonHelper.checkForUnsupportedParameters(o, SHOCK_PARAMS);
            final ShockType type = enumValue(o, "type", ShockType.class, v, true, p);
            final String label = text(o, "label");
            v.reset().parameter(p + "label").value(label).notBlank().notExceedingLengthOf(150);
            final LadderSourceCode source = enumValue(o, "source", LadderSourceCode.class, v, false, p);
            final String lineCode = upper(text(o, "lineCode"));
            v.reset().parameter(p + "lineCode").value(lineCode).ignoreIfNull().notExceedingLengthOf(50);
            final Long productId = this.fromApiJsonHelper.extractLongNamed("productId", o);
            v.reset().parameter(p + "productId").value(productId).ignoreIfNull().longGreaterThanZero();
            final String bucket = upper(text(o, "bucketCode"));
            v.reset().parameter(p + "bucketCode").value(bucket).ignoreIfNull().notExceedingLengthOf(20);
            final ShockSide side = enumValue(o, "side", ShockSide.class, v, false, p);
            final BigDecimal factor = amount(o, "factor", v, p);
            final String assetClass = upper(text(o, "assetClass"));
            final FundingSourceType fundingType = enumValue(o, "fundingSourceType", FundingSourceType.class, v, false, p);
            final Long fundingLine = this.fromApiJsonHelper.extractLongNamed("fundingLineId", o);
            v.reset().parameter(p + "fundingLineId").value(fundingLine).ignoreIfNull().longGreaterThanZero();
            if (type != null) {
                final boolean lines = type.shocksLines();
                if (lines && source == null) {
                    v.reset().parameter(p + "source").value(null).failWithCode("required.for.type", type.name());
                }
                if (source == LadderSourceCode.TOTAL) {
                    v.reset().parameter(p + "source").value(source.name()).failWithCode("not.shockable");
                }
                if (type == ShockType.REMOVE_ROLLOVER && source != null && source != LadderSourceCode.DEALS
                        && source != LadderSourceCode.TERM_DEPOSITS) {
                    v.reset().parameter(p + "source").value(source.name()).failWithCode("no.rollover");
                }
                notAllowed(v, p, type, !lines, Map.of("source", nullable(source), "lineCode", nullable(lineCode), "productId",
                        nullable(productId), "bucketCode", nullable(bucket), "side", nullable(side)));
                notAllowed(v, p, type, type != ShockType.HAIRCUT_LIQUID_ASSET, Map.of("assetClass", nullable(assetClass)));
                notAllowed(v, p, type, type != ShockType.FREEZE_LINE,
                        Map.of("fundingSourceType", nullable(fundingType), "fundingLineId", nullable(fundingLine)));
                notAllowed(v, p, type, type == ShockType.REMOVE_ROLLOVER || type == ShockType.FREEZE_LINE,
                        Map.of("factor", nullable(factor)));
                if (type == ShockType.ADD_OUTFLOW_PCT_OF_BALANCE && side == ShockSide.INFLOW) {
                    v.reset().parameter(p + "side").value(side.name()).failWithCode("inflow.not.allowed");
                }
                if (type == ShockType.MULTIPLY) {
                    v.reset().parameter(p + "factor").value(factor).notNull().zeroOrPositiveAmount();
                    if (factor != null && factor.compareTo(MAX_MULTIPLIER) > 0) {
                        v.reset().parameter(p + "factor").value(factor).failWithCode("above.maximum", MAX_MULTIPLIER);
                    }
                }
                if (type == ShockType.ADD_OUTFLOW_PCT_OF_BALANCE || type == ShockType.HAIRCUT_LIQUID_ASSET) {
                    v.reset().parameter(p + "factor").value(factor).notNull().zeroOrPositiveAmount();
                    if (factor != null && factor.compareTo(HUNDRED) > 0) {
                        v.reset().parameter(p + "factor").value(factor).failWithCode("above.maximum", HUNDRED);
                    }
                }
                if (type == ShockType.HAIRCUT_LIQUID_ASSET) {
                    v.reset().parameter(p + "assetClass").value(assetClass).notBlank().notExceedingLengthOf(50);
                }
                if (type == ShockType.FREEZE_LINE && (fundingType == null) == (fundingLine == null)) {
                    v.reset().parameter(p + "fundingSourceType").value(null).failWithCode("source.type.or.line.required");
                }
            }
            shocks.add(new Shock(type, label, source, lineCode, productId, bucket,
                    type == null || !type.shocksLines() ? null : side == null ? ShockSide.BOTH : side, factor, assetClass, fundingType,
                    fundingLine));
        }
        return shocks;
    }

    private static void notAllowed(final DataValidatorBuilder v, final String prefix, final ShockType type, final boolean refused,
            final Map<String, Object> values) {
        if (!refused) {
            return;
        }
        values.entrySet().stream().filter(e -> e.getValue() != NONE).sorted(Map.Entry.comparingByKey()).forEach(
                e -> v.reset().parameter(prefix + e.getKey()).value(e.getValue()).failWithCode("not.allowed.for.type", type.name()));
    }

    // ---------- funding lines ----------

    /**
     * @param current
     *            the line as it is, null for a new line
     */
    public FundingLineRequest validateFundingLine(final String json, final JsonObject current) {
        final JsonObject request = parse(json, FUNDING_LINE_PARAMS);
        final JsonObject object = current == null ? request : merge(current, request);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_FUNDING_LINE_RESOURCE_NAME);
        final String code = code(object, request, current, v);
        final String name = text(object, NAME_PARAM);
        v.reset().parameter(NAME_PARAM).value(name).notBlank().notExceedingLengthOf(150);
        final FundingSourceType type = enumValue(object, "sourceType", FundingSourceType.class, v, true);
        final Long counterparty = this.fromApiJsonHelper.extractLongNamed("counterpartyId", object);
        v.reset().parameter("counterpartyId").value(counterparty).ignoreIfNull().longGreaterThanZero();
        final String currency = upper(text(object, "currencyCode"));
        v.reset().parameter("currencyCode").value(currency).notBlank().matchesRegularExpression("^[A-Z]{3}$",
                "The parameter `currencyCode` is a three-letter code");
        final BigDecimal committed = amount(object, "committedAmount", v, "");
        v.reset().parameter("committedAmount").value(committed).notNull().zeroOrPositiveAmount();
        final BigDecimal drawnSent = amount(object, "drawnAmount", v, "");
        final BigDecimal drawn = drawnSent == null ? BigDecimal.ZERO : drawnSent;
        v.reset().parameter("drawnAmount").value(drawn).zeroOrPositiveAmount();
        if (committed != null && drawn.compareTo(committed) > 0) {
            v.reset().parameter("drawnAmount").value(drawn).failWithCode("above.committed.amount", committed);
        }
        final BigDecimal available = amount(object, "availableAmount", v, "");
        v.reset().parameter("availableAmount").value(available).ignoreIfNull().zeroOrPositiveAmount();
        if (available != null && committed != null && committed.signum() > 0 && available.compareTo(committed.subtract(drawn)) > 0) {
            v.reset().parameter("availableAmount").value(available).failWithCode("above.undrawn.amount", committed.subtract(drawn));
        }
        final Integer days = integer(object, "daysToAccess", v, "");
        v.reset().parameter("daysToAccess").value(days).notNull().inMinMaxRange(0, MAX_DAYS);
        final LocalDate expiry = date(object, "expiryDate", v);
        final Long owner = this.fromApiJsonHelper.extractLongNamed("ownerUserId", object);
        v.reset().parameter("ownerUserId").value(owner).ignoreIfNull().longGreaterThanZero();
        final String ownerName = text(object, "ownerName");
        v.reset().parameter("ownerName").value(ownerName).ignoreIfNull().notExceedingLengthOf(150);
        final FundingLineStatus status = enumValue(object, "status", FundingLineStatus.class, v, false);
        final Optional<Boolean> capacity = bool(object, "includeInCapacity", v);
        final String description = text(object, DESCRIPTION_PARAM);
        v.reset().parameter(DESCRIPTION_PARAM).value(description).ignoreIfNull().notExceedingLengthOf(1000);
        throwIfAny(errors);
        return new FundingLineRequest(code, name, type, counterparty, currency, committed, drawn, available, days, expiry, owner, ownerName,
                status == null ? FundingLineStatus.ACTIVE : status, capacity.orElse(type.countsAsCapacityByDefault()), description);
    }

    // ---------- triggers ----------

    public TriggerRequest validateTrigger(final String json, final JsonObject current) {
        final JsonObject request = parse(json, TRIGGER_PARAMS);
        final JsonObject object = current == null ? request : merge(current, request);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_CFP_TRIGGER_RESOURCE_NAME);
        final String code = code(object, request, current, v);
        final String name = text(object, NAME_PARAM);
        v.reset().parameter(NAME_PARAM).value(name).notBlank().notExceedingLengthOf(150);
        final TriggerCondition condition = enumValue(object, "conditionType", TriggerCondition.class, v, true);
        final Long limitId = this.fromApiJsonHelper.extractLongNamed("limitId", object);
        v.reset().parameter("limitId").value(limitId).ignoreIfNull().longGreaterThanZero();
        final LimitMetric metric = enumValue(object, "metric", LimitMetric.class, v, false);
        LimitLevel level = enumValue(object, "level", LimitLevel.class, v, false);
        final BigDecimal survival = amount(object, "survivalDays", v, "");
        final String scenario = upper(text(object, "scenarioCode"));
        v.reset().parameter("scenarioCode").value(scenario).ignoreIfNull().notExceedingLengthOf(50);
        final CfpStage stage = enumValue(object, "stage", CfpStage.class, v, true);
        final Long role = this.fromApiJsonHelper.extractLongNamed("notifyRoleId", object);
        v.reset().parameter("notifyRoleId").value(role).ignoreIfNull().longGreaterThanZero();
        final String description = text(object, DESCRIPTION_PARAM);
        v.reset().parameter(DESCRIPTION_PARAM).value(description).ignoreIfNull().notExceedingLengthOf(1000);
        if (condition != null) {
            final Map<String, Object> breach = Map.of("limitId", nullable(limitId), "metric", nullable(metric), "level", nullable(level));
            final Map<String, Object> stress = Map.of("survivalDays", nullable(survival), "scenarioCode", nullable(scenario));
            if (condition == TriggerCondition.LIMIT_BREACH) {
                if ((limitId == null) == (metric == null)) {
                    v.reset().parameter("limitId").value(limitId).failWithCode("limit.or.metric.required");
                }
                level = level == null ? LimitLevel.WARNING : level;
            } else {
                refuse(v, breach, condition);
                level = null;
            }
            if (condition == TriggerCondition.SURVIVAL_BELOW) {
                v.reset().parameter("survivalDays").value(survival).notNull().positiveAmount();
            } else {
                refuse(v, stress, condition);
            }
        }
        final List<ActionSpec> actions = new ArrayList<>();
        final JsonElement element = object.get("actions");
        if (element != null && !element.isJsonNull()) {
            if (!element.isJsonArray()) {
                v.reset().parameter("actions").value(element.toString()).failWithCode("not.an.array");
            } else {
                final JsonArray array = element.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    final String p = "actions[" + i + "].";
                    if (!array.get(i).isJsonObject()) {
                        v.reset().parameter("actions[" + i + "]").failWithCode("not.an.object");
                        continue;
                    }
                    final JsonObject a = array.get(i).getAsJsonObject();
                    this.fromApiJsonHelper.checkForUnsupportedParameters(a, ACTION_PARAMS);
                    final String text = text(a, "description");
                    v.reset().parameter(p + "description").value(text).notBlank().notExceedingLengthOf(500);
                    final Long ownerRole = this.fromApiJsonHelper.extractLongNamed("ownerRoleId", a);
                    final Long ownerUser = this.fromApiJsonHelper.extractLongNamed("ownerUserId", a);
                    final Long line = this.fromApiJsonHelper.extractLongNamed("fundingLineId", a);
                    v.reset().parameter(p + "ownerRoleId").value(ownerRole).ignoreIfNull().longGreaterThanZero();
                    v.reset().parameter(p + "ownerUserId").value(ownerUser).ignoreIfNull().longGreaterThanZero();
                    v.reset().parameter(p + "fundingLineId").value(line).ignoreIfNull().longGreaterThanZero();
                    actions.add(new ActionSpec(text, ownerRole, ownerUser, line));
                }
            }
        }
        throwIfAny(errors);
        return new TriggerRequest(code, name, condition, limitId, metric, level, survival, scenario, stage, role, description, actions);
    }

    private static void refuse(final DataValidatorBuilder v, final Map<String, Object> values, final TriggerCondition condition) {
        values.entrySet().stream().filter(e -> e.getValue() != NONE).sorted(Map.Entry.comparingByKey()).forEach(
                e -> v.reset().parameter(e.getKey()).value(e.getValue()).failWithCode("not.allowed.for.condition", condition.name()));
    }

    // ---------- notes and activation steps ----------

    public String validateNote(final String json) {
        final JsonObject object = parse(StringUtils.isBlank(json) ? "{}" : json, NOTE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final String note = text(object, NOTE_PARAM);
        new DataValidatorBuilder(errors).resource(TREASURY_CFP_TRIGGER_RESOURCE_NAME).parameter(NOTE_PARAM).value(note).ignoreIfNull()
                .notExceedingLengthOf(1000);
        throwIfAny(errors);
        return note;
    }

    /** complete-action: actionId, optional comment; close: comment. */
    public ActionStep validateActivationStep(final String json, final boolean action) {
        final JsonObject object = parse(StringUtils.isBlank(json) ? "{}" : json, COMMENT_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_CFP_ACTIVATION_RESOURCE_NAME);
        final Long actionId = this.fromApiJsonHelper.extractLongNamed(ACTION_ID_PARAM, object);
        final String comment = text(object, COMMENT_PARAM);
        if (action) {
            v.reset().parameter(ACTION_ID_PARAM).value(actionId).notNull().longGreaterThanZero();
            v.reset().parameter(COMMENT_PARAM).value(comment).ignoreIfNull().notExceedingLengthOf(2000);
        } else {
            if (object.has(ACTION_ID_PARAM)) {
                v.reset().parameter(ACTION_ID_PARAM).value(actionId).failWithCode("not.allowed");
            }
            v.reset().parameter(COMMENT_PARAM).value(comment).notBlank().notExceedingLengthOf(2000);
        }
        throwIfAny(errors);
        return new ActionStep(actionId, comment);
    }

    // ---------- helpers ----------

    private static final Object NONE = new Object();

    private static Object nullable(final Object value) {
        return value == null ? NONE : value;
    }

    private static String code(final JsonObject object, final JsonObject request, final JsonObject current, final DataValidatorBuilder v) {
        final String code = upper(text(object, CODE_PARAM));
        v.reset().parameter(CODE_PARAM).value(code).notBlank().notExceedingLengthOf(50).matchesRegularExpression("^[A-Z0-9_]*$",
                "The parameter `code` has letters, digits and underscores only");
        if (current != null && request.has(CODE_PARAM) && !Objects.equals(code, upper(text(current, CODE_PARAM)))) {
            v.reset().parameter(CODE_PARAM).value(code).failWithCode("cannot.be.changed");
        }
        return code;
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
        return enumValue(object, parameterName, type, v, required, "");
    }

    private static <E extends Enum<E>> E enumValue(final JsonObject object, final String parameterName, final Class<E> type,
            final DataValidatorBuilder v, final boolean required, final String prefix) {
        final String value = upper(text(object, parameterName));
        if (value == null) {
            if (required) {
                v.reset().parameter(prefix + parameterName).value(value).notBlank();
            }
            return null;
        }
        final List<String> names = Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        if (!names.contains(value)) {
            v.reset().parameter(prefix + parameterName).value(value).isOneOfTheseStringValues(names);
            return null;
        }
        return Enum.valueOf(type, value);
    }

    /** Empty when not sent, so the default applies. */
    private static Optional<Boolean> bool(final JsonObject object, final String parameterName, final DataValidatorBuilder v) {
        final JsonElement value = object.get(parameterName);
        if (value == null || value.isJsonNull()) {
            return Optional.empty();
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            v.reset().parameter(parameterName).value(value.toString()).failWithCode("not.a.boolean");
            return Optional.empty();
        }
        return Optional.of(value.getAsBoolean());
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

    private static Integer integer(final JsonObject object, final String parameterName, final DataValidatorBuilder v, final String prefix) {
        final JsonElement value = object.get(parameterName);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (NumberFormatException | ArithmeticException | UnsupportedOperationException | IllegalStateException e) {
            v.reset().parameter(prefix + parameterName).value(value.toString()).failWithCode("not.a.whole.number");
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
