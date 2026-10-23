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

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.liquidity.ComponentType;
import org.apache.fineract.treasury.liquidity.LadderMetric;
import org.apache.fineract.treasury.liquidity.RatioDirection;
import org.apache.fineract.treasury.liquidity.RatioPart;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator.RuleSetRequest;
import org.junit.jupiter.api.Test;

/** Ratios, components and asset classes of a rule set version, and copying from another version. */
class RatioConfigValidatorTest {

    private final TreasuryLiquidityDataValidator validator = new TreasuryLiquidityDataValidator(new FromJsonHelper());

    @Test
    void ratiosAndComponentsReadEveryField() {
        final RuleSetRequest request = this.validator.validateRuleSet(
                """
                        {"ratios": [{"code": "lcr", "name": "LCR", "direction": "min", "limit": 100, "warning": 110, "inReturn": true,
                          "numeratorLabel": "HQLA", "denominatorLabel": "Net outflows", "description": "Simplified",
                          "components": [
                            {"part": "numerator", "type": "asset_class", "label": "Bills", "assetClass": "tbill"},
                            {"part": "NUMERATOR", "type": "GL_ACCOUNT", "label": "Vault", "glAccountId": 12, "sign": -1, "weight": 50},
                            {"part": "NUMERATOR", "type": "GL_ACCOUNT", "label": "Deposits", "glCodePrefix": "2000"},
                            {"part": "NUMERATOR", "type": "GL_ACCOUNT", "label": "Range", "glCodeFrom": "200001", "glCodeTo": "200009"},
                            {"part": "DENOMINATOR", "type": "LADDER_METRIC", "label": "Outflows", "ladderMetric": "net_outflows", "metricDays": 30}]}]}
                        """,
                false);
        final RatioConfigParser.RatioSpec ratio = request.ratios().getFirst();
        assertEquals("LCR", ratio.code());
        assertEquals(RatioDirection.MIN, ratio.direction());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(ratio.limit()));
        assertEquals(0, BigDecimal.valueOf(110).compareTo(ratio.warning()));
        assertTrue(ratio.inReturn());
        assertEquals("HQLA", ratio.numeratorLabel());
        assertEquals("Net outflows", ratio.denominatorLabel());
        assertEquals("Simplified", ratio.description());
        final List<RatioConfigParser.ComponentSpec> c = ratio.components();
        assertEquals(new RatioConfigParser.ComponentSpec(RatioPart.NUMERATOR, ComponentType.ASSET_CLASS, "Bills", null, null, null, null,
                "TBILL", null, null, 1, BigDecimal.valueOf(100)), c.get(0));
        assertEquals(12L, c.get(1).glAccountId());
        assertEquals(-1, c.get(1).sign());
        assertEquals(0, BigDecimal.valueOf(50).compareTo(c.get(1).weight()));
        assertEquals("2000", c.get(2).glCodePrefix());
        assertEquals("200001", c.get(3).glCodeFrom());
        assertEquals("200009", c.get(3).glCodeTo());
        assertEquals(LadderMetric.NET_OUTFLOWS, c.get(4).ladderMetric());
        assertEquals(30, c.get(4).metricDays());
        assertNull(request.assetClasses());
    }

    @Test
    void assetClassesReadEveryField() {
        final RuleSetRequest request = this.validator.validateRuleSet(
                """
                        {"assetClasses": [{"code": "tbill", "label": "Bills", "liquid": true, "instrumentKinds": ["treasury_bill"], "dealTypeIds": [4],
                          "maxMaturityDays": 91, "haircut": 0},
                          {"code": "BANK", "label": "Banks", "accountTypes": ["BANK"], "liquid": false, "haircut": 5}]}
                        """,
                false);
        assertEquals(List.of(
                new RatioConfigParser.AssetClassSpec("TBILL", "Bills", true, List.of(), List.of("TREASURY_BILL"), List.of(4L), 91,
                        new BigDecimal("0")),
                new RatioConfigParser.AssetClassSpec("BANK", "Banks", false, List.of("BANK"), List.of(), List.of(), null,
                        new BigDecimal("5"))),
                request.assetClasses());
    }

    @Test
    void ratioRulesAreChecked() {
        assertEquals(List.of("ratios[0].code", "ratios[0].name", "ratios[0].direction", "ratios[0].limit", "ratios[0].components"),
                parameters("{\"ratios\": [{\"components\": []}]}"));
        assertEquals(List.of("ratios[0].warning"), parameters(ratio("\"direction\": \"MIN\", \"limit\": 15, \"warning\": 10")));
        assertEquals(List.of("ratios[0].warning"), parameters(ratio("\"direction\": \"MAX\", \"limit\": 80, \"warning\": 85")));
        assertEquals(List.of("ratios[0].components"),
                parameters("{\"ratios\": [{\"code\": \"X\", \"name\": \"X\", \"direction\": \"MIN\", \"limit\": 1,"
                        + " \"components\": [{\"part\": \"NUMERATOR\", \"type\": \"ASSET_CLASS\", \"label\": \"A\", \"assetClass\": \"CASH\"}]}]}"));
        assertEquals(List.of("ratios[1].code"), parameters("{\"ratios\": [" + oneRatio("X") + ", " + oneRatio("x") + "]}"));
    }

    @Test
    void componentRulesAreChecked() {
        assertEquals(List.of("ratios[0].components[0].glAccountId"),
                component("{\"part\": \"NUMERATOR\", \"type\": \"GL_ACCOUNT\", \"label\": \"G\"}"));
        assertEquals(List.of("ratios[0].components[0].glAccountId"), component(
                "{\"part\": \"NUMERATOR\", \"type\": \"GL_ACCOUNT\", \"label\": \"G\", \"glAccountId\": 1, \"glCodePrefix\": \"2\"}"));
        assertEquals(List.of("ratios[0].components[0].glCodeTo"),
                component("{\"part\": \"NUMERATOR\", \"type\": \"GL_ACCOUNT\", \"label\": \"G\", \"glCodeFrom\": \"1\"}"));
        assertEquals(List.of("ratios[0].components[0].assetClass"),
                component("{\"part\": \"NUMERATOR\", \"type\": \"ASSET_CLASS\", \"label\": \"A\"}"));
        assertEquals(List.of("ratios[0].components[0].ladderMetric", "ratios[0].components[0].metricDays"), component(
                "{\"part\": \"NUMERATOR\", \"type\": \"LADDER_METRIC\", \"label\": \"L\", \"ladderMetric\": \"GUESS\", \"metricDays\": 400}"));
        assertEquals(List.of("ratios[0].components[0].sign", "ratios[0].components[0].weight"), component(
                "{\"part\": \"NUMERATOR\", \"type\": \"ASSET_CLASS\", \"label\": \"A\", \"assetClass\": \"CASH\", \"sign\": 2, \"weight\": 120}"));
        assertEquals(List.of("ratios[0].components[0].assetClass"), component(
                "{\"part\": \"NUMERATOR\", \"type\": \"GL_ACCOUNT\", \"label\": \"G\", \"glAccountId\": 1, \"assetClass\": \"CASH\"}"));
        assertThrows(UnsupportedParameterException.class,
                () -> this.validator.validateRuleSet("{\"ratios\": [{\"code\": \"X\", \"name\": \"X\","
                        + " \"direction\": \"MIN\", \"limit\": 1, \"components\": [{\"part\": \"NUMERATOR\", \"type\": \"ASSET_CLASS\", \"label\": \"A\","
                        + " \"assetClass\": \"CASH\", \"haircut\": 5}]}]}", false));
    }

    @Test
    void assetClassRulesAreChecked() {
        assertEquals(List.of("assetClasses[0].code", "assetClasses[0].label", "assetClasses[0].accountTypes"),
                parameters("{\"assetClasses\": [{}]}"));
        assertEquals(List.of("assetClasses[0].haircut", "assetClasses[0].accountTypes", "assetClasses[0].instrumentKinds"), parameters(
                "{\"assetClasses\": [{\"code\": \"A\", \"label\": \"A\", \"accountTypes\": [\"SAFE\"], \"instrumentKinds\": [\"BORROWING\"], \"haircut\": 101}]}"));
        assertEquals(List.of("assetClasses[1].code", "assetClasses[1].accountTypes"), parameters("{\"assetClasses\": ["
                + "{\"code\": \"A\", \"label\": \"A\", \"accountTypes\": [\"BANK\"]}, {\"code\": \"A\", \"label\": \"B\", \"accountTypes\": [\"BANK\"]}]}"));
    }

    @Test
    void aNewVersionMayCopyAnotherAndNeedsNoBucketsThen() {
        final RuleSetRequest request = this.validator.validateRuleSet(
                "{\"code\": \"BOU_MDI_2004\", \"name\": \"MDI v2\", \"effectiveDate\": \"2026-10-01\", \"copyFromId\": 1}", true);
        assertEquals(1L, request.copyFromId());
        assertNull(request.buckets());
        assertEquals(List.of("copyFromId"), parameters("{\"copyFromId\": 1}"));
    }

    private List<String> component(final String component) {
        return parameters("{\"ratios\": [{\"code\": \"X\", \"name\": \"X\", \"direction\": \"MIN\", \"limit\": 1, \"components\": ["
                + component + ", {\"part\": \"DENOMINATOR\", \"type\": \"ASSET_CLASS\", \"label\": \"D\", \"assetClass\": \"CASH\"}]}]}");
    }

    private static String ratio(final String fields) {
        return "{\"ratios\": [{\"code\": \"X\", \"name\": \"X\", " + fields + ", \"components\": ["
                + "{\"part\": \"NUMERATOR\", \"type\": \"ASSET_CLASS\", \"label\": \"A\", \"assetClass\": \"CASH\"},"
                + "{\"part\": \"DENOMINATOR\", \"type\": \"ASSET_CLASS\", \"label\": \"D\", \"assetClass\": \"CASH\"}]}]}";
    }

    private static String oneRatio(final String code) {
        return "{\"code\": \"" + code + "\", \"name\": \"X\", \"direction\": \"MIN\", \"limit\": 1, \"components\": ["
                + "{\"part\": \"NUMERATOR\", \"type\": \"ASSET_CLASS\", \"label\": \"A\", \"assetClass\": \"CASH\"},"
                + "{\"part\": \"DENOMINATOR\", \"type\": \"ASSET_CLASS\", \"label\": \"D\", \"assetClass\": \"CASH\"}]}";
    }

    private List<String> parameters(final String json) {
        return assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateRuleSet(json, false)).getErrors()
                .stream().map(ApiParameterError::getParameterName).distinct().toList();
    }

    @Test
    void aKindSplitByClassificationMayBeInTwoClasses() {
        final var request = this.validator.validateRuleSet(
                """
                        {"assetClasses": [{"code": "TRADING", "label": "Held for trading", "instrumentKinds": ["GOVERNMENT_BOND"], "classifications": ["FVTPL"]},
                          {"code": "AFS", "label": "Available for sale", "instrumentKinds": ["GOVERNMENT_BOND"], "classifications": ["fvoci"], "haircut": 10}]}
                        """,
                false);
        assertEquals(List.of("FVTPL"), request.assetClasses().get(0).classifications());
        assertEquals(List.of("FVOCI"), request.assetClasses().get(1).classifications());
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateRuleSet("""
                {"assetClasses": [{"code": "ALL", "label": "All bonds", "instrumentKinds": ["GOVERNMENT_BOND"]},
                  {"code": "AFS", "label": "Available for sale", "instrumentKinds": ["GOVERNMENT_BOND"], "classifications": ["FVOCI"]}]}
                """, false), "a class of every classification and one of a classification overlap");
    }
}
