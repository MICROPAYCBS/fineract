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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.limits.GapBasis;
import org.apache.fineract.treasury.limits.GapType;
import org.apache.fineract.treasury.limits.LimitComparison;
import org.apache.fineract.treasury.limits.LimitMetric;
import org.apache.fineract.treasury.limits.LimitScopeType;
import org.apache.fineract.treasury.limits.LimitUnit;
import org.apache.fineract.treasury.serialization.TreasuryLimitDataValidator.EvaluateRequest;
import org.apache.fineract.treasury.serialization.TreasuryLimitDataValidator.LimitRequest;
import org.junit.jupiter.api.Test;

/** Every field of a limit, the metric's rules, updates over the current limit, breach actions and evaluations. */
class TreasuryLimitDataValidatorTest {

    private final TreasuryLimitDataValidator validator = new TreasuryLimitDataValidator(new FromJsonHelper());

    @Test
    void everyFieldOfABucketGapLimit() {
        final LimitRequest r = this.validator.validateLimit("""
                {"code": "gap_on_pct", "name": "Overnight gap", "description": "Board limit", "metric": "bucket_gap", "metricKey": "on",
                 "gapType": "net", "gapBasis": "deposit_outflows", "scopeType": "office", "scopeId": 2, "currencyCode": "ugx",
                 "comparison": "min", "warningLevel": -5, "hardLevel": -10, "unit": "percent", "effectiveFrom": "16 October 2026",
                 "effectiveTo": "31 December 2026", "dateFormat": "dd MMMM yyyy", "locale": "en", "notifyRoleId": 4}
                """, null);
        assertEquals(new LimitRequest("GAP_ON_PCT", "Overnight gap", "Board limit", LimitMetric.BUCKET_GAP, "ON", GapType.NET,
                GapBasis.DEPOSIT_OUTFLOWS, null, LimitScopeType.OFFICE, 2L, "UGX", LimitComparison.MIN, BigDecimal.valueOf(-5),
                BigDecimal.valueOf(-10), LimitUnit.PERCENT, false, LocalDate.of(2026, 10, 16), LocalDate.of(2026, 12, 31), 4L), r);
    }

    @Test
    void defaultsAndOwnLevels() {
        final LimitRequest gap = this.validator.validateLimit("""
                {"code": "G", "name": "Gap", "metric": "BUCKET_GAP", "metricKey": "D2_8", "scopeType": "INSTITUTION", "currencyCode": "UGX",
                 "comparison": "MIN", "hardLevel": 0, "unit": "AMOUNT"}
                """, null);
        assertEquals(GapType.CUMULATIVE, gap.gapType());
        assertNull(gap.warningLevel());
        final LimitRequest top = this.validator.validateLimit("""
                {"code": "D", "name": "Depositor", "metric": "DEPOSITOR_CONCENTRATION", "scopeType": "INSTITUTION", "comparison": "MAX",
                 "hardLevel": 5, "unit": "PERCENT"}
                """, null);
        assertEquals(1, top.topN());
        final LimitRequest own = this.validator.validateLimit("""
                {"code": "C", "name": "Counterparties", "metric": "COUNTERPARTY_EXPOSURE", "scopeType": "COUNTERPARTY", "scopeId": 5,
                 "useOwnLevels": true}
                """, null);
        assertTrue(own.useOwnLevels());
        assertEquals(LimitUnit.PERCENT, own.unit());
        assertNull(own.comparison());
        assertEquals(LimitUnit.AMOUNT, this.validator.validateLimit("""
                {"code": "A", "name": "Accounts", "metric": "ACCOUNT_BALANCE", "scopeType": "ACCOUNT", "scopeId": 3, "useOwnLevels": true}
                """, null).unit());
    }

    @Test
    void requiredFieldsAndValues() {
        assertEquals(List.of("code", "name", "metric", "scopeType"), parameters("{}"));
        assertEquals(List.of("code"), parameters(base("\"code\": \"bad code\"")));
        assertEquals(List.of("metric"), parameters(base("\"metric\": \"FX_POSITION\"")));
        assertEquals(List.of("comparison", "hardLevel", "unit"), parameters(
                "{\"code\": \"X\", \"name\": \"X\", \"metric\": \"RATIO\", \"metricKey\": \"R\", \"scopeType\": \"INSTITUTION\"}"));
        assertEquals(List.of("hardLevel"), parameters(base("\"hardLevel\": \"high\"")));
        assertEquals(List.of("useOwnLevels"), parameters(base("\"useOwnLevels\": \"yes\"")));
        assertThrows(UnsupportedParameterException.class, () -> this.validator.validateLimit(base("\"limitValue\": 5"), null));
    }

