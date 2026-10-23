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
package org.apache.fineract.treasury.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.treasury.liquidity.Bucket;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.stress.StressCalculator.BucketResult;
import org.apache.fineract.treasury.stress.StressCalculator.FundingAccess;
import org.apache.fineract.treasury.stress.StressCalculator.Survival;
import org.junit.jupiter.api.Test;

/** Each shock type, capacity by days to access, and the survival horizon with its edge cases. */
class StressCalculatorTest {

    private static final Bucket ON = new Bucket(1, "ON", "Overnight", 0, 1);
    private static final Bucket WEEK = new Bucket(2, "D2_8", "2 to 8 days", 2, 8);
    private static final Bucket LAST = new Bucket(3, "Y1_PLUS", "Beyond", 9, null);
    private static final List<Bucket> BUCKETS = List.of(ON, WEEK, LAST);

    @Test
    void multiplyScalesTheMatchingSideOnly() {
        final List<LadderLine> lines = List.of(line(ON, LadderSourceCode.LOANS, "LOAN_PRINCIPAL", 1L, "1000", "0"),
                line(ON, LadderSourceCode.LOANS, "LOAN_INTEREST_FEES", 1L, "-100", "0"),
                line(WEEK, LadderSourceCode.LOANS, "LOAN_PRINCIPAL", 1L, "500", "-50"));
        final List<StressLine> stressed = shock(lines, new Shock(ShockType.MULTIPLY, "Collections at 70%", LadderSourceCode.LOANS, null,
                null, null, ShockSide.INFLOW, pct("70"), null, null, null));
        assertEquals(new BigDecimal("700.00"), at(stressed, 1, "LOAN_PRINCIPAL", 1L).getStressed());
        assertEquals(new BigDecimal("-100"), at(stressed, 1, "LOAN_INTEREST_FEES", 1L).getStressed(), "an outflow is left alone");
        assertFalse(at(stressed, 1, "LOAN_INTEREST_FEES", 1L).isShocked());
        assertEquals(new BigDecimal("315.00"), at(stressed, 2, "LOAN_PRINCIPAL", 1L).getStressed(), "70% of 450");
        assertEquals("Collections at 70%", at(stressed, 2, "LOAN_PRINCIPAL", 1L).shockLabels());
        final List<StressLine> week = shock(lines, new Shock(ShockType.MULTIPLY, "x", LadderSourceCode.LOANS, "LOAN_PRINCIPAL", null,
                "D2_8", ShockSide.BOTH, pct("150"), null, null, null));
        assertEquals(new BigDecimal("1000"), at(week, 1, "LOAN_PRINCIPAL", 1L).getStressed(), "another bucket");
        assertEquals(new BigDecimal("675.00"), at(week, 2, "LOAN_PRINCIPAL", 1L).getStressed());
    }

    @Test
    void removeRolloverTakesTheBehaviouralRolloverOut() {
        final List<LadderLine> lines = List.of(line(WEEK, LadderSourceCode.TERM_DEPOSITS, "FIXED_DEPOSIT", 3L, "-1000", "700"),
                line(WEEK, LadderSourceCode.DEALS, "DEAL_PRINCIPAL", 11L, "-2000", "750"),
                line(WEEK, LadderSourceCode.DEALS, "DEAL_PRINCIPAL", 17L, "1000", "-500"));
        final List<StressLine> deposits = shock(lines, new Shock(ShockType.REMOVE_ROLLOVER, "Not rolled", LadderSourceCode.TERM_DEPOSITS,
                null, null, null, ShockSide.BOTH, null, null, null, null));
        assertEquals(new BigDecimal("-1000"), at(deposits, 2, "FIXED_DEPOSIT", 3L).getStressed(), "the deposit is paid out whole");
        final List<StressLine> borrowings = shock(lines, new Shock(ShockType.REMOVE_ROLLOVER, "Borrowings not rolled",
                LadderSourceCode.DEALS, null, null, null, ShockSide.OUTFLOW, null, null, null, null));
        assertEquals(new BigDecimal("-2000"), at(borrowings, 2, "DEAL_PRINCIPAL", 11L).getStressed());
        assertEquals(new BigDecimal("500"), at(borrowings, 2, "DEAL_PRINCIPAL", 17L).getStressed(),
                "a placement of ours keeps its rollover");
    }

