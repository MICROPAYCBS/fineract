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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.apache.fineract.treasury.deal.DealAccrualCalculator.Deal;
import org.apache.fineract.treasury.deal.DealAccrualCalculator.Targets;
import org.apache.fineract.treasury.deal.DealCalculator.Flow;
import org.apache.fineract.treasury.deal.DealCalculator.FlowType;
import org.apache.fineract.treasury.deal.DealPricer.Priced;
import org.junit.jupiter.api.Test;

/** Worked examples; the expected figures were computed independently. */
class DealAccrualCalculatorTest {

    private static final LocalDate VALUE = LocalDate.of(2026, 10, 16);

    @Test
    void placementAccruesDailyAndEndsOnTheScheduledInterest() {
        final Deal deal = deal(InstrumentKind.MONEY_MARKET_PLACEMENT, "500000000", "12", null, DayCount.ACT_365, 0,
                LocalDate.of(2027, 1, 14), null, BigDecimal.ZERO, AmortisationMethod.EFFECTIVE_INTEREST);

        assertEquals(new BigDecimal("0.00"), DealAccrualCalculator.targets(deal, VALUE).interest());
        // 45 days: 500,000,000 x 0.12 x 45/365
        assertEquals(new BigDecimal("7397260.27"), DealAccrualCalculator.targets(deal, VALUE.plusDays(45)).interest());
        // over the whole term the accruals add up to the interest in the schedule
        assertEquals(scheduledInterest(deal), DealAccrualCalculator.targets(deal, LocalDate.of(2027, 1, 14)).interest());
        assertEquals(new BigDecimal("14794520.55"), DealAccrualCalculator.targets(deal, LocalDate.of(2027, 6, 1)).interest(),
                "capped at maturity");
    }

    @Test
    void amortisingBorrowingAccruesOnWhatIsStillOwed() {
        final Deal deal = deal(InstrumentKind.BORROWING, "1200000", "10", null, DayCount.ACT_360, 4, LocalDate.of(2027, 1, 1), null,
                BigDecimal.ZERO, AmortisationMethod.EFFECTIVE_INTEREST, LocalDate.of(2026, 1, 1), true);

        // first quarter's 30,000.00 is in by its end; then 900,000 accrues
        assertEquals(new BigDecimal("30000.00"), DealAccrualCalculator.targets(deal, LocalDate.of(2026, 4, 1)).interest());
        assertEquals(new BigDecimal("30250.00"), DealAccrualCalculator.targets(deal, LocalDate.of(2026, 4, 2)).interest());
        assertEquals(new BigDecimal("75750.00"), DealAccrualCalculator.targets(deal, LocalDate.of(2027, 1, 1)).interest(),
                "30,000.00 + 22,750.00 + 15,333.33 + 7,666.67");
    }

    @Test
    void billGrowsToFaceExactly() {
        final Deal deal = deal(InstrumentKind.TREASURY_BILL, "1000000000", "11", null, DayCount.ACT_365, 0, LocalDate.of(2027, 1, 15), null,
                BigDecimal.ZERO, AmortisationMethod.EFFECTIVE_INTEREST);

        // after 30 days the bill is worth its price for the 61 days left at the 11% yield
        assertEquals(new BigDecimal("8640914.55"), DealAccrualCalculator.targets(deal, VALUE.plusDays(30)).interest());
        final BigDecimal total = DealAccrualCalculator.targets(deal, LocalDate.of(2027, 1, 15)).interest();
        assertEquals(new BigDecimal("26692621.53"), total);
        assertEquals(new BigDecimal("1000000000.00"), deal.consideration().add(total), "consideration + discount earned = face");
    }

    @Test
    void bondCouponIncomeNetsTheAccruedInterestBought() {
        final Deal deal = bond(AmortisationMethod.EFFECTIVE_INTEREST, true);

        // at the first coupon: 7,000,000 less the 1,198,895.03 bought with the bond
        assertEquals(new BigDecimal("5801104.97"), DealAccrualCalculator.targets(deal, LocalDate.of(2027, 3, 15)).interest());
        assertEquals(new BigDecimal("61801104.97"), DealAccrualCalculator.targets(deal, LocalDate.of(2031, 3, 15)).interest(),
                "9 coupons of 7,000,000 less the accrued interest bought");
    }

    @Test
    void bondDiscountAccretesToFaceByEffectiveInterest() {
        final Deal deal = bond(AmortisationMethod.EFFECTIVE_INTEREST, true);

        assertEquals(0, DealAccrualCalculator.targets(deal, VALUE).amortisation().signum());
        // 7.2303% a half-year (14.46% a year); by the first coupon 136,851.15 of the 1,500,000 discount is accreted
        final BigDecimal first = DealAccrualCalculator.targets(deal, LocalDate.of(2027, 3, 15)).amortisation();
        assertTrue(first.subtract(new BigDecimal("136851.15")).abs().compareTo(new BigDecimal("0.05")) <= 0, first.toPlainString());
        BigDecimal previous = BigDecimal.ZERO;
        for (LocalDate d = VALUE.plusMonths(1); d.isBefore(LocalDate.of(2031, 3, 15)); d = d.plusMonths(1)) {
            final BigDecimal now = DealAccrualCalculator.targets(deal, d).amortisation();
            assertTrue(now.compareTo(previous) >= 0, "accretion only grows: " + d);
            previous = now;
        }
        assertEquals(new BigDecimal("1500000.00"), DealAccrualCalculator.targets(deal, LocalDate.of(2031, 3, 15)).amortisation(),
                "carrying amount reaches face");
    }

