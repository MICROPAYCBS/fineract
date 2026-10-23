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

import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ASSET_CLASS_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.COMPONENT_PARAMS;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.RATIO_PARAMS;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.domain.TreasuryAccountType;
import org.apache.fineract.treasury.liquidity.ComponentType;
import org.apache.fineract.treasury.liquidity.LadderMetric;
import org.apache.fineract.treasury.liquidity.RatioDirection;
import org.apache.fineract.treasury.liquidity.RatioPart;

/** Reads and checks the {@code ratios} and {@code assetClasses} of a rule set version. */
public final class RatioConfigParser {

    private static final String CODE_PATTERN = "^[A-Za-z0-9_-]{1,50}$";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private RatioConfigParser() {}

    public record ComponentSpec(RatioPart part, ComponentType type, String label, Long glAccountId, String glCodePrefix, String glCodeFrom,
            String glCodeTo, String assetClass, LadderMetric ladderMetric, Integer metricDays, int sign, BigDecimal weight) {
    }

    public record RatioSpec(String code, String name, RatioDirection direction, BigDecimal limit, BigDecimal warning, boolean inReturn,
            String numeratorLabel, String denominatorLabel, String description, List<ComponentSpec> components) {
    }

    /** {@code classifications}: the deals' accounting classifications counted, all when empty. */
    public record AssetClassSpec(String code, String label, boolean liquid, List<String> accountTypes, List<String> instrumentKinds,
            List<Long> dealTypeIds, Integer maxMaturityDays, BigDecimal haircut, List<String> classifications) {

        public AssetClassSpec(final String code, final String label, final boolean liquid, final List<String> accountTypes,
                final List<String> instrumentKinds, final List<Long> dealTypeIds, final Integer maxMaturityDays, final BigDecimal haircut) {
            this(code, label, liquid, accountTypes, instrumentKinds, dealTypeIds, maxMaturityDays, haircut, List.of());
        }
    }

