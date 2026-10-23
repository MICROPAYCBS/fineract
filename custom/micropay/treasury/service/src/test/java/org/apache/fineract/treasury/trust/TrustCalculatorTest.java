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
package org.apache.fineract.treasury.trust;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** NPS Act 2020 s.47, s.60 and NPS Regulations 2021 reg 15: coverage, shortfall and concentration. */
class TrustCalculatorTest {

    private static BigDecimal d(final String v) {
        return new BigDecimal(v);
    }

    private static TrustCalculator.Asset demand(final long bank, final String amount) {
        return new TrustCalculator.Asset(TrustAssetType.DEMAND_DEPOSIT, bank, "Bank " + bank, 1L, null, "Trust " + bank, d(amount),
                BigDecimal.ZERO);
    }

    @Test
    void fullyCoveredAboveTheThresholdWithTheConcentrationPerBank() {
        // 1,000m of e-money: 300m + 250m at two banks, a 400m bill (100m of it pledged), a 100m term deposit
        final TrustCalculator.Result r = TrustCalculator.reconcile(
                Map.of(EMoneyCategory.CUSTOMER, d("800000000"), EMoneyCategory.AGENT, d("200000000")),
                List.of(demand(7, "300000000"), demand(8, "250000000"),
                        new TrustCalculator.Asset(TrustAssetType.TREASURY_BILL, null, "Government of Uganda", 1L, 41L, "Bill",
                                d("400000000"), d("100000000")),
                        new TrustCalculator.Asset(TrustAssetType.TERM_DEPOSIT, 7L, "Bank 7", 1L, 42L, "Term", d("100000000"),
                                BigDecimal.ZERO)),
                2);
        assertEquals(0, d("1000000000").compareTo(r.emoney()));
        assertEquals(0, d("1050000000").compareTo(r.trustTotal()));
        assertEquals(0, d("850000000").compareTo(r.eligible()), "demand deposits and the unpledged part of the bill");
        assertEquals(0, d("100000000").compareTo(r.encumbered()));
        assertEquals(0, d("100000000").compareTo(r.ineligible()), "the term deposit is held in trust but is not liquid");
        assertEquals(0, d("85.0000").compareTo(r.coveragePercent()));
        assertEquals(0, d("150000000.00").compareTo(r.shortfall()));
        assertEquals(0, d("50000000").compareTo(r.difference()));
        assertTrue(r.concentrationApplies());
        assertEquals(2, r.concentrations().size(), "the bill is a claim on the government, not an institution");
        assertEquals(0, d("40.0000").compareTo(r.concentrations().getFirst().percent()), "bank 7: 300m demand and 100m term");
        assertEquals(0, d("40.0000").compareTo(r.maxConcentrationPercent()));
    }

    @Test
    void theConcentrationLimitAppliesOnlyAboveFiveHundredMillion() {
        assertFalse(TrustCalculator.reconcile(Map.of(EMoneyCategory.CUSTOMER, d("500000000")), List.of(demand(7, "500000000")), 2)
                .concentrationApplies(), "at UGX 500m exactly it does not apply");
        final TrustCalculator.Result above = TrustCalculator.reconcile(Map.of(EMoneyCategory.CUSTOMER, d("500000001")),
                List.of(demand(7, "500000001")), 2);
        assertTrue(above.concentrationApplies());
        assertEquals(0, d("100.0000").compareTo(above.maxConcentrationPercent()));
        assertEquals(0, BigDecimal.ZERO.compareTo(above.shortfall()));
    }

    @Test
    void noEmoneyIsNoCoverageAndNoShortfall() {
        final TrustCalculator.Result r = TrustCalculator.reconcile(Map.of(), List.of(demand(7, "1000")), 2);
        assertNull(r.coveragePercent());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.shortfall()));
        assertNull(r.concentrations().getFirst().percent());
    }

    @Test
    void dealKindsMapToTrustAssets() {
        assertEquals(TrustAssetType.TREASURY_BILL, TrustCalculator.assetType("TREASURY_BILL"));
        assertEquals(TrustAssetType.TREASURY_BOND, TrustCalculator.assetType("GOVERNMENT_BOND"));
        assertEquals(TrustAssetType.TERM_DEPOSIT, TrustCalculator.assetType("FIXED_DEPOSIT_PLACED"));
        assertNull(TrustCalculator.assetType("MONEY_MARKET_BORROWING"));
    }
}