    @Test
    void straightLineDiscountAndFairValueBonds() {
        final Deal straight = bond(AmortisationMethod.STRAIGHT_LINE, true);
        final long term = java.time.temporal.ChronoUnit.DAYS.between(VALUE, LocalDate.of(2031, 3, 15));
        final LocalDate halfway = VALUE.plusDays(term / 2);

        assertEquals(
                new BigDecimal("1500000").multiply(BigDecimal.valueOf(term / 2)).divide(BigDecimal.valueOf(term), 2, RoundingMode.HALF_UP),
                DealAccrualCalculator.targets(straight, halfway).amortisation());
        assertEquals(0, DealAccrualCalculator.targets(bond(AmortisationMethod.EFFECTIVE_INTEREST, false), halfway).amortisation().signum(),
                "FVTPL bonds are not amortised");
    }

    @Test
    void feesAmortiseStraightLineOrWithTheInterest() {
        final Deal straight = deal(InstrumentKind.MONEY_MARKET_PLACEMENT, "500000000", "12", null, DayCount.ACT_365, 0,
                LocalDate.of(2027, 1, 14), null, new BigDecimal("90000"), AmortisationMethod.STRAIGHT_LINE);
        assertEquals(new BigDecimal("45000.00"), DealAccrualCalculator.targets(straight, VALUE.plusDays(45)).feeAmortised());
        assertEquals(new BigDecimal("90000.00"), DealAccrualCalculator.targets(straight, LocalDate.of(2027, 1, 14)).feeAmortised());

        // with the interest: a bill's discount is earned faster towards maturity, and so is the fee
        final Deal bill = deal(InstrumentKind.TREASURY_BILL, "1000000000", "11", null, DayCount.ACT_365, 0, LocalDate.of(2027, 1, 15), null,
                new BigDecimal("100000"), AmortisationMethod.EFFECTIVE_INTEREST);
        final BigDecimal fee30 = DealAccrualCalculator.targets(bill, VALUE.plusDays(30)).feeAmortised();
        assertEquals(new BigDecimal("32371.92"), fee30, "100,000 x 8,640,914.55 / 26,692,621.53");
        assertEquals(new BigDecimal("100000.00"), DealAccrualCalculator.targets(bill, LocalDate.of(2027, 1, 15)).feeAmortised());
    }

    @Test
    void targetsAreTheSameWhateverTheRunsInBetween() {
        final Deal deal = bond(AmortisationMethod.EFFECTIVE_INTEREST, true);
        final Targets once = DealAccrualCalculator.targets(deal, LocalDate.of(2027, 6, 30));

        assertEquals(once, DealAccrualCalculator.targets(deal, LocalDate.of(2027, 6, 30)));
    }

    private static Deal bond(final AmortisationMethod method, final boolean amortise) {
        final Priced priced = DealPricer.price(new DealPricer.Terms(InstrumentKind.GOVERNMENT_BOND, new BigDecimal("100000000"),
                new BigDecimal("14"), new BigDecimal("98.50"), DayCount.ACT_ACT, 2, false, VALUE, LocalDate.of(2031, 3, 15), null, 2));
        return new Deal(InstrumentKind.GOVERNMENT_BOND, new BigDecimal("100000000"), new BigDecimal("14"), DayCount.ACT_ACT, 2, VALUE,
                LocalDate.of(2031, 3, 15), null, priced.consideration(), priced.accruedInterest(), priced.premium(), priced.discount(),
                BigDecimal.ZERO, method, amortise, 2, priced.flows());
    }

    private static Deal deal(final InstrumentKind kind, final String amount, final String rate, final BigDecimal cleanPrice,
            final DayCount dayCount, final int frequency, final LocalDate maturity, final LocalDate issue, final BigDecimal fee,
            final AmortisationMethod method) {
        return deal(kind, amount, rate, cleanPrice, dayCount, frequency, maturity, issue, fee, method, VALUE, false);
    }

    private static Deal deal(final InstrumentKind kind, final String amount, final String rate, final BigDecimal cleanPrice,
            final DayCount dayCount, final int frequency, final LocalDate maturity, final LocalDate issue, final BigDecimal fee,
            final AmortisationMethod method, final LocalDate value, final boolean amortising) {
        final Priced priced = DealPricer.price(new DealPricer.Terms(kind, new BigDecimal(amount), new BigDecimal(rate), cleanPrice,
                dayCount, frequency, amortising, value, maturity, issue, 2));
        return new Deal(kind, new BigDecimal(amount), new BigDecimal(rate), dayCount, frequency, value, maturity, issue,
                priced.consideration(), priced.accruedInterest(), priced.premium(), priced.discount(), fee, method, true, 2,
                priced.flows());
    }

    private static BigDecimal scheduledInterest(final Deal deal) {
        return deal.flows().stream().filter(f -> f.type() == FlowType.INTEREST).map(Flow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