    static List<RatioSpec> ratios(final FromJsonHelper helper, final JsonElement element, final DataValidatorBuilder v) {
        final List<RatioSpec> ratios = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            v.reset().parameter("ratios").failWithCode("must.be.an.array");
            return ratios;
        }
        final Set<String> codes = new HashSet<>();
        final JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            final String p = "ratios[" + i + "].";
            if (!array.get(i).isJsonObject()) {
                v.reset().parameter("ratios[" + i + "]").failWithCode("not.an.object");
                continue;
            }
            final JsonObject o = array.get(i).getAsJsonObject();
            helper.checkForUnsupportedParameters(o, RATIO_PARAMS);
            final String code = upper(text(o, "code"));
            final String direction = upper(text(o, "direction"));
            final BigDecimal limit = number(o, "limit", v, p);
            final BigDecimal warning = number(o, "warning", v, p);
            v.reset().parameter(p + "code").value(code).notBlank().matchesRegularExpression(CODE_PATTERN,
                    "A ratio code may hold letters, digits, - and _ only, 50 at most");
            if (code != null && !codes.add(code)) {
                v.reset().parameter(p + "code").value(code).failWithCode("duplicate");
            }
            v.reset().parameter(p + "name").value(text(o, "name")).notBlank().notExceedingLengthOf(150);
            v.reset().parameter(p + "direction").value(direction).notBlank().isOneOfTheseStringValues(names(RatioDirection.values()));
            v.reset().parameter(p + "limit").value(limit).notNull().zeroOrPositiveAmount();
            v.reset().parameter(p + "warning").value(warning).ignoreIfNull().zeroOrPositiveAmount();
            v.reset().parameter(p + "numeratorLabel").value(text(o, "numeratorLabel")).ignoreIfNull().notExceedingLengthOf(150);
            v.reset().parameter(p + "denominatorLabel").value(text(o, "denominatorLabel")).ignoreIfNull().notExceedingLengthOf(150);
            v.reset().parameter(p + "description").value(text(o, "description")).ignoreIfNull().notExceedingLengthOf(1000);
            RatioDirection parsed = null;
            if ("MIN".equals(direction) || "MAX".equals(direction)) {
                parsed = RatioDirection.valueOf(direction);
                if (limit != null && warning != null
                        && (parsed == RatioDirection.MIN ? warning.compareTo(limit) < 0 : warning.compareTo(limit) > 0)) {
                    v.reset().parameter(p + "warning").value(warning)
                            .failWithCode(parsed == RatioDirection.MIN ? "below.limit" : "above.limit", limit);
                }
            }
            final List<ComponentSpec> components = components(helper, o.get("components"), v, p);
            if (components.stream().noneMatch(c -> c.part() == RatioPart.NUMERATOR)
                    || components.stream().noneMatch(c -> c.part() == RatioPart.DENOMINATOR)) {
                v.reset().parameter(p + "components").failWithCode("numerator.and.denominator.required");
            }
            final JsonElement inReturn = o.get("inReturn");
            ratios.add(new RatioSpec(code, text(o, "name"), parsed, limit, warning,
                    inReturn != null && !inReturn.isJsonNull() && inReturn.getAsBoolean(), text(o, "numeratorLabel"),
                    text(o, "denominatorLabel"), text(o, "description"), components));
        }
        return ratios;
    }

    private static List<ComponentSpec> components(final FromJsonHelper helper, final JsonElement element, final DataValidatorBuilder v,
            final String ratio) {
        final List<ComponentSpec> components = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            v.reset().parameter(ratio + "components").failWithCode("must.be.an.array");
            return components;
        }
        final JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            final String p = ratio + "components[" + i + "].";
            if (!array.get(i).isJsonObject()) {
                v.reset().parameter(ratio + "components[" + i + "]").failWithCode("not.an.object");
                continue;
            }
            final JsonObject o = array.get(i).getAsJsonObject();
            helper.checkForUnsupportedParameters(o, COMPONENT_PARAMS);
            final String part = upper(text(o, "part"));
            final String type = upper(text(o, "type"));
            final Long glAccountId = helper.extractLongNamed("glAccountId", o);
            final String prefix = text(o, "glCodePrefix");
            final String from = text(o, "glCodeFrom");
            final String to = text(o, "glCodeTo");
            final String assetClass = upper(text(o, "assetClass"));
            final String metric = upper(text(o, "ladderMetric"));
            final BigDecimal days = number(o, "metricDays", v, p);
            final BigDecimal sign = number(o, "sign", v, p);
            final BigDecimal weight = number(o, "weight", v, p);
            v.reset().parameter(p + "part").value(part).notBlank().isOneOfTheseStringValues(names(RatioPart.values()));
            v.reset().parameter(p + "type").value(type).notBlank().isOneOfTheseStringValues(names(ComponentType.values()));
            v.reset().parameter(p + "label").value(text(o, "label")).notBlank().notExceedingLengthOf(150);
            if (sign != null && sign.intValue() != 1 && sign.intValue() != -1) {
                v.reset().parameter(p + "sign").value(sign).failWithCode("must.be.1.or.minus.1");
            }
            v.reset().parameter(p + "weight").value(weight).ignoreIfNull().zeroOrPositiveAmount().notGreaterThanMax(HUNDRED);
            ComponentType parsed = null;
            if (type != null && Arrays.stream(ComponentType.values()).anyMatch(t -> t.name().equals(type))) {
                parsed = ComponentType.valueOf(type);
                final int glWays = (glAccountId != null ? 1 : 0) + (prefix != null ? 1 : 0) + (from != null || to != null ? 1 : 0);
                if (parsed == ComponentType.GL_ACCOUNT) {
                    if (glWays != 1) {
                        v.reset().parameter(p + "glAccountId").failWithCode("one.of.gl.account.id.prefix.or.range.required");
                    } else if ((from == null) != (to == null)) {
                        v.reset().parameter(p + "glCodeTo").failWithCode("range.needs.from.and.to");
                    }
                } else if (glWays > 0) {
                    v.reset().parameter(p + "glAccountId").failWithCode("only.for.gl.account.components");
                }
                if (parsed == ComponentType.ASSET_CLASS) {
                    v.reset().parameter(p + "assetClass").value(assetClass).notBlank();
                } else if (assetClass != null) {
                    v.reset().parameter(p + "assetClass").failWithCode("only.for.asset.class.components");
                }
                if (parsed == ComponentType.LADDER_METRIC) {
                    v.reset().parameter(p + "ladderMetric").value(metric).notBlank().isOneOfTheseStringValues(names(LadderMetric.values()));
                    v.reset().parameter(p + "metricDays").value(days == null ? null : days.intValue()).ignoreIfNull().inMinMaxRange(1, 366);
                } else if (metric != null || days != null) {
                    v.reset().parameter(p + "ladderMetric").failWithCode("only.for.ladder.metric.components");
                }
            }
            final boolean metricOk = metric != null && Arrays.stream(LadderMetric.values()).anyMatch(m -> m.name().equals(metric));
            components.add(new ComponentSpec(
                    part != null && Arrays.stream(RatioPart.values()).anyMatch(x -> x.name().equals(part)) ? RatioPart.valueOf(part) : null,
                    parsed, text(o, "label"), glAccountId, prefix, from, to, assetClass, metricOk ? LadderMetric.valueOf(metric) : null,
                    parsed == ComponentType.LADDER_METRIC ? (days == null ? 30 : days.intValue()) : null,
                    sign == null ? 1 : sign.intValue(), weight == null ? HUNDRED : weight));
        }
        return components;
    }

    static List<AssetClassSpec> assetClasses(final FromJsonHelper helper, final JsonElement element, final DataValidatorBuilder v) {
        final List<AssetClassSpec> classes = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            v.reset().parameter("assetClasses").failWithCode("must.be.an.array");
            return classes;
        }
        final Set<String> codes = new HashSet<>();
        final Set<String> claimed = new HashSet<>();
        final List<String> assetKinds = Arrays.stream(InstrumentKind.values()).filter(InstrumentKind::isAsset).map(Enum::name).toList();
        final JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            final String p = "assetClasses[" + i + "].";
            if (!array.get(i).isJsonObject()) {
                v.reset().parameter("assetClasses[" + i + "]").failWithCode("not.an.object");
                continue;
            }
            final JsonObject o = array.get(i).getAsJsonObject();
            helper.checkForUnsupportedParameters(o, ASSET_CLASS_PARAMS);
            final String code = upper(text(o, "code"));
            final BigDecimal maturity = number(o, "maxMaturityDays", v, p);
            final BigDecimal haircut = number(o, "haircut", v, p);
            v.reset().parameter(p + "code").value(code).notBlank().matchesRegularExpression(CODE_PATTERN,
                    "An asset class code may hold letters, digits, - and _ only, 50 at most");
            if (code != null && !codes.add(code)) {
                v.reset().parameter(p + "code").value(code).failWithCode("duplicate");
            }
            v.reset().parameter(p + "label").value(text(o, "label")).notBlank().notExceedingLengthOf(150);
            v.reset().parameter(p + "maxMaturityDays").value(maturity == null ? null : maturity.intValue()).ignoreIfNull()
                    .integerZeroOrGreater();
            v.reset().parameter(p + "haircut").value(haircut).ignoreIfNull().zeroOrPositiveAmount().notGreaterThanMax(HUNDRED);
            final List<String> accountTypes = strings(o.get("accountTypes"));
            final List<String> kinds = strings(o.get("instrumentKinds"));
            final List<String> classifications = strings(o.get("classifications"));
            for (final String classification : classifications) {
                if (Arrays.stream(DealClassification.values()).noneMatch(c -> c.name().equals(classification))) {
                    v.reset().parameter(p + "classifications").value(classification).failWithCode("unknown");
                }
            }
            // a kind may sit in two classes when they split it by classification (bonds held for trading and available
            // for sale)
            final List<String> kindKeys = classifications.isEmpty() ? Arrays.stream(DealClassification.values()).map(Enum::name).toList()
                    : classifications;
            final List<Long> dealTypes = new ArrayList<>();
            for (final String accountType : accountTypes) {
                if (Arrays.stream(TreasuryAccountType.values()).noneMatch(t -> t.name().equals(accountType))) {
                    v.reset().parameter(p + "accountTypes").value(accountType).failWithCode("unknown");
                } else if (!claimed.add("A:" + accountType)) {
                    v.reset().parameter(p + "accountTypes").value(accountType).failWithCode("in.two.classes");
                }
            }
            for (final String kind : kinds) {
                if (!assetKinds.contains(kind)) {
                    v.reset().parameter(p + "instrumentKinds").value(kind).failWithCode("not.an.asset.kind");
                } else if (!kindKeys.stream().allMatch(c -> claimed.add("K:" + kind + ":" + c))) {
                    v.reset().parameter(p + "instrumentKinds").value(kind).failWithCode("in.two.classes");
                }
            }
            final JsonElement ids = o.get("dealTypeIds");
            if (ids != null && ids.isJsonArray()) {
                for (final JsonElement id : ids.getAsJsonArray()) {
                    try {
                        dealTypes.add(id.getAsLong());
                    } catch (RuntimeException e) {
                        v.reset().parameter(p + "dealTypeIds").value(id.toString()).failWithCode("not.a.number");
                    }
                }
            }
            if (accountTypes.isEmpty() && kinds.isEmpty() && dealTypes.isEmpty()) {
                v.reset().parameter(p + "accountTypes").failWithCode("account.types.instrument.kinds.or.deal.types.required");
            }
            final JsonElement liquid = o.get("liquid");
            classes.add(new AssetClassSpec(code, text(o, "label"), liquid == null || liquid.isJsonNull() || liquid.getAsBoolean(),
                    accountTypes, kinds, dealTypes, maturity == null ? null : maturity.intValue(),
                    haircut == null ? BigDecimal.ZERO : haircut, classifications));
        }
        return classes;
    }

    private static List<String> strings(final JsonElement element) {
        final List<String> values = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            for (final JsonElement item : element.getAsJsonArray()) {
                if (!item.isJsonNull()) {
                    values.add(item.getAsString().trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return values;
    }

    private static BigDecimal number(final JsonObject object, final String name, final DataValidatorBuilder v, final String prefix) {
        final JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsBigDecimal();
        } catch (NumberFormatException | UnsupportedOperationException e) {
            v.reset().parameter(prefix + name).value(value.toString()).failWithCode("not.a.number");
            return null;
        }
    }

    private static String text(final JsonObject object, final String name) {
        final JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? null : StringUtils.trimToNull(value.getAsString());
    }

    private static String upper(final String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> List<String> names(final E[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
