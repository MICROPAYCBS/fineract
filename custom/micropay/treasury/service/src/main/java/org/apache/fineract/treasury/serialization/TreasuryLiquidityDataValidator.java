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

import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ACTIVATE_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ACTIVE_FROM_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ACTIVE_TO_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.AMOUNT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ASSET_CLASSES_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ASSUMPTIONS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ASSUMPTION_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ASSUMPTION_SET_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ASSUMPTION_SET_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.AS_OF_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.BUCKETS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.BUCKET_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.BUCKET_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.COPY_FROM_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.CURRENCY_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.DATE_FORMAT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.DAY_OF_MONTH_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.DESCRIPTION_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.DIRECTION_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.EFFECTIVE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.FROM_DAY_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.IS_DEFAULT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.LABEL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.LADDER_ITEM_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.MAX_DAYS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.MIN_DAYS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.NOTE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.OFFICE_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ONE_OFF_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.PRODUCT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.RATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.RATIOS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.RULE_SET_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.RULE_SET_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.RUN_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.SOURCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.SOURCE_RUN_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.TO_DAY_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.TREASURY_ASSUMPTION_SET_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.TREASURY_LADDER_ITEM_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.TREASURY_RULE_SET_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.TREASURY_RUN_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.TYPE_PARAM;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
import org.apache.fineract.treasury.liquidity.AssumptionType;
import org.apache.fineract.treasury.liquidity.Assumptions;
import org.apache.fineract.treasury.liquidity.Bucket;
import org.apache.fineract.treasury.liquidity.LadderBuilder;
import org.apache.fineract.treasury.liquidity.LadderItemDirection;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.liquidity.RunType;
import org.springframework.stereotype.Component;

/**
 * Field-level validation of rule sets and buckets, assumption sets, ladder items and runs. Dates are ISO
 * ({@code 2026-10-16}) unless a {@code dateFormat} is sent; rates are percentages.
 */
@Component
@RequiredArgsConstructor
public class TreasuryLiquidityDataValidator {

    private static final String CODE_PATTERN = "^[A-Za-z0-9_-]{1,50}$";
    private static final String BUCKET_CODE_PATTERN = "^[A-Za-z0-9_-]{1,20}$";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal FLOAT_SCALE_MAX = BigDecimal.valueOf(1000);
    /** The sources each assumption type applies to. */
    private static final Map<AssumptionType, Set<LadderSourceCode>> SOURCES = Map.of(AssumptionType.RUN_OFF,
            Set.of(LadderSourceCode.SAVINGS), AssumptionType.CORE_SHARE, Set.of(LadderSourceCode.SAVINGS), AssumptionType.PREPAYMENT,
            Set.of(LadderSourceCode.LOANS), AssumptionType.ARREARS_HAIRCUT, Set.of(LadderSourceCode.LOANS), AssumptionType.ROLLOVER,
            Set.of(LadderSourceCode.TERM_DEPOSITS, LadderSourceCode.DEALS), AssumptionType.DRAWDOWN, Set.of(LadderSourceCode.COMMITMENTS),
            AssumptionType.FLOAT_DEMAND, Set.of(LadderSourceCode.AGENT_FLOAT));

    private final FromJsonHelper fromApiJsonHelper;

    public record RuleSetRequest(Set<String> parametersPassed, String code, String name, LocalDate effectiveDate, String description,
            Boolean isDefault, List<Bucket> buckets, Long copyFromId, List<RatioConfigParser.RatioSpec> ratios,
            List<RatioConfigParser.AssetClassSpec> assetClasses) {

        public boolean has(final String parameter) {
            return this.parametersPassed.contains(parameter);
        }
    }

    public record AssumptionSetRequest(Set<String> parametersPassed, String code, String name, LocalDate effectiveDate, String description,
            List<Assumptions.Row> assumptions) {

        public boolean has(final String parameter) {
            return this.parametersPassed.contains(parameter);
        }
    }

    public record LadderItemRequest(Set<String> parametersPassed, String name, LadderItemDirection direction, BigDecimal amount,
            String currencyCode, Long officeId, LocalDate oneOffDate, Integer dayOfMonth, LocalDate activeFrom, LocalDate activeTo) {

        public boolean has(final String parameter) {
            return this.parametersPassed.contains(parameter);
        }
    }

