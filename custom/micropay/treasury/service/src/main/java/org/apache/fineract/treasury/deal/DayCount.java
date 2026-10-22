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
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Day count conventions: the fraction of a year between two dates.
 *
 * <ul>
 * <li>{@code ACT_365}: actual days / 365 (Actual/365 Fixed).</li>
 * <li>{@code ACT_360}: actual days / 360.</li>
 * <li>{@code THIRTY_360}: 30/360 bond basis (ISDA): a 31st start becomes the 30th, and a 31st end becomes the 30th when
 * the start is the 30th or 31st.</li>
 * <li>{@code ACT_ACT}: Actual/Actual ISDA: days in a leap year over 366 plus days in other years over 365. For bond
 * coupons the ICMA ratio is used instead, see {@link DealCalculator}.</li>
 * </ul>
 */
public enum DayCount {

    ACT_365, //
    ACT_360, //
    THIRTY_360, //
    ACT_ACT; //

    static final MathContext MC = MathContext.DECIMAL64;

    public BigDecimal yearFraction(final LocalDate start, final LocalDate end) {
        if (end.isBefore(start)) {
            return yearFraction(end, start).negate();
        }
        return switch (this) {
            case ACT_365 -> days(start, end).divide(BigDecimal.valueOf(365), MC);
            case ACT_360 -> days(start, end).divide(BigDecimal.valueOf(360), MC);
            case THIRTY_360 -> BigDecimal.valueOf(thirty360Days(start, end)).divide(BigDecimal.valueOf(360), MC);
            case ACT_ACT -> actualActualIsda(start, end);
        };
    }

    /** Days in the year of the convention, for discount-yield pricing; Actual/Actual counts the start date's year. */
    public int basis(final LocalDate start) {
        return switch (this) {
            case ACT_360, THIRTY_360 -> 360;
            case ACT_365 -> 365;
            case ACT_ACT -> start.isLeapYear() ? 366 : 365;
        };
    }

    static long thirty360Days(final LocalDate start, final LocalDate end) {
        int d1 = start.getDayOfMonth();
        int d2 = end.getDayOfMonth();
        if (d1 == 31) {
            d1 = 30;
        }
        if (d2 == 31 && d1 == 30) {
            d2 = 30;
        }
        return 360L * (end.getYear() - start.getYear()) + 30L * (end.getMonthValue() - start.getMonthValue()) + (d2 - d1);
    }

    private static BigDecimal actualActualIsda(final LocalDate start, final LocalDate end) {
        BigDecimal fraction = BigDecimal.ZERO;
        LocalDate from = start;
        while (from.isBefore(end)) {
            final LocalDate yearEnd = LocalDate.of(from.getYear() + 1, 1, 1);
            final LocalDate to = end.isBefore(yearEnd) ? end : yearEnd;
            fraction = fraction.add(days(from, to).divide(BigDecimal.valueOf(from.isLeapYear() ? 366 : 365), MC));
            from = to;
        }
        return fraction;
    }

    static BigDecimal days(final LocalDate start, final LocalDate end) {
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end));
    }
}
