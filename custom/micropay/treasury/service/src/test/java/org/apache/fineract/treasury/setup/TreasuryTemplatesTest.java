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
package org.apache.fineract.treasury.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.limits.LimitMetric;
import org.apache.fineract.treasury.liquidity.ReturnFrequency;
import org.apache.fineract.treasury.serialization.RatioConfigParser;
import org.apache.fineract.treasury.serialization.TreasuryLimitDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator.RuleSetRequest;
import org.junit.jupiter.api.Test;

/** Every client-type template passes the validators the API uses, and its limits point at its own ratios. */
class TreasuryTemplatesTest {

    private final TreasuryLiquidityDataValidator ruleSets = new TreasuryLiquidityDataValidator(new FromJsonHelper());
    private final TreasuryLimitDataValidator limits = new TreasuryLimitDataValidator(new FromJsonHelper());

    @Test
    void eachTemplateIsAValidRuleSetCitingItsSourceAndPlaceholders() {
        for (final ClientType type : ClientType.values()) {
            final RuleSetRequest request = this.ruleSets.validateRuleSet(TreasuryTemplates.ruleSet(type).orElseThrow(), true);
            assertEquals(type.ruleSetCode(), request.code());
            assertTrue(request.description().contains("PLACEHOLDER"), type + " says what is a placeholder");
            assertTrue(request.description().matches(".*(Regulations|Act) 20\\d\\d.*"), type + " cites its source");
            assertFalse(request.ratios().isEmpty());
            final Set<String> ratios = request.ratios().stream().map(RatioConfigParser.RatioSpec::code).collect(Collectors.toSet());
            for (final String limit : TreasuryTemplates.limits(type)) {
                final TreasuryLimitDataValidator.LimitRequest l = this.limits.validateLimit(limit, null);
                if (type == ClientType.PSP) {
                    assertTrue(Set.of(LimitMetric.TRUST_COVERAGE, LimitMetric.TRUST_BANK_CONCENTRATION, LimitMetric.TRUST_SHORTFALL)
                            .contains(l.metric()));
                } else {
                    assertEquals(LimitMetric.RATIO, l.metric());
                    assertTrue(ratios.contains(l.metricKey()), l.code() + " evaluates a ratio of " + type.ruleSetCode());
                }
                assertTrue(type.regulatoryLimits().contains(l.code()), l.code() + " is one of the type's regulatory limits");
            }
        }
    }

    @Test
    void theRegulatoryFiguresAreTheMinimums() {
        final RuleSetRequest bank = this.ruleSets.validateRuleSet(TreasuryTemplates.ruleSet(ClientType.BANK).orElseThrow(), true);
        assertEquals(ReturnFrequency.WEEKLY, bank.returnFrequency());
        assertEquals(0, ratio(bank, "LIQUID_ASSETS_TO_DEPOSITS").limit().compareTo(new java.math.BigDecimal("20")));
        assertEquals(0, ratio(bank, "LCR").limit().compareTo(new java.math.BigDecimal("100")));
        assertEquals(0, ratio(bank, "NSFR").limit().compareTo(new java.math.BigDecimal("100")));
        final RatioConfigParser.AssetClassSpec afs = bank.assetClasses().stream().filter(c -> c.code().equals("GOVT_SEC_AFS")).findFirst()
                .orElseThrow();
        assertEquals(List.of("FVOCI"), afs.classifications());
        assertEquals(0, afs.haircut().compareTo(new java.math.BigDecimal("10")));

        final RuleSetRequest mdi = this.ruleSets.validateRuleSet(TreasuryTemplates.ruleSet(ClientType.MDI).orElseThrow(), true);
        assertEquals(0, ratio(mdi, "LIQUID_ASSETS_TO_DEPOSITS").limit().compareTo(new java.math.BigDecimal("15")));

        final RuleSetRequest sacco = this.ruleSets.validateRuleSet(TreasuryTemplates.ruleSet(ClientType.SACCO_MFI).orElseThrow(), true);
        assertEquals(ReturnFrequency.QUARTERLY, sacco.returnFrequency());
        assertEquals(5, sacco.buckets().size());
        assertEquals("MIN 15, MIN 10, MAX 10, MAX 25, MAX 40", sacco.ratios().stream()
                .map(r -> r.direction() + " " + r.limit().stripTrailingZeros().toPlainString()).collect(Collectors.joining(", ")));
    }

    @Test
    void thePspTemplateHasTheTrustRatioAndNoDepositRatios() {
        final RuleSetRequest psp = this.ruleSets.validateRuleSet(TreasuryTemplates.ruleSet(ClientType.PSP).orElseThrow(), true);
        assertEquals(ReturnFrequency.DAILY, psp.returnFrequency());
        assertEquals(List.of("TRUST_COVERAGE_GL"), psp.ratios().stream().map(RatioConfigParser.RatioSpec::code).toList());
        assertEquals(3, TreasuryTemplates.limits(ClientType.PSP).size());
    }

    private static RatioConfigParser.RatioSpec ratio(final RuleSetRequest request, final String code) {
        return request.ratios().stream().filter(r -> r.code().equals(code)).findFirst().orElseThrow();
    }
}
