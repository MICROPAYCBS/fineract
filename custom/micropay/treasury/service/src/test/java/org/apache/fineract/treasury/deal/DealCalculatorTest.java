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
package org.apache.fineract.treasury.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.treasury.deal.DealCalculator.BondPrice;
import org.apache.fineract.treasury.deal.DealCalculator.Flow;
import org.apache.fineract.treasury.deal.DealCalculator.FlowType;
import org.junit.jupiter.api.Test;

/** Worked examples; the expected figures were computed independently. */
class DealCalculatorTest {

    private static final BigDecimal FACE = new BigDecimal("100000000");

    @Test
    void dayCounts() {
        final LocalDate jan1 = LocalDate.of(2026, 1, 1);
        final LocalDate jul1 = LocalDate.of(2026, 7, 1);
        // 181 actual days
        assertEquals("0.4958904110", ten(DayCount.ACT_365.yearFraction(jan1, jul1)));
        assertEquals("0.5027777778", ten(DayCount.ACT_360.yearFraction(jan1, jul1)));
        // 30/360: a 31st start and a 31st end both count as the 30th
        assertEquals("0.5000000000", ten(DayCount.THIRTY_360.yearFraction(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 7, 31))));
        // the end stays the 31st when the start is not the 30th or 31st: 6 months and 3 days
        assertEquals(183, DayCount.thirty360Days(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 8, 31)));
        // Actual/Actual ISDA across a year end: 31/365 + 60/366
        assertEquals("0.2488659331", ten(DayCount.ACT_ACT.yearFraction(LocalDate.of(2027, 12, 1), LocalDate.of(2028, 3, 1))));
    }

    @Test
    void treasuryBillOnTheDiscountYield() {
        // UGX 1,000,000,000 face, 91 days at 11% on ACT/365: 1e9 / (1 + 0.11 x 91/365) = 973,307,378.47
        assertEquals(new BigDecimal("973307378"), DealCalculator.treasuryBillConsideration(new BigDecimal("1000000000"),
                new BigDecimal("11"), LocalDate.of(2026, 10, 16), LocalDate.of(2027, 1, 15), DayCount.ACT_365, 0));
        assertEquals(new BigDecimal("909317389.14"), DealCalculator.treasuryBillConsideration(new BigDecimal("1000000000"), BigDecimal.TEN,
                LocalDate.of(2026, 10, 16), LocalDate.of(2027, 10, 15), DayCount.ACT_365, 2));
    }

    @Test
    void bondPriceWithAccruedCoupon() {
        // 14% semi-annual, coupons 15 March and 15 September; bought 16 October at 98.50 clean.
        // Accrued (ICMA): 7,000,000 x 31 days / 181 days = 1,198,895.03
        final BondPrice price = DealCalculator.bondPrice(FACE, new BigDecimal("98.50"), new BigDecimal("14"), 2, DayCount.ACT_ACT,
                LocalDate.of(2026, 10, 16), LocalDate.of(2031, 3, 15), null, 2);

        assertEquals(new BigDecimal("98500000.00"), price.cleanAmount());
        assertEquals(new BigDecimal("1198895.03"), price.accruedInterest());
        assertEquals(new BigDecimal("99698895.03"), price.consideration());
        assertEquals(LocalDate.of(2026, 9, 15), price.accrualStart());
        assertEquals(LocalDate.of(2027, 3, 15), price.nextCouponDate());
    }

    @Test
    void bondScheduleSteppedBackFromMaturity() {
        final List<Flow> flows = DealCalculator.bondSchedule(FACE, new BigDecimal("14"), 2, DayCount.ACT_ACT, LocalDate.of(2026, 10, 16),
                LocalDate.of(2031, 3, 15), null, 2);

        assertEquals(10, flows.size(), "9 coupons and the face value");
        assertEquals(LocalDate.of(2027, 3, 15), flows.getFirst().dueDate());
        assertEquals(LocalDate.of(2026, 9, 15), flows.getFirst().periodStart());
        assertEquals(new BigDecimal("7000000.00"), flows.getFirst().amount());
        assertEquals(FlowType.INTEREST, flows.get(8).type());
        assertEquals(LocalDate.of(2031, 3, 15), flows.get(8).dueDate());
        assertEquals(FlowType.PRINCIPAL, flows.get(9).type());
        assertEquals(new BigDecimal("100000000.00"), flows.get(9).amount());
    }

    @Test
    void newlyIssuedBondHasAShortFirstCoupon() {
        final LocalDate issue = LocalDate.of(2026, 10, 1);
        final BondPrice price = DealCalculator.bondPrice(FACE, new BigDecimal("100"), new BigDecimal("14"), 2, DayCount.ACT_ACT,
                LocalDate.of(2026, 10, 16), LocalDate.of(2031, 3, 15), issue, 2);
        final List<Flow> flows = DealCalculator.bondSchedule(FACE, new BigDecimal("14"), 2, DayCount.ACT_ACT, LocalDate.of(2026, 10, 16),
                LocalDate.of(2031, 3, 15), issue, 2);

        // accrued from the issue date: 7,000,000 x 15 / 181; first coupon 7,000,000 x 165 / 181
        assertEquals(new BigDecimal("580110.50"), price.accruedInterest());
        assertEquals(issue, price.accrualStart());
        assertEquals(new BigDecimal("6381215.47"), flows.getFirst().amount());
        assertEquals(issue, flows.getFirst().periodStart());
        assertEquals(new BigDecimal("7000000.00"), flows.get(1).amount(), "later coupons are regular");
    }

    @Test
    void endOfMonthCouponsStayOnTheMonthEnd() {
        assertEquals(List.of(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 8, 31)),
                DealCalculator.futureCouponDates(LocalDate.of(2027, 8, 31), 2, LocalDate.of(2026, 10, 16)));
    }

    @Test
    void placementPaysPrincipalAndInterestAtMaturity() {
        // UGX 500,000,000 for 90 days at 12% on ACT/365: interest 14,794,521 (0 decimals)
        final List<Flow> flows = DealCalculator.simpleSchedule(new BigDecimal("500000000"), new BigDecimal("12"), 0, false,
                DayCount.ACT_365, LocalDate.of(2026, 10, 16), LocalDate.of(2027, 1, 14), 0);

        assertEquals(2, flows.size());
        assertEquals(new BigDecimal("14794521"), flows.get(0).amount());
        assertEquals(FlowType.PRINCIPAL, flows.get(1).type());
        assertEquals(new BigDecimal("500000000"), flows.get(1).amount());
        assertEquals(LocalDate.of(2027, 1, 14), flows.get(1).dueDate());
    }

    @Test
    void periodicInterestEndsWithAShortStub() {
        final List<LocalDate[]> periods = DealCalculator.periods(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 8, 1), 4);

        assertEquals(3, periods.size());
        assertEquals(LocalDate.of(2026, 4, 15), periods.get(0)[1]);
        assertEquals(LocalDate.of(2026, 7, 15), periods.get(1)[1]);
        assertEquals(LocalDate.of(2026, 7, 15), periods.get(2)[0]);
        assertEquals(LocalDate.of(2026, 8, 1), periods.get(2)[1]);
    }

    @Test
    void amortisingBorrowingRepaysEqualPrincipal() {
        // 1,200,000 at 10% ACT/360, quarterly for a year: 300,000 a quarter, interest on what is still owed
        final List<Flow> flows = DealCalculator.simpleSchedule(new BigDecimal("1200000"), BigDecimal.TEN, 4, true, DayCount.ACT_360,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 2);

        assertEquals(8, flows.size());
        assertEquals(List.of("30000.00", "22750.00", "15333.33", "7666.67"),
                flows.stream().filter(f -> f.type() == FlowType.INTEREST).map(f -> f.amount().toPlainString()).toList());
        assertEquals(List.of("300000.00", "300000.00", "300000.00", "300000.00"),
                flows.stream().filter(f -> f.type() == FlowType.PRINCIPAL).map(f -> f.amount().toPlainString()).toList());
        assertEquals(0, flows.getLast().outstanding().signum());
    }

    @Test
    void lastInstalmentTakesTheRounding() {
        final List<Flow> flows = DealCalculator.simpleSchedule(new BigDecimal("1000000"), BigDecimal.ZERO, 12, true, DayCount.ACT_365,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1), 0);

        assertEquals(List.of("333333", "333333", "333334"), flows.stream().map(f -> f.amount().toPlainString()).toList());
    }

    @Test
    void oddFrequencyIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> DealCalculator.months(3));
    }

    @Test
    void pricerSplitsABondPurchase() {
        final DealPricer.Priced priced = DealPricer.price(new DealPricer.Terms(InstrumentKind.GOVERNMENT_BOND, FACE, new BigDecimal("14"),
                new BigDecimal("98.50"), DayCount.ACT_ACT, 2, false, LocalDate.of(2026, 10, 16), LocalDate.of(2031, 3, 15), null, 2));

        assertEquals(new BigDecimal("100000000.00"), priced.principalAmount());
        assertEquals(new BigDecimal("1500000.00"), priced.discount());
        assertEquals(0, priced.premium().signum());
        assertEquals(new BigDecimal("99698895.03"), priced.consideration());
        assertEquals(new BigDecimal("100000000.00"), priced.exposureAmount(), "bonds count at face");
    }

    @Test
    void pricerCarriesABillAtItsCost() {
        final DealPricer.Priced priced = DealPricer.price(new DealPricer.Terms(InstrumentKind.TREASURY_BILL, new BigDecimal("1000000000"),
                new BigDecimal("11"), null, DayCount.ACT_365, 0, false, LocalDate.of(2026, 10, 16), LocalDate.of(2027, 1, 15), null, 0));

        assertEquals(new BigDecimal("973307378"), priced.principalAmount());
        assertEquals(new BigDecimal("1000000000"), priced.exposureAmount(), "bills count at face");
        assertEquals(1, priced.flows().size());
    }

    private static String ten(final BigDecimal value) {
        return value.setScale(10, RoundingMode.HALF_UP).toPlainString();
    }
}
