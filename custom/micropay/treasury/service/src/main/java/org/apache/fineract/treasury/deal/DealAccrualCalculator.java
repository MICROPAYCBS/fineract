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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.apache.fineract.treasury.deal.DealCalculator.Flow;
import org.apache.fineract.treasury.deal.DealCalculator.FlowType;

/**
 * What a settled deal has earned (or cost) from its value date up to a date, cumulatively: interest, premium or
 * discount amortisation, and fee amortisation. An accrual run posts the difference between these targets and what was
 * posted before, so a rerun for the same date posts nothing, rounding never drifts, and at maturity the targets are the
 * exact totals of the schedule.
 *
 * <p>
 * Interest for a date covers the days from the value date up to, not including, that date: the year fraction from the
 * value date to it. Interest rates, yields and prices are as in {@link DealCalculator}.
 * </p>
 */
public final class DealAccrualCalculator {

    private DealAccrualCalculator() {}

    /**
     * A settled deal as the calculator needs it.
     *
     * @param flows
     *            the schedule generated at capture
     * @param amortisePremiumDiscount
     *            false for bonds measured at fair value through profit or loss: their premium or discount is released
     *            at maturity
     */
    public record Deal(InstrumentKind kind, BigDecimal amount, BigDecimal rate, DayCount dayCount, int frequency, LocalDate valueDate,
            LocalDate maturityDate, LocalDate issueDate, BigDecimal consideration, BigDecimal accruedInterestPurchased, BigDecimal premium,
            BigDecimal discount, BigDecimal deferredFee, AmortisationMethod method, boolean amortisePremiumDiscount, int scale,
            List<Flow> flows) {
    }

    /**
     * @param interest
     *            interest income (asset deals; for bills the discount earned) or expense (liability deals)
     * @param amortisation
     *            bonds: change of the clean carrying amount, positive while a discount is accreted, negative while a
     *            premium is amortised
     * @param feeAmortised
     *            deferred fees taken to expense
     */
    public record Targets(BigDecimal interest, BigDecimal amortisation, BigDecimal feeAmortised) {
    }

    public static Targets targets(final Deal deal, final LocalDate date) {
        final LocalDate d = date.isAfter(deal.maturityDate()) ? deal.maturityDate()
                : date.isBefore(deal.valueDate()) ? deal.valueDate() : date;
        final BigDecimal interest = interest(deal, d);
        return new Targets(interest, amortisation(deal, d), fee(deal, d, interest));
    }

    // ---------- interest ----------

    static BigDecimal interest(final Deal deal, final LocalDate d) {
        return switch (deal.kind().interestMethod()) {
            case SIMPLE -> simpleInterest(deal, d);
            case DISCOUNT_YIELD -> discountEarned(deal, d);
            case COUPON -> couponIncome(deal, d);
        };
    }

    /** Scheduled interest of the periods ended, plus the running period's share by the day count. */
    private static BigDecimal simpleInterest(final Deal deal, final LocalDate d) {
        BigDecimal total = zero(deal);
        for (final Flow flow : deal.flows()) {
            if (flow.type() != FlowType.INTEREST) {
                continue;
            }
            if (!flow.periodEnd().isAfter(d)) {
                total = total.add(flow.amount());
            } else if (flow.periodStart().isBefore(d)) {
                total = total.add(flow.outstanding().multiply(rate(deal.rate()), DayCount.MC)
                        .multiply(deal.dayCount().yearFraction(flow.periodStart(), d), DayCount.MC)
                        .setScale(deal.scale(), RoundingMode.HALF_UP));
            }
        }
        return total;
    }

    /**
     * A bill's carrying amount on a date is its price for the days still to run at the dealt yield, on the basis used
     * at purchase; the discount earned is what it has grown since. It reaches face exactly at maturity.
     */
    private static BigDecimal discountEarned(final Deal deal, final LocalDate d) {
        if (!d.isBefore(deal.maturityDate())) {
            return deal.amount().setScale(deal.scale(), RoundingMode.HALF_UP).subtract(deal.consideration());
        }
        final BigDecimal days = BigDecimal.valueOf(ChronoUnit.DAYS.between(d, deal.maturityDate()));
        final BigDecimal factor = BigDecimal.ONE.add(rate(deal.rate()).multiply(days, DayCount.MC)
                .divide(BigDecimal.valueOf(deal.dayCount().basis(deal.valueDate())), DayCount.MC));
        return deal.amount().divide(factor, DayCount.MC).setScale(deal.scale(), RoundingMode.HALF_UP).subtract(deal.consideration());
    }

    /** Coupons fallen due, plus the coupon accrued on the date, less the accrued interest bought with the bond. */
    private static BigDecimal couponIncome(final Deal deal, final LocalDate d) {
        BigDecimal total = zero(deal);
        for (final Flow flow : deal.flows()) {
            if (flow.type() == FlowType.INTEREST && !flow.dueDate().isAfter(d)) {
                total = total.add(flow.amount());
            }
        }
        return total.add(accruedCoupon(deal, d)).subtract(deal.accruedInterestPurchased());
    }

    static BigDecimal accruedCoupon(final Deal deal, final LocalDate d) {
        if (!d.isBefore(deal.maturityDate())) {
            return zero(deal);
        }
        return DealCalculator.bondPrice(deal.amount(), BigDecimal.valueOf(100), deal.rate(), deal.frequency(), deal.dayCount(), d,
                deal.maturityDate(), deal.issueDate(), deal.scale()).accruedInterest();
    }

    // ---------- premium and discount ----------