    @Test
    void scopesUnitsAndLevelsPerMetric() {
        assertEquals(List.of("scopeType"), codes(base("\"scopeType\": \"COUNTERPARTY\", \"scopeId\": 4"), "not.allowed.for.metric"));
        assertEquals(List.of("scopeId"), codes(base("\"scopeType\": \"OFFICE\""), "scopeId.cannot.be.blank"));
        assertEquals(List.of("scopeId"), codes(base("\"scopeId\": 3"), "not.allowed.for.scope"));
        assertEquals(List.of("currencyCode"), codes(base("\"scopeType\": \"CURRENCY\""), "required.for.currency.scope"));
        assertEquals(List.of("unit"), codes(base("\"unit\": \"DAYS\""), "not.allowed.for.metric"));
        assertEquals(List.of("unit"), codes(base("\"unit\": \"AMOUNT\", \"currencyCode\": \"UGX\""), "not.allowed.for.metric"));
        assertEquals(List.of("warningLevel"), codes(base("\"warningLevel\": 14"), "not.before.hard.level"));
        assertEquals(List.of("warningLevel"), codes(base("\"warningLevel\": 15"), "not.before.hard.level"));
        assertEquals(List.of("warningLevel"), codes(base("\"comparison\": \"MAX\", \"warningLevel\": 16"), "not.before.hard.level"));
        assertEquals(List.of("currencyCode"), codes(gap("\"unit\": \"AMOUNT\""), "required.for.amounts"));
        assertEquals(List.of("currencyCode"), codes(base("\"currencyCode\": \"SHILLING\""), "currencyCode"));
        assertEquals(List.of("metricKey"), codes(base("\"metricKey\": null"), "metricKey"));
        assertEquals(List.of("metricKey"), codes("{\"code\": \"D\", \"name\": \"D\", \"metric\": \"DEPOSITOR_CONCENTRATION\", "
                + "\"metricKey\": \"X\", \"scopeType\": \"INSTITUTION\", \"comparison\": \"MAX\", \"hardLevel\": 5, \"unit\": \"PERCENT\"}",
                "not.allowed.for.metric"));
        assertEquals(List.of("gapBasis"), codes(gap("\"unit\": \"PERCENT\""), "gapBasis"));
        assertEquals(List.of("gapBasis"),
                codes(gap("\"unit\": \"AMOUNT\", \"currencyCode\": \"UGX\", \"gapBasis\": \"TOTAL_OUTFLOWS\""), "only.for.percent"));
        assertEquals(List.of("gapType", "gapBasis"),
                codes(base("\"gapType\": \"NET\", \"gapBasis\": \"TOTAL_OUTFLOWS\""), "not.allowed.for.metric"));
        assertEquals(List.of("topN"), codes(base("\"topN\": 5"), "not.allowed.for.metric"));
        assertEquals(List.of("topN"), codes("{\"code\": \"D\", \"name\": \"D\", \"metric\": \"FUNDING_CONCENTRATION\", \"topN\": 0, "
                + "\"scopeType\": \"INSTITUTION\", \"comparison\": \"MAX\", \"hardLevel\": 40, \"unit\": \"PERCENT\"}", "topN"));
        assertEquals(List.of("useOwnLevels"), codes("{\"code\": \"D\", \"name\": \"D\", \"metric\": \"FUNDING_CONCENTRATION\", "
                + "\"scopeType\": \"INSTITUTION\", \"useOwnLevels\": true}", "not.supported.for.metric"));
        assertEquals(List.of("comparison", "hardLevel", "unit"), codes(base("\"useOwnLevels\": true"), "not.allowed.with.own.levels"));
        assertEquals(List.of("effectiveTo"),
                codes(base("\"effectiveFrom\": \"2026-10-16\", \"effectiveTo\": \"2026-10-15\""), "before.effective.from"));
        assertEquals(List.of("notifyRoleId"), codes(base("\"notifyRoleId\": 0"), "notifyRoleId"));
        assertEquals(List.of("description"), codes(base("\"description\": \"" + "x".repeat(1001) + "\""), "description"));
    }

