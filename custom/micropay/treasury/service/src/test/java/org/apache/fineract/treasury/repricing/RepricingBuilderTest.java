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
package org.apache.fineract.treasury.repricing;

import static org.apache.fineract.treasury.repricing.RepricingFixtures.AS_OF;
import static org.apache.fineract.treasury.repricing.RepricingFixtures.context;
import static org.apache.fineract.treasury.repricing.RepricingFixtures.d;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.repricing.RepricingBuilder.BucketGap;
import org.apache.fineract.treasury.repricing.RepricingBuilder.Gap;
import org.junit.jupiter.api.Test;

/**
 * Bucketing by repricing date, the gap maths and earnings at risk. Worked example: gaps of +1,000 overnight, -500 in 2
 * to 8 days, +2,000 in 1 to 3 months, -1,000 in 6 to 12 months and +5,000 beyond a year; at +200bp net interest income
 * changes by 1,000 x 2% x 364.5/365 - 500 x 2% x 360/365 + 2,000 x 2% x 304.5/365 - 1,000 x 2% x 92/365 = 38.44.
 */
class RepricingBuilderTest {

    @Test
    void positionsGoToTheBucketOfTheirRepricingDate() {
        final Gap gap = RepricingBuilder.build(context(List.of()),
                List.of(asset(RateType.FIXED, AS_OF.plusDays(5), null, "100"), asset(RateType.FIXED, AS_OF.minusDays(10), null, "10"),
                        asset(RateType.FLOATING, null, null, "20"), asset(RateType.ADMINISTERED, null, "M3_6", "30"),
                        asset(RateType.FIXED, AS_OF.plusDays(800), null, "40"),
                        asset(RateType.NON_SENSITIVE, AS_OF.plusDays(5), null, "50"),
                        new RepricingPosition(LadderSourceCode.SAVINGS, "DEMAND_SAVINGS", 1L, "S", RepricingSide.LIABILITY,
                                RateType.ADMINISTERED, null, "ON", d("70")),
                        asset(RateType.FIXED, AS_OF.plusDays(5), null, "0")),
                d("1000"));
        assertEquals(0, d("30").compareTo(rsa(gap, "ON")), "past due and floating without a reset in the first bucket");
        assertEquals(0, d("100").compareTo(rsa(gap, "D2_8")));
        assertEquals(0, d("30").compareTo(rsa(gap, "M3_6")), "a distribution names its bucket");
        assertEquals(0, d("40").compareTo(rsa(gap, "Y1_PLUS")));
        assertEquals(0, d("50").compareTo(gap.nonSensitiveAssets()), "non-sensitive positions are in no bucket");
        assertEquals(0, d("70").compareTo(bucket(gap, "ON").rsl()));
        assertEquals(7, gap.lines().size(), "zero amounts are dropped; the past and floating lines stay apart by rate type");
    }

    @Test
    void theGapByRateTypeCumulativeAndAsAShareOfTotalAssets() {
        final Gap gap = RepricingBuilder.build(context(List.of()),
                List.of(asset(RateType.FIXED, AS_OF, null, "600"), asset(RateType.FLOATING, AS_OF, null, "400"),
                        new RepricingPosition(LadderSourceCode.TERM_DEPOSITS, "FIXED_DEPOSIT", 3L, "TD", RepricingSide.LIABILITY,
                                RateType.FIXED, AS_OF.plusDays(4), null, d("500")),
                        asset(RateType.FIXED, AS_OF.plusDays(400), null, "5000")),
                d("10000"));
        final BucketGap on = bucket(gap, "ON");
        assertEquals(0, d("600").compareTo(on.assets().get(RateType.FIXED)));
        assertEquals(0, d("400").compareTo(on.assets().get(RateType.FLOATING)));
        assertEquals(0, d("1000").compareTo(on.gap()));
        assertNull(on.ratio(), "no RSL");
        assertEquals(new BigDecimal("10.0000"), on.gapPercent());
        final BucketGap week = bucket(gap, "D2_8");
        assertEquals(0, d("-500").compareTo(week.gap()));
        assertEquals(0, d("500").compareTo(week.cumulativeGap()));
        assertEquals(new BigDecimal("0.0000"), week.ratio());
        assertEquals(0, d("500").compareTo(gap.cumulativeGapYear()), "the one-year cumulative gap stops at 6 to 12 months");
        assertEquals(new BigDecimal("5.0000"), gap.cumulativeGapYearPercent());
        assertEquals(0, d("5500").compareTo(bucket(gap, "Y1_PLUS").cumulativeGap()));
        assertNull(RepricingBuilder.build(context(List.of()), List.of(), BigDecimal.ZERO).cumulativeGapYearPercent(), "no total assets");
    }

    @Test
    void earningsAtRiskOverTwelveMonths() {
        final Gap gap = RepricingBuilder.build(context(List.of()),
                List.of(asset(RateType.FIXED, AS_OF, null, "1000"), liability(AS_OF.plusDays(5), "500"),
                        asset(RateType.FIXED, AS_OF.plusDays(60), null, "2000"), liability(AS_OF.plusDays(270), "1000"),
                        asset(RateType.FIXED, AS_OF.plusDays(500), null, "5000")),
                d("10000"));
        assertEquals(new BigDecimal("38.44"), RepricingBuilder.deltaNii(gap.buckets(), 200, 2));
        assertEquals(new BigDecimal("-38.44"), RepricingBuilder.deltaNii(gap.buckets(), -200, 2), "the gap beyond a year does not count");
        assertEquals(new BigDecimal("3.8440"), RepricingBuilder.earPercent(d("-38.44"), d("1000")));
        assertNull(RepricingBuilder.earPercent(d("-38.44"), null));
        assertEquals(new BigDecimal("0.0000"), RepricingBuilder.earPercent(d("38.44"), d("1000")), "a gain is no loss");
    }

    private static RepricingPosition asset(final RateType type, final java.time.LocalDate date, final String bucket, final String amount) {
        return new RepricingPosition(LadderSourceCode.LOANS, "LOAN_PRINCIPAL", 1L, "Loans", RepricingSide.ASSET, type, date, bucket,
                d(amount));
    }

    private static RepricingPosition liability(final java.time.LocalDate date, final String amount) {
        return new RepricingPosition(LadderSourceCode.TERM_DEPOSITS, "FIXED_DEPOSIT", 3L, "TD", RepricingSide.LIABILITY, RateType.FIXED,
                date, null, d(amount));
    }

    private static BucketGap bucket(final Gap gap, final String code) {
        return gap.buckets().stream().filter(b -> b.bucket().code().equals(code)).findFirst().orElseThrow();
    }

    private static BigDecimal rsa(final Gap gap, final String code) {
        return bucket(gap, code).rsa();
    }
}