    public record RunRequest(RunType type, LocalDate asOfDate, String currencyCode, Long officeId, Long ruleSetId, Long assumptionSetId,
            Long sourceRunId) {
    }

    // ---------- rule sets ----------

    public RuleSetRequest validateRuleSet(final String json, final boolean create) {
        final JsonObject object = parse(json, RULE_SET_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_RULE_SET_RESOURCE_NAME);
        final String code = upper(text(object, CODE_PARAM));
        final LocalDate effective = date(object, EFFECTIVE_DATE_PARAM, v);
        if (create) {
            v.reset().parameter(CODE_PARAM).value(code).notBlank().matchesRegularExpression(CODE_PATTERN,
                    "The parameter `code` may hold letters, digits, - and _ only, 50 at most");
            v.reset().parameter(NAME_PARAM).value(text(object, NAME_PARAM)).notBlank().notExceedingLengthOf(150);
            v.reset().parameter(EFFECTIVE_DATE_PARAM).value(effective).notNull();
            if (!object.has(COPY_FROM_ID_PARAM)) {
                v.reset().parameter(BUCKETS_PARAM).value(object.get(BUCKETS_PARAM)).notNull();
            }
        } else {
            if (object.has(CODE_PARAM)) {
                v.reset().parameter(CODE_PARAM).failWithCode("cannot.be.changed");
            }
            if (object.has(COPY_FROM_ID_PARAM)) {
                v.reset().parameter(COPY_FROM_ID_PARAM).failWithCode("only.on.create");
            }
            if (object.has(NAME_PARAM)) {
                v.reset().parameter(NAME_PARAM).value(text(object, NAME_PARAM)).notBlank().notExceedingLengthOf(150);
            }
            if (object.keySet().isEmpty()) {
                v.reset().anyOfNotNull();
            }
        }
        final Long copyFrom = this.fromApiJsonHelper.extractLongNamed(COPY_FROM_ID_PARAM, object);
        v.reset().parameter(COPY_FROM_ID_PARAM).value(copyFrom).ignoreIfNull().longGreaterThanZero();
        v.reset().parameter(DESCRIPTION_PARAM).value(text(object, DESCRIPTION_PARAM)).ignoreIfNull().notExceedingLengthOf(1000);
        final List<RatioConfigParser.RatioSpec> ratios = object.has(RATIOS_PARAM)
                ? RatioConfigParser.ratios(this.fromApiJsonHelper, object.get(RATIOS_PARAM), v)
                : null;
        final List<RatioConfigParser.AssetClassSpec> assetClasses = object.has(ASSET_CLASSES_PARAM)
                ? RatioConfigParser.assetClasses(this.fromApiJsonHelper, object.get(ASSET_CLASSES_PARAM), v)
                : null;
        final Boolean isDefault = object.has(IS_DEFAULT_PARAM) ? this.fromApiJsonHelper.extractBooleanNamed(IS_DEFAULT_PARAM, object)
                : null;
        if (create && isDefault != null) {
            v.reset().parameter(IS_DEFAULT_PARAM).failWithCode("only.for.active.rule.sets");
        }
        List<Bucket> buckets = null;
        if (object.has(BUCKETS_PARAM)) {
            buckets = buckets(object.get(BUCKETS_PARAM), v);
        }
        throwIfAny(errors);
        if (buckets != null) {
            final String problem = LadderBuilder.checkBuckets(buckets);
            if (problem != null) {
                v.reset().parameter(BUCKETS_PARAM).failWithCode("invalid", problem);
                throwIfAny(errors);
            }
        }
        return new RuleSetRequest(Set.copyOf(object.keySet()), code, text(object, NAME_PARAM), effective, text(object, DESCRIPTION_PARAM),
                isDefault, buckets, copyFrom, ratios, assetClasses);
    }