    @Test
    void anUpdateIsLaidOverTheLimitAsItIs() {
        final JsonObject current = JsonParser.parseString(base("\"warningLevel\": 17, \"notifyRoleId\": 4")).getAsJsonObject();
        final LimitRequest r = this.validator.validateLimit("{\"hardLevel\": 12, \"warningLevel\": null, \"name\": \"Liquid assets\"}",
                current);
        assertEquals(0, BigDecimal.valueOf(12).compareTo(r.hardLevel()));
        assertNull(r.warningLevel(), "a null clears");
        assertEquals("Liquid assets", r.name());
        assertEquals(4L, r.notifyRoleId(), "kept");
        assertEquals("LIQ", r.code());
        assertEquals(List.of("code"),
                assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateLimit("{\"code\": \"OTHER\"}", current))
                        .getErrors().stream().map(ApiParameterError::getParameterName).toList());
        assertEquals("LIQ", this.validator.validateLimit("{\"code\": \"liq\"}", current).code(), "the same code");
        assertEquals(List.of("warningLevel"),
                assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateLimit("{\"hardLevel\": 20}", current))
                        .getErrors().stream().map(ApiParameterError::getParameterName).toList(),
                "the merged limit is checked");
    }

    @Test
    void breachActionsAndEvaluations() {
        assertNull(this.validator.validateBreachAction("{}", false));
        assertEquals("Seen", this.validator.validateBreachAction("{\"comment\": \" Seen \"}", false));
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateBreachAction("{}", true));
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateBreachAction("{\"comment\": \"" + "x".repeat(2001) + "\"}", true));
        assertThrows(UnsupportedParameterException.class, () -> this.validator.validateBreachAction("{\"reason\": \"x\"}", true));
        assertEquals(new EvaluateRequest(LocalDate.of(2026, 10, 16), "UGX", 1L, null),
                this.validator.validateEvaluate("{\"asOfDate\": \"2026-10-16\", \"currencyCode\": \"ugx\", \"officeId\": 1}"));
        assertEquals(new EvaluateRequest(null, null, null, 13L), this.validator.validateEvaluate("{\"runId\": 13}"));
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateEvaluate("{}"), "a currency or a run");
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateEvaluate("{\"runId\": 0}"));
        assertEquals("note", this.validator.validateNote("{\"note\": \"note\"}"));
        assertNull(this.validator.validateNote("{}"));
    }

    private static String base(final String extra) {
        return "{\"code\": \"LIQ\", \"name\": \"Liquid assets to deposits\", \"metric\": \"RATIO\", \"metricKey\": \"LIQUID_ASSETS_TO_DEPOSITS\", "
                + "\"scopeType\": \"INSTITUTION\", \"comparison\": \"MIN\", \"hardLevel\": 15, \"unit\": \"PERCENT\""
                + (extra.isEmpty() ? "" : ", " + extra) + "}";
    }

    private static String gap(final String extra) {
        return "{\"code\": \"G\", \"name\": \"Gap\", \"metric\": \"BUCKET_GAP\", \"metricKey\": \"ON\", \"scopeType\": \"INSTITUTION\", "
                + "\"comparison\": \"MIN\", \"hardLevel\": 0, " + extra + "}";
    }

    private List<String> parameters(final String json) {
        return assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateLimit(json, null)).getErrors().stream()
                .map(ApiParameterError::getParameterName).distinct().toList();
    }

    /** The parameters in error, each error code containing the fragment. */
    private List<String> codes(final String json, final String fragment) {
        final List<ApiParameterError> errors = assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateLimit(json, null)).getErrors();
        assertTrue(errors.stream().allMatch(e -> e.getUserMessageGlobalisationCode().contains(fragment)),
                () -> errors.stream().map(ApiParameterError::getUserMessageGlobalisationCode).toList().toString());
        return errors.stream().map(ApiParameterError::getParameterName).distinct().toList();
    }
}
