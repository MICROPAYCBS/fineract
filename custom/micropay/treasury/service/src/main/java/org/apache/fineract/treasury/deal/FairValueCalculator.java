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

/**
 * Clean fair value of a bill or bond from a market quote.
 *
 * <ul>
 * <li>Bonds: the face still outstanding times the clean price per 100.</li>
 * <li>Bills: the face discounted at the quoted yield for the days from the valuation date to maturity, on the basis the
 * bill was bought on (as {@link DealAccrualCalculator} carries it at the dealt yield); or the face times a price per
 * 100 when only a price is quoted.</li>
 * </ul>
 */
public final class FairValueCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private FairValueCalculator() {}

    /**
     * @return the clean fair value on {@code date}, or {@code null} when the quote does not fit the instrument (a bond
     *         without a clean price, a bill with neither yield nor price)
     */
    public static BigDecimal fairValue(final InstrumentKind kind, final BigDecimal face, final BigDecimal cleanPrice,
            final BigDecimal yield, final DayCount dayCount, final LocalDate valueDate, final LocalDate maturityDate, final LocalDate date,
            final int scale) {
        if (kind == InstrumentKind.GOVERNMENT_BOND) {
            return cleanPrice == null ? null : face.multiply(cleanPrice).divide(HUNDRED, scale, RoundingMode.HALF_UP);
        }
        if (kind != InstrumentKind.TREASURY_BILL) {
            throw new IllegalArgumentException(kind + " holdings are not valued at fair value");
        }
        if (yield == null) {
            return cleanPrice == null ? null : face.multiply(cleanPrice).divide(HUNDRED, scale, RoundingMode.HALF_UP);
        }
        if (!date.isBefore(maturityDate)) {
            return face.setScale(scale, RoundingMode.HALF_UP);
        }
        final BigDecimal days = BigDecimal.valueOf(ChronoUnit.DAYS.between(date, maturityDate));
        final BigDecimal factor = BigDecimal.ONE.add(yield.divide(HUNDRED, DayCount.MC).multiply(days, DayCount.MC)
                .divide(BigDecimal.valueOf(dayCount.basis(valueDate)), DayCount.MC));
        return face.divide(factor, DayCount.MC).setScale(scale, RoundingMode.HALF_UP);
    }
}
