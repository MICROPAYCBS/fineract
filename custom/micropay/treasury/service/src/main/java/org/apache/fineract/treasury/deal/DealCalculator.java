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
import java.util.ArrayList;
import java.util.List;

/**
 * Consideration and cash flow schedules of treasury deals. Rates and yields are percentages per year (12.5 means
 * 12.5%), prices are per 100 of face value, and frequencies are payments per year (1, 2, 4 or 12; 0 means once, at
 * maturity). Amounts are rounded half up to the currency's decimal places; intermediate figures are not.
 */
public final class DealCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private DealCalculator() {}

    public enum FlowType {
        PRINCIPAL, //
        INTEREST; //
    }

    /** One expected cash flow; {@code outstanding} is the principal still owed after it. */
    public record Flow(int sequence, FlowType type, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate, BigDecimal amount,
            BigDecimal outstanding) {
    }

    /** Price of a bond purchase: what is paid, split into clean amount and accrued interest. */
    public record BondPrice(BigDecimal cleanAmount, BigDecimal accruedInterest, BigDecimal consideration, LocalDate accrualStart,
            LocalDate nextCouponDate) {
    }

    // ---------- treasury bills ----------

    /**
     * Discount-yield price of a treasury bill: {@code face / (1 + yield × days / basis)}, days from value date to
     * maturity and basis from the day count (365 for ACT_365, 360 for ACT_360 and 30/360, the value date's year for
     * ACT_ACT).
     */
    public static BigDecimal treasuryBillConsideration(final BigDecimal face, final BigDecimal yieldPercent, final LocalDate valueDate,
            final LocalDate maturityDate, final DayCount dayCount, final int scale) {
        final BigDecimal days = BigDecimal.valueOf(ChronoUnit.DAYS.between(valueDate, maturityDate));
        final BigDecimal discountFactor = BigDecimal.ONE
                .add(rate(yieldPercent).multiply(days, DayCount.MC).divide(BigDecimal.valueOf(dayCount.basis(valueDate)), DayCount.MC));
        return face.divide(discountFactor, DayCount.MC).setScale(scale, RoundingMode.HALF_UP);
    }

    // ---------- bonds ----------

    /** Coupon dates after the value date, stepping back from maturity by whole periods. */
    public static List<LocalDate> futureCouponDates(final LocalDate maturityDate, final int frequency, final LocalDate valueDate) {
        final List<LocalDate> dates = new ArrayList<>();
        for (int k = 0;; k++) {
            final LocalDate date = maturityDate.minusMonths((long) k * months(frequency));
            if (!date.isAfter(valueDate)) {
                break;
            }
            dates.addFirst(date);
        }
        return dates;
    }

    /** The regular coupon date on or before the value date. */
    public static LocalDate previousCouponDate(final LocalDate maturityDate, final int frequency, final LocalDate valueDate) {
        final List<LocalDate> future = futureCouponDates(maturityDate, frequency, valueDate);
        final LocalDate next = future.isEmpty() ? maturityDate : future.getFirst();
        return next.minusMonths(months(frequency));
    }

    /**
     * Regular coupon: face × coupon rate / frequency. A first coupon after a later issue date (a short stub) is cut in
     * proportion: by days over the regular period's days for ACT_ACT (ICMA), else by the day count's year fraction.
     */
    public static BigDecimal couponAmount(final BigDecimal face, final BigDecimal couponPercent, final int frequency,
            final DayCount dayCount, final LocalDate periodStart, final LocalDate regularStart, final LocalDate periodEnd) {
        final BigDecimal regular = face.multiply(rate(couponPercent), DayCount.MC).divide(BigDecimal.valueOf(frequency), DayCount.MC);
        if (!periodStart.isAfter(regularStart)) {
            return regular;
        }
        if (dayCount == DayCount.ACT_ACT) {
            return regular.multiply(DayCount.days(periodStart, periodEnd), DayCount.MC).divide(DayCount.days(regularStart, periodEnd),
                    DayCount.MC);
        }
        return face.multiply(rate(couponPercent), DayCount.MC).multiply(dayCount.yearFraction(periodStart, periodEnd), DayCount.MC);
    }

    /**
     * What a bond purchase costs on the value date: face × clean price / 100 plus the coupon accrued since the last
     * coupon date (or the issue date, when the bond was issued after it). Accrued interest is the regular coupon × days
     * accrued / days in the coupon period for ACT_ACT (ICMA), else face × rate × the day count's year fraction.
     *
     * @param issueDate
     *            optional; only matters when it falls after the regular coupon date before the value date
     */
    public static BondPrice bondPrice(final BigDecimal face, final BigDecimal cleanPrice, final BigDecimal couponPercent,
            final int frequency, final DayCount dayCount, final LocalDate valueDate, final LocalDate maturityDate,
            final LocalDate issueDate, final int scale) {
        final LocalDate regularStart = previousCouponDate(maturityDate, frequency, valueDate);
        final LocalDate accrualStart = issueDate != null && issueDate.isAfter(regularStart) ? issueDate : regularStart;
        final List<LocalDate> future = futureCouponDates(maturityDate, frequency, valueDate);
        final LocalDate nextCoupon = future.isEmpty() ? maturityDate : future.getFirst();
        final BigDecimal accrued;
        if (dayCount == DayCount.ACT_ACT) {
            final BigDecimal regular = face.multiply(rate(couponPercent), DayCount.MC).divide(BigDecimal.valueOf(frequency), DayCount.MC);
            accrued = regular.multiply(DayCount.days(accrualStart, valueDate), DayCount.MC).divide(DayCount.days(regularStart, nextCoupon),
                    DayCount.MC);
        } else {
            accrued = face.multiply(rate(couponPercent), DayCount.MC).multiply(dayCount.yearFraction(accrualStart, valueDate), DayCount.MC);
        }
        final BigDecimal clean = face.multiply(cleanPrice, DayCount.MC).divide(HUNDRED, DayCount.MC).setScale(scale, RoundingMode.HALF_UP);
        final BigDecimal accruedRounded = accrued.setScale(scale, RoundingMode.HALF_UP);
        return new BondPrice(clean, accruedRounded, clean.add(accruedRounded), accrualStart, nextCoupon);
    }

    /** Coupons after the value date and the face value at maturity. */
    public static List<Flow> bondSchedule(final BigDecimal face, final BigDecimal couponPercent, final int frequency,
            final DayCount dayCount, final LocalDate valueDate, final LocalDate maturityDate, final LocalDate issueDate, final int scale) {
        final List<Flow> flows = new ArrayList<>();
        final List<LocalDate> dates = futureCouponDates(maturityDate, frequency, valueDate);
        final LocalDate firstRegularStart = previousCouponDate(maturityDate, frequency, valueDate);
        LocalDate start = issueDate != null && issueDate.isAfter(firstRegularStart) ? issueDate : firstRegularStart;
        LocalDate regularStart = firstRegularStart;
        int sequence = 1;
        for (final LocalDate date : dates) {
            final BigDecimal coupon = couponAmount(face, couponPercent, frequency, dayCount, start, regularStart, date).setScale(scale,
                    RoundingMode.HALF_UP);
            flows.add(new Flow(sequence++, FlowType.INTEREST, start, date, date, coupon, face));
            start = date;
            regularStart = date;
        }
        flows.add(new Flow(sequence, FlowType.PRINCIPAL, null, null, maturityDate, face.setScale(scale, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(scale)));
        return flows;
    }

    // ---------- placements, deposits and borrowings ----------

    /**
     * Interest periods from the value date forward by whole periods, the last one ending at maturity (a short stub if
     * the term is not a whole number of periods). Frequency 0 gives one period.
     */
    public static List<LocalDate[]> periods(final LocalDate valueDate, final LocalDate maturityDate, final int frequency) {
        final List<LocalDate[]> periods = new ArrayList<>();
        if (frequency == 0) {
            periods.add(new LocalDate[] { valueDate, maturityDate });
            return periods;
        }
        LocalDate start = valueDate;
        for (int k = 1;; k++) {
            final LocalDate end = valueDate.plusMonths((long) k * months(frequency));
            if (!end.isBefore(maturityDate)) {
                periods.add(new LocalDate[] { start, maturityDate });
                return periods;
            }
            periods.add(new LocalDate[] { start, end });
            start = end;
        }
    }

    /**
     * Simple-interest schedule: interest on the outstanding principal for each period by the day count, principal at
     * maturity, or in equal instalments each period when amortising (the last instalment takes the rounding).
     */
    public static List<Flow> simpleSchedule(final BigDecimal principal, final BigDecimal ratePercent, final int frequency,
            final boolean amortising, final DayCount dayCount, final LocalDate valueDate, final LocalDate maturityDate, final int scale) {
        final List<LocalDate[]> periods = periods(valueDate, maturityDate, frequency);
        final BigDecimal instalment = amortising
                ? principal.divide(BigDecimal.valueOf(periods.size()), DayCount.MC).setScale(scale, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        final List<Flow> flows = new ArrayList<>();
        BigDecimal outstanding = principal.setScale(scale, RoundingMode.HALF_UP);
        int sequence = 1;
        for (int i = 0; i < periods.size(); i++) {
            final LocalDate start = periods.get(i)[0];
            final LocalDate end = periods.get(i)[1];
            final boolean last = i == periods.size() - 1;
            final BigDecimal interest = outstanding.multiply(rate(ratePercent), DayCount.MC)
                    .multiply(dayCount.yearFraction(start, end), DayCount.MC).setScale(scale, RoundingMode.HALF_UP);
            final BigDecimal repaid = last ? outstanding : amortising ? instalment : BigDecimal.ZERO;
            if (interest.signum() > 0) {
                flows.add(new Flow(sequence++, FlowType.INTEREST, start, end, end, interest, outstanding));
            }
            if (repaid.signum() > 0) {
                outstanding = outstanding.subtract(repaid);
                flows.add(new Flow(sequence++, FlowType.PRINCIPAL, start, end, end, repaid, outstanding));
            }
        }
        return flows;
    }

    /** A treasury bill pays its face value at maturity; the discount is the interest. */
    public static List<Flow> treasuryBillSchedule(final BigDecimal face, final LocalDate valueDate, final LocalDate maturityDate,
            final int scale) {
        return List.of(new Flow(1, FlowType.PRINCIPAL, valueDate, maturityDate, maturityDate, face.setScale(scale, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(scale)));
    }

    public static int months(final int frequency) {
        if (frequency != 1 && frequency != 2 && frequency != 4 && frequency != 12) {
            throw new IllegalArgumentException("Frequency must be 1, 2, 4 or 12 payments a year, not " + frequency);
        }
        return 12 / frequency;
    }

    private static BigDecimal rate(final BigDecimal percent) {
        return percent.divide(HUNDRED, DayCount.MC);
    }
}