    @Test
    void anOutflowAsAShareOfTheBalanceInTheTargetBucket() {
        final List<LadderLine> lines = List.of(line(ON, LadderSourceCode.SAVINGS, "DEMAND_SAVINGS", 1L, "-1000", "980"),
                line(WEEK, LadderSourceCode.SAVINGS, "DEMAND_SAVINGS", 1L, "0", "-10"),
                line(LAST, LadderSourceCode.SAVINGS, "DEMAND_SAVINGS", 1L, "0", "-970"),
                line(ON, LadderSourceCode.SAVINGS, "DEMAND_SAVINGS", 4L, "-500", "490"));
        final List<StressLine> stressed = shock(lines, new Shock(ShockType.ADD_OUTFLOW_PCT_OF_BALANCE, "20% run", LadderSourceCode.SAVINGS,
                "DEMAND_SAVINGS", null, "D2_8", ShockSide.OUTFLOW, pct("20"), null, null, null));
        assertEquals(5, stressed.size(), "a line added for product 4 in the week");
        final StressLine product1 = stressed.stream().filter(l -> l.getBucketSeq() == 2 && l.getProductId() == 1L).findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("-210.00"), product1.getStressed(), "20% of the 1,000 balance on top of the 10");
        final StressLine product4 = stressed.stream().filter(l -> l.getBucketSeq() == 2 && l.getProductId() == 4L).findFirst()
                .orElseThrow();
        assertEquals(0, product4.getBaseline().signum());
        assertEquals(new BigDecimal("-100.00"), product4.getStressed());
        assertEquals(new BigDecimal("-20"),
                stressed.stream().filter(l -> l.getBucketSeq() == 1 && l.getProductId() == 1L).findFirst().orElseThrow().getStressed(),
                "the overnight bucket is not the target");
        assertEquals(new BigDecimal("-200.00"),
                shock(lines,
                        new Shock(ShockType.ADD_OUTFLOW_PCT_OF_BALANCE, "x", LadderSourceCode.SAVINGS, null, 1L, null, ShockSide.BOTH,
                                pct("20"), null, null, null))
                        .getFirst().getStressed().subtract(new BigDecimal("-20")),
                "the first bucket by default");
    }

    @Test
    void monetisedHoldingsLeaveTheirInflowsToTheCapacity() {
        final List<LadderLine> lines = List.of(line(WEEK, LadderSourceCode.DEALS, "DEAL_PRINCIPAL", 24L, "500", "0"),
                line(WEEK, LadderSourceCode.DEALS, "DEAL_PRINCIPAL", 11L, "-500", "0"));
        final List<StressLine> stressed = StressCalculator.shock(BUCKETS, lines, List.of(),
                l -> l.productId() == 24L || l.productId() == 11L, 2);
        assertEquals(0, stressed.get(1).getStressed().signum());
        assertEquals("Monetised as counterbalancing capacity", stressed.get(1).shockLabels());
        assertEquals(new BigDecimal("-500"), stressed.get(0).getStressed(), "outflows stay");
    }

    @Test
    void fundingCountsFromTheFirstBucketItCanBeDrawnIn() {
        final List<FundingAccess> lines = List.of(new FundingAccess(new BigDecimal("200"), 5), new FundingAccess(new BigDecimal("50"), 0),
                new FundingAccess(new BigDecimal("1000"), 400));
        assertEquals(0, new BigDecimal("50").compareTo(StressCalculator.accessible(lines, ON)));
        assertEquals(0, new BigDecimal("250").compareTo(StressCalculator.accessible(lines, WEEK)));
        assertEquals(0, new BigDecimal("1250").compareTo(StressCalculator.accessible(lines, LAST)), "every line in the open-ended bucket");
        assertEquals(0,
                new BigDecimal("688.50").compareTo(
                        StressCalculator.eligible(new BigDecimal("1000"), new BigDecimal("100"), BigDecimal.TEN, new BigDecimal("15"), 2)),
                "900 at 90% then 85%");
    }

    @Test
    void survivalIsInterpolatedWithinTheBucket() {
        // cumulative 100, -200, -250 with no capacity: negative a third of the way into the 7-day week
        Survival survival = StressCalculator.survival(results(List.of("100", "-300", "-50"), "0", List.of()), true);
        assertEquals(new BigDecimal("4.33"), survival.days());
        assertEquals("D2_8", survival.bucketCode());
        assertFalse(survival.beyondHorizon());
        // 150 of liquid assets: 250 at the start of the week, -50 at its end
        survival = StressCalculator.survival(results(List.of("100", "-300", "-50"), "150", List.of()), true);
        assertEquals(new BigDecimal("7.83"), survival.days());
    }

    @Test
    void survivalEdgeCases() {
        Survival survival = StressCalculator.survival(results(List.of("-10", "5", "5"), "0", List.of()), true);
        assertEquals(new BigDecimal("0.00"), survival.days(), "negative on day 0");
        survival = StressCalculator.survival(results(List.of("10", "5", "5"), "0", List.of()), true);
        assertNull(survival.days());
        assertTrue(survival.beyondHorizon(), "never negative");
        assertEquals(9, survival.horizonDays());
        survival = StressCalculator.survival(results(List.of("10", "0", "-50"), "0", List.of()), true);
        assertEquals(new BigDecimal("9.00"), survival.days(), "the open-ended last bucket counts from its first day");
        // 200 of funding drawn in 5 days covers the week; the last bucket goes below
        survival = StressCalculator
                .survival(results(List.of("0", "-200", "-50"), "0", List.of(new FundingAccess(new BigDecimal("200"), 5))), true);
        assertEquals(new BigDecimal("9.00"), survival.days());
        assertEquals("Y1_PLUS", survival.bucketCode());
    }

    @Test
    void bucketsCarryTheBaselineAndTheStressedGaps() {
        final List<LadderLine> lines = List.of(line(ON, LadderSourceCode.LOANS, "LOAN_PRINCIPAL", 1L, "1000", "0"),
                line(WEEK, LadderSourceCode.SAVINGS, "DEMAND_SAVINGS", 1L, "-400", "0"));
        final List<StressLine> stressed = shock(lines, new Shock(ShockType.MULTIPLY, "half", LadderSourceCode.LOANS, null, null, null,
                ShockSide.INFLOW, pct("50"), null, null, null));
        final List<BucketResult> results = StressCalculator.buckets(BUCKETS, stressed, new BigDecimal("10"),
                List.of(new FundingAccess(new BigDecimal("20"), 3)), new BigDecimal("30"),
                List.of(new FundingAccess(new BigDecimal("20"), 3)));
        assertEquals(0, new BigDecimal("1000").compareTo(results.get(0).baselineCumulative()));
        assertEquals(0, new BigDecimal("500").compareTo(results.get(0).stressedCumulative()));
        assertEquals(0, new BigDecimal("100").compareTo(results.get(1).stressedCumulative()));
        assertEquals(0, new BigDecimal("10").compareTo(results.get(0).capacity()), "the line takes 3 days");
        assertEquals(0, new BigDecimal("130").compareTo(results.get(1).position()));
        assertEquals(0, new BigDecimal("50").compareTo(results.get(1).baselineCapacity()));
        assertNull(StressCalculator.survival(results, false).days());
    }

    private static StressLine at(final List<StressLine> lines, final int bucket, final String code, final Long product) {
        return lines.stream().filter(l -> l.getBucketSeq() == bucket && l.getLineCode().equals(code) && product.equals(l.getProductId()))
                .findFirst().orElseThrow();
    }

    private static List<StressLine> shock(final List<LadderLine> lines, final Shock shock) {
        return StressCalculator.shock(BUCKETS, lines, List.of(shock), l -> false, 2);
    }

    private static List<BucketResult> results(final List<String> nets, final String liquid, final List<FundingAccess> funding) {
        final List<LadderLine> lines = List.of(line(ON, LadderSourceCode.FIXED, "X", null, nets.get(0), "0"),
                line(WEEK, LadderSourceCode.FIXED, "X", null, nets.get(1), "0"),
                line(LAST, LadderSourceCode.FIXED, "X", null, nets.get(2), "0"));
        return StressCalculator.buckets(BUCKETS, StressCalculator.shock(BUCKETS, lines, List.of(), l -> false, 2), new BigDecimal(liquid),
                funding, new BigDecimal(liquid), funding);
    }

    private static LadderLine line(final Bucket bucket, final LadderSourceCode source, final String code, final Long product,
            final String contractual, final String behavioural) {
        return new LadderLine(bucket.seq(), bucket.code(), source, code, product, "Product " + product, new BigDecimal(contractual),
                new BigDecimal(behavioural));
    }

    private static BigDecimal pct(final String value) {
        return new BigDecimal(value);
    }
}
