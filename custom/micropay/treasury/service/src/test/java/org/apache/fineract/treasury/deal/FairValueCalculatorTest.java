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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FairValueCalculatorTest {

    private static final BigDecimal FACE = new BigDecimal("1000000000");
    private static final LocalDate VALUE = LocalDate.of(2026, 10, 16);
    private static final LocalDate BILL_MATURITY = LocalDate.of(2027, 1, 15);

    @Test
    void bondAtItsCleanPrice() {
        assertEquals(new BigDecimal("101250000.00"),
                FairValueCalculator.fairValue(InstrumentKind.GOVERNMENT_BOND, new BigDecimal("100000000"), new BigDecimal("101.25"), null,
                        DayCount.ACT_ACT, VALUE, LocalDate.of(2031, 3, 15), LocalDate.of(2026, 11, 1), 2));
    }

    @Test
    void billFromItsYieldForTheDaysLeft() {
        // 60 days left at 10.5% on 365: 1,000,000,000 / (1 + 0.105 x 60 / 365)
        assertEquals(new BigDecimal("983032588.20"), FairValueCalculator.fairValue(InstrumentKind.TREASURY_BILL, FACE, null,
                new BigDecimal("10.5"), DayCount.ACT_365, VALUE, BILL_MATURITY, LocalDate.of(2026, 11, 16), 2));
    }

    @Test
    void billAtTheDealtYieldIsItsAmortisedCost() {
        // the accrual carries the bill at 11% for the days to run: 982,238,966.63 after 31 of 91 days
        assertEquals(new BigDecimal("982238966.63"), FairValueCalculator.fairValue(InstrumentKind.TREASURY_BILL, FACE, null,
                new BigDecimal("11"), DayCount.ACT_365, VALUE, BILL_MATURITY, LocalDate.of(2026, 11, 16), 2));
    }

    @Test
    void billQuotedAsAPriceAndAtMaturity() {
        assertEquals(new BigDecimal("980000000.00"), FairValueCalculator.fairValue(InstrumentKind.TREASURY_BILL, FACE, new BigDecimal("98"),
                null, DayCount.ACT_365, VALUE, BILL_MATURITY, LocalDate.of(2026, 11, 16), 2));
        assertEquals(new BigDecimal("1000000000.00"), FairValueCalculator.fairValue(InstrumentKind.TREASURY_BILL, FACE, null,
                new BigDecimal("11"), DayCount.ACT_365, VALUE, BILL_MATURITY, BILL_MATURITY, 2));
    }

    @Test
    void aQuoteThatDoesNotFitGivesNoValue() {
        assertNull(FairValueCalculator.fairValue(InstrumentKind.GOVERNMENT_BOND, FACE, null, new BigDecimal("14"), DayCount.ACT_ACT, VALUE,
                LocalDate.of(2031, 3, 15), LocalDate.of(2026, 11, 1), 2));
        assertNull(FairValueCalculator.fairValue(InstrumentKind.TREASURY_BILL, FACE, null, null, DayCount.ACT_365, VALUE, BILL_MATURITY,
                LocalDate.of(2026, 11, 1), 2));
        assertThrows(IllegalArgumentException.class, () -> FairValueCalculator.fairValue(InstrumentKind.MONEY_MARKET_PLACEMENT, FACE,
                BigDecimal.TEN, null, DayCount.ACT_365, VALUE, BILL_MATURITY, VALUE, 2));
    }

    @Test
    void eclIsExposureTimesPdTimesLgd() {
        // 507,397,260.27 x 0.5% x 45%; stage 2 takes the lifetime PD of 4%
        assertEquals(new BigDecimal("1141643.84"),
                EclCalculator.ecl(new BigDecimal("507397260.27"), new BigDecimal("0.5"), new BigDecimal("45"), 2));
        assertEquals(new BigDecimal("9133150.68"), EclCalculator.ecl(new BigDecimal("507397260.27"),
                EclCalculator.pd(2, new BigDecimal("0.5"), new BigDecimal("4")), new BigDecimal("45"), 2));
        assertEquals(new BigDecimal("0.00"), EclCalculator.ecl(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, 2));
    }
}