    private List<Bucket> buckets(final JsonElement element, final DataValidatorBuilder v) {
        final List<Bucket> buckets = new ArrayList<>();
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().isEmpty()) {
            v.reset().parameter(BUCKETS_PARAM).failWithCode("must.be.a.non.empty.array");
            return buckets;
        }
        final JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            final String prefix = BUCKETS_PARAM + "[" + i + "].";
            if (!array.get(i).isJsonObject()) {
                v.reset().parameter(BUCKETS_PARAM + "[" + i + "]").failWithCode("not.an.object");
                continue;
            }
            final JsonObject item = array.get(i).getAsJsonObject();
            this.fromApiJsonHelper.checkForUnsupportedParameters(item, BUCKET_PARAMS);
            final String code = upper(text(item, CODE_PARAM));
            final Integer from = integer(item, FROM_DAY_PARAM, v, prefix);
            final Integer to = integer(item, TO_DAY_PARAM, v, prefix);
            v.reset().parameter(prefix + CODE_PARAM).value(code).notBlank().matchesRegularExpression(BUCKET_CODE_PATTERN,
                    "A bucket code may hold letters, digits, - and _ only, 20 at most");
            v.reset().parameter(prefix + LABEL_PARAM).value(text(item, LABEL_PARAM)).notBlank().notExceedingLengthOf(100);
            v.reset().parameter(prefix + FROM_DAY_PARAM).value(from).notNull().integerZeroOrGreater();
            v.reset().parameter(prefix + TO_DAY_PARAM).value(to).ignoreIfNull().integerZeroOrGreater();
            buckets.add(new Bucket(i + 1, code, text(item, LABEL_PARAM), from == null ? -1 : from, to));
        }
        return buckets;
    }

    // ---------- assumption sets ----------

    public AssumptionSetRequest validateAssumptionSet(final String json, final boolean create) {
        final JsonObject object = parse(json, ASSUMPTION_SET_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_ASSUMPTION_SET_RESOURCE_NAME);
        final String code = upper(text(object, CODE_PARAM));
        final LocalDate effective = date(object, EFFECTIVE_DATE_PARAM, v);
        if (create) {
            v.reset().parameter(CODE_PARAM).value(code).notBlank().matchesRegularExpression(CODE_PATTERN,
                    "The parameter `code` may hold letters, digits, - and _ only, 50 at most");
            v.reset().parameter(NAME_PARAM).value(text(object, NAME_PARAM)).notBlank().notExceedingLengthOf(150);
            v.reset().parameter(EFFECTIVE_DATE_PARAM).value(effective).notNull();
        } else {
            if (object.has(CODE_PARAM)) {
                v.reset().parameter(CODE_PARAM).failWithCode("cannot.be.changed");
            }
            if (object.has(NAME_PARAM)) {
                v.reset().parameter(NAME_PARAM).value(text(object, NAME_PARAM)).notBlank().notExceedingLengthOf(150);
            }
            if (object.keySet().isEmpty()) {
                v.reset().anyOfNotNull();
            }
        }
        v.reset().parameter(DESCRIPTION_PARAM).value(text(object, DESCRIPTION_PARAM)).ignoreIfNull().notExceedingLengthOf(1000);
        List<Assumptions.Row> rows = create ? List.of() : null;
        if (object.has(ASSUMPTIONS_PARAM)) {
            rows = assumptions(object.get(ASSUMPTIONS_PARAM), v);
        }
        throwIfAny(errors);
        if (rows != null) {
            final String problem = checkAssumptions(rows);
            if (problem != null) {
                v.reset().parameter(ASSUMPTIONS_PARAM).failWithCode("invalid", problem);
                throwIfAny(errors);
            }
        }
        return new AssumptionSetRequest(Set.copyOf(object.keySet()), code, text(object, NAME_PARAM), effective,
                text(object, DESCRIPTION_PARAM), rows);
    }

    private List<Assumptions.Row> assumptions(final JsonElement element, final DataValidatorBuilder v) {
        final List<Assumptions.Row> rows = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            v.reset().parameter(ASSUMPTIONS_PARAM).failWithCode("must.be.an.array");
            return rows;
        }
        final JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            final String prefix = ASSUMPTIONS_PARAM + "[" + i + "].";
            if (!array.get(i).isJsonObject()) {
                v.reset().parameter(ASSUMPTIONS_PARAM + "[" + i + "]").failWithCode("not.an.object");
                continue;
            }
            final JsonObject item = array.get(i).getAsJsonObject();
            this.fromApiJsonHelper.checkForUnsupportedParameters(item, ASSUMPTION_PARAMS);
            final String type = upper(text(item, TYPE_PARAM));
            final String source = upper(text(item, SOURCE_PARAM));
            final Long product = this.fromApiJsonHelper.extractLongNamed(PRODUCT_ID_PARAM, item);
            final String bucket = upper(text(item, BUCKET_CODE_PARAM));
            final Integer minDays = integer(item, MIN_DAYS_PARAM, v, prefix);
            final Integer maxDays = integer(item, MAX_DAYS_PARAM, v, prefix);
            final BigDecimal rate = amount(item, RATE_PARAM, v, prefix + RATE_PARAM);
            v.reset().parameter(prefix + TYPE_PARAM).value(type).notBlank().isOneOfTheseStringValues(names(AssumptionType.values()));
            v.reset().parameter(prefix + SOURCE_PARAM).value(source).notBlank().isOneOfTheseStringValues(names(LadderSourceCode.values()));
            v.reset().parameter(prefix + PRODUCT_ID_PARAM).value(product).ignoreIfNull().longGreaterThanZero();
            v.reset().parameter(prefix + RATE_PARAM).value(rate).notNull().zeroOrPositiveAmount();
            v.reset().parameter(prefix + MIN_DAYS_PARAM).value(minDays).ignoreIfNull().integerZeroOrGreater();
            v.reset().parameter(prefix + MAX_DAYS_PARAM).value(maxDays).ignoreIfNull().integerZeroOrGreater();
            if (type == null || source == null || Arrays.stream(AssumptionType.values()).noneMatch(t -> t.name().equals(type))
                    || Arrays.stream(LadderSourceCode.values()).noneMatch(s -> s.name().equals(source))) {
                continue;
            }
            final AssumptionType assumptionType = AssumptionType.valueOf(type);
            final LadderSourceCode sourceCode = LadderSourceCode.valueOf(source);
            if (!SOURCES.get(assumptionType).contains(sourceCode)) {
                v.reset().parameter(prefix + SOURCE_PARAM).value(source).failWithCode("not.for.this.type", assumptionType.name(),
                        SOURCES.get(assumptionType));
            }
            final BigDecimal max = assumptionType == AssumptionType.FLOAT_DEMAND ? FLOAT_SCALE_MAX : HUNDRED;
            v.reset().parameter(prefix + RATE_PARAM).value(rate).ignoreIfNull().notGreaterThanMax(max);
            if (assumptionType == AssumptionType.RUN_OFF) {
                v.reset().parameter(prefix + BUCKET_CODE_PARAM).value(bucket).notBlank();
            } else if (bucket != null) {
                v.reset().parameter(prefix + BUCKET_CODE_PARAM).value(bucket).failWithCode("only.for.run.off");
            }
            if (assumptionType == AssumptionType.ARREARS_HAIRCUT) {
                v.reset().parameter(prefix + MIN_DAYS_PARAM).value(minDays).notNull();
                if (minDays != null && maxDays != null && maxDays < minDays) {
                    v.reset().parameter(prefix + MAX_DAYS_PARAM).value(maxDays).failWithCode("before.min.days", minDays);
                }
            } else if (assumptionType == AssumptionType.FLOAT_DEMAND) {
                v.reset().parameter(prefix + MIN_DAYS_PARAM).value(minDays).notNull().inMinMaxRange(1, 366);
                if (maxDays != null) {
                    v.reset().parameter(prefix + MAX_DAYS_PARAM).value(maxDays).failWithCode("only.for.arrears.haircut");
                }
            } else if (minDays != null || maxDays != null) {
                v.reset().parameter(prefix + MIN_DAYS_PARAM).value(minDays).failWithCode("only.for.arrears.haircut.or.float.demand");
            }
            rows.add(new Assumptions.Row(assumptionType, sourceCode, product, bucket, minDays, maxDays, rate));
        }
        return rows;
    }

    /**
     * Across the rows: no duplicates, arrears bands of a product do not overlap, run-off of a product adds up to 100%
     * at most.
     *
     * @return the problem, or null
     */
    public static String checkAssumptions(final List<Assumptions.Row> rows) {
        final Set<String> keys = new HashSet<>();
        for (final Assumptions.Row row : rows) {
            final String key = row.type() + "|" + row.source() + "|" + row.productId() + "|" + row.bucketCode() + "|"
                    + (row.type() == AssumptionType.ARREARS_HAIRCUT ? row.minDays() : "");
            if (!keys.add(key)) {
                return "two " + row.type() + " rows for source " + row.source()
                        + (row.productId() == null ? "" : ", product " + row.productId())
                        + (row.bucketCode() == null ? "" : ", bucket " + row.bucketCode());
            }
        }
        final Map<String, List<Assumptions.Row>> bands = new HashMap<>();
        final Map<String, BigDecimal> runOff = new HashMap<>();
        for (final Assumptions.Row row : rows) {
            if (row.type() == AssumptionType.ARREARS_HAIRCUT) {
                bands.computeIfAbsent(String.valueOf(row.productId()), k -> new ArrayList<>()).add(row);
            } else if (row.type() == AssumptionType.RUN_OFF) {
                runOff.merge(String.valueOf(row.productId()), row.rate(), BigDecimal::add);
            } else if (row.type() == AssumptionType.FLOAT_DEMAND || row.type() == AssumptionType.CORE_SHARE
                    || row.type() == AssumptionType.PREPAYMENT || row.type() == AssumptionType.DRAWDOWN) {
                if (rows.stream()
                        .filter(r -> r.type() == row.type() && r.source() == row.source() && Objects.equals(r.productId(), row.productId()))
                        .count() > 1) {
                    return "two " + row.type() + " rows for the same product";
                }
            }
        }
        for (final Map.Entry<String, List<Assumptions.Row>> product : bands.entrySet()) {
            final List<Assumptions.Row> list = new ArrayList<>(product.getValue());
            list.sort((a, b) -> Integer.compare(a.minDays(), b.minDays()));
            for (int i = 1; i < list.size(); i++) {
                final Assumptions.Row previous = list.get(i - 1);
                if (previous.maxDays() == null || previous.maxDays() >= list.get(i).minDays()) {
                    return "arrears haircut bands overlap from " + list.get(i).minDays() + " days"
                            + ("null".equals(product.getKey()) ? "" : " for product " + product.getKey());
                }
            }
        }
        for (final Map.Entry<String, BigDecimal> product : runOff.entrySet()) {
            if (product.getValue().compareTo(HUNDRED) > 0) {
                return "run-off rates add up to " + product.getValue().toPlainString() + "%"
                        + ("null".equals(product.getKey()) ? "" : " for product " + product.getKey()) + ", above 100%";
            }
        }
        return null;
    }

    // ---------- ladder items ----------

    public LadderItemRequest validateLadderItem(final String json, final boolean create) {
        final JsonObject object = parse(json, LADDER_ITEM_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_LADDER_ITEM_RESOURCE_NAME);
        final String direction = upper(text(object, DIRECTION_PARAM));
        final BigDecimal amount = amount(object, AMOUNT_PARAM, v, AMOUNT_PARAM);
        final String currency = upper(text(object, CURRENCY_CODE_PARAM));
        final Long office = this.fromApiJsonHelper.extractLongNamed(OFFICE_ID_PARAM, object);
        final LocalDate oneOff = date(object, ONE_OFF_DATE_PARAM, v);
        final Integer day = integer(object, DAY_OF_MONTH_PARAM, v, "");
        final LocalDate from = date(object, ACTIVE_FROM_PARAM, v);
        final LocalDate to = date(object, ACTIVE_TO_PARAM, v);
        if (create || object.has(NAME_PARAM)) {
            v.reset().parameter(NAME_PARAM).value(text(object, NAME_PARAM)).notBlank().notExceedingLengthOf(150);
        }
        if (create || object.has(DIRECTION_PARAM)) {
            v.reset().parameter(DIRECTION_PARAM).value(direction).notBlank().isOneOfTheseStringValues(names(LadderItemDirection.values()));
        }
        if (create || object.has(AMOUNT_PARAM)) {
            v.reset().parameter(AMOUNT_PARAM).value(amount).notNull().positiveAmount();
        }
        if (create || object.has(CURRENCY_CODE_PARAM)) {
            v.reset().parameter(CURRENCY_CODE_PARAM).value(currency).notBlank().matchesRegularExpression("^[A-Z]{3}$",
                    "The parameter `currencyCode` is a three-letter code");
        }
        if (create || object.has(ACTIVE_FROM_PARAM)) {
            v.reset().parameter(ACTIVE_FROM_PARAM).value(from).notNull();
        }
        v.reset().parameter(OFFICE_ID_PARAM).value(office).ignoreIfNull().longGreaterThanZero();
        v.reset().parameter(DAY_OF_MONTH_PARAM).value(day).ignoreIfNull().inMinMaxRange(1, 31);
        if (create && (oneOff == null) == (day == null)) {
            v.reset().parameter(ONE_OFF_DATE_PARAM).failWithCode("one.off.date.or.day.of.month.required");
        }
        if (from != null && to != null && to.isBefore(from)) {
            v.reset().parameter(ACTIVE_TO_PARAM).value(to).failWithCode("before.active.from", from);
        }
        if (!create && object.keySet().isEmpty()) {
            v.reset().anyOfNotNull();
        }
        throwIfAny(errors);
        return new LadderItemRequest(Set.copyOf(object.keySet()), text(object, NAME_PARAM),
                direction == null ? null : LadderItemDirection.valueOf(direction), amount, currency, office, oneOff, day, from, to);
    }

    // ---------- runs and activation ----------

    public RunRequest validateRun(final String json) {
        final JsonObject object = parse(StringUtils.isBlank(json) ? "{}" : json, RUN_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_RUN_RESOURCE_NAME);
        final String type = upper(text(object, TYPE_PARAM));
        final String currency = upper(text(object, CURRENCY_CODE_PARAM));
        final LocalDate asOf = date(object, AS_OF_DATE_PARAM, v);
        final Long office = this.fromApiJsonHelper.extractLongNamed(OFFICE_ID_PARAM, object);
        final Long ruleSet = this.fromApiJsonHelper.extractLongNamed(RULE_SET_ID_PARAM, object);
        final Long assumptionSet = this.fromApiJsonHelper.extractLongNamed(ASSUMPTION_SET_ID_PARAM, object);
        final Long sourceRun = this.fromApiJsonHelper.extractLongNamed(SOURCE_RUN_ID_PARAM, object);
        v.reset().parameter(SOURCE_RUN_ID_PARAM).value(sourceRun).ignoreIfNull().longGreaterThanZero();
        v.reset().parameter(TYPE_PARAM).value(type).ignoreIfNull().isOneOfTheseStringValues(names(RunType.values()));
        v.reset().parameter(CURRENCY_CODE_PARAM).value(currency).notBlank().matchesRegularExpression("^[A-Z]{3}$",
                "The parameter `currencyCode` is a three-letter code");
        v.reset().parameter(OFFICE_ID_PARAM).value(office).ignoreIfNull().longGreaterThanZero();
        v.reset().parameter(RULE_SET_ID_PARAM).value(ruleSet).ignoreIfNull().longGreaterThanZero();
        v.reset().parameter(ASSUMPTION_SET_ID_PARAM).value(assumptionSet).ignoreIfNull().longGreaterThanZero();
        throwIfAny(errors);
        return new RunRequest(type == null ? RunType.LADDER : RunType.valueOf(type), asOf, currency, office, ruleSet, assumptionSet,
                sourceRun);
    }

    public void validateActivate(final String json) {
        final JsonObject object = parse(StringUtils.isBlank(json) ? "{}" : json, ACTIVATE_PARAMS);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource(TREASURY_RULE_SET_RESOURCE_NAME);
        v.reset().parameter(NOTE_PARAM).value(text(object, NOTE_PARAM)).ignoreIfNull().notExceedingLengthOf(1000);
        throwIfAny(errors);
    }

    // ---------- helpers ----------

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

    private static Integer integer(final JsonObject object, final String parameterName, final DataValidatorBuilder v, final String prefix) {
        final JsonElement value = object.get(parameterName);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            final BigDecimal number = value.getAsBigDecimal();
            return number.intValueExact();
        } catch (NumberFormatException | ArithmeticException | UnsupportedOperationException e) {
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

    private static <E extends Enum<E>> List<String> names(final E[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static void throwIfAny(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }
}