    static BigDecimal amortisation(final Deal deal, final LocalDate d) {
        if (deal.kind() != InstrumentKind.GOVERNMENT_BOND || !deal.amortisePremiumDiscount()) {
            return zero(deal);
        }
        final BigDecimal total = deal.discount().subtract(deal.premium());
        if (total.signum() == 0 || !d.isBefore(deal.maturityDate())) {
            return total;
        }
        if (deal.method() == AmortisationMethod.STRAIGHT_LINE) {
            return byDays(total, deal, d);
        }
        return BigDecimal.valueOf(EffectiveYield.of(deal).amortisation(deal, d)).setScale(deal.scale(), RoundingMode.HALF_UP);
    }

    /**
     * Effective interest by coupon period. The yield per period {@code j} prices the remaining coupons and the face at
     * the dirty purchase price, the first period discounted by the fraction of it still to run (ICMA): dirty = Σ CF_i /
     * (1 + j)^(w + i - 1). On each coupon date the amortised cost is the flows still to come discounted at {@code j};
     * each period's change is recognised evenly by days within it. Doubles are precise enough here (15 significant
     * digits) and the step to maturity lands on face exactly.
     */
    record EffectiveYield(double perPeriod, List<LocalDate> dates, List<Double> amounts, double firstFraction) {

        static EffectiveYield of(final Deal deal) {
            final List<LocalDate> dates = new java.util.ArrayList<>();
            final List<Double> amounts = new java.util.ArrayList<>();
            for (final Flow flow : deal.flows()) {
                if (flow.type() == FlowType.INTEREST) {
                    dates.add(flow.dueDate());
                    amounts.add(flow.amount().doubleValue());
                }
            }
            final double face = deal.amount().doubleValue();
            if (dates.isEmpty() || !dates.getLast().equals(deal.maturityDate())) {
                dates.add(deal.maturityDate());
                amounts.add(face);
            } else {
                amounts.set(amounts.size() - 1, amounts.getLast() + face);
            }
            final LocalDate first = dates.getFirst();
            final LocalDate regularStart = first.minusMonths(DealCalculator.months(deal.frequency()));
            final double w = (double) ChronoUnit.DAYS.between(deal.valueDate(), first) / ChronoUnit.DAYS.between(regularStart, first);
            final double dirty = deal.consideration().doubleValue();
            double low = -0.99;
            double high = 10;
            for (int i = 0; i < 200; i++) {
                final double mid = (low + high) / 2;
                if (value(amounts, mid, 0, w) > dirty) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            return new EffectiveYield((low + high) / 2, dates, amounts, w);
        }

        /** Flows from index {@code from} on, the first discounted by {@code offset} periods. */
        private static double value(final List<Double> amounts, final double j, final int from, final double offset) {
            double total = 0;
            for (int i = from; i < amounts.size(); i++) {
                total += amounts.get(i) * Math.pow(1 + j, -(offset + i - from));
            }
            return total;
        }

        /** Amortised (clean) cost on each coupon date; the value date and maturity are the ends. */
        private double costAt(final int couponIndex, final Deal deal) {
            if (couponIndex >= this.dates.size() - 1) {
                return deal.amount().doubleValue();
            }
            return value(this.amounts, this.perPeriod, couponIndex + 1, 1);
        }

        double amortisation(final Deal deal, final LocalDate d) {
            final double start = deal.consideration().subtract(deal.accruedInterestPurchased()).doubleValue();
            LocalDate from = deal.valueDate();
            double fromCost = start;
            for (int k = 0; k < this.dates.size(); k++) {
                final LocalDate to = this.dates.get(k);
                final double toCost = costAt(k, deal);
                if (!d.isAfter(to)) {
                    final double share = (double) ChronoUnit.DAYS.between(from, d) / ChronoUnit.DAYS.between(from, to);
                    return fromCost + (toCost - fromCost) * share - start;
                }
                from = to;
                fromCost = toCost;
            }
            return fromCost - start;
        }
    }

    // ---------- fees ----------

    /** Straight-line by days, or in proportion to the interest earned (falling back to days when there is none). */
    static BigDecimal fee(final Deal deal, final LocalDate d, final BigDecimal interestToDate) {
        final BigDecimal fee = deal.deferredFee() == null ? BigDecimal.ZERO : deal.deferredFee();
        if (fee.signum() == 0 || !d.isBefore(deal.maturityDate())) {
            return fee.setScale(deal.scale(), RoundingMode.HALF_UP);
        }
        if (deal.method() == AmortisationMethod.EFFECTIVE_INTEREST) {
            final BigDecimal totalInterest = interest(deal, deal.maturityDate());
            if (totalInterest.signum() > 0) {
                return fee.multiply(interestToDate, DayCount.MC).divide(totalInterest, DayCount.MC).setScale(deal.scale(),
                        RoundingMode.HALF_UP);
            }
        }
        return byDays(fee, deal, d);
    }

    private static BigDecimal byDays(final BigDecimal total, final Deal deal, final LocalDate d) {
        final BigDecimal elapsed = BigDecimal.valueOf(ChronoUnit.DAYS.between(deal.valueDate(), d));
        final BigDecimal term = BigDecimal.valueOf(ChronoUnit.DAYS.between(deal.valueDate(), deal.maturityDate()));
        return total.multiply(elapsed, DayCount.MC).divide(term, DayCount.MC).setScale(deal.scale(), RoundingMode.HALF_UP);
    }

    private static BigDecimal rate(final BigDecimal percent) {
        return percent.divide(BigDecimal.valueOf(100), DayCount.MC);
    }

    private static BigDecimal zero(final Deal deal) {
        return BigDecimal.ZERO.setScale(deal.scale());
    }
}
