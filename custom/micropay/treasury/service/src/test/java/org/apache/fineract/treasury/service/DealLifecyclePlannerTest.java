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
package org.apache.fineract.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.treasury.deal.DealCalculator.FlowType;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.service.DealLifecyclePlanner.Carrying;
import org.apache.fineract.treasury.service.DealLifecyclePlanner.Gl;
import org.apache.fineract.treasury.service.DealLifecyclePlanner.Termination;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;
import org.junit.jupiter.api.Test;

class DealLifecyclePlannerTest {

    private static final long OFFICE = 1L;
    /**
     * settlement 10, principal 20, receivable 21, payable 22, income 23, expense 24, premium 25, discount 26, deferred
     * fee 27, fee 28, gain 29, loss 30
     */
    private static final Gl GL = new Gl(10L, 20L, 21L, 22L, 23L, 24L, 25L, 26L, 27L, 28L, 29L, 30L);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test
    void accrualOfAPlacementWithAFee() {
        final List<Leg> legs = DealLifecyclePlanner.accrual(InstrumentKind.MONEY_MARKET_PLACEMENT, new BigDecimal("164383.56"), ZERO,
                new BigDecimal("1000"), false, OFFICE, GL);

        assertEquals(List.of(leg(21, true, "164383.56"), leg(23, false, "164383.56"), leg(28, true, "1000"), leg(27, false, "1000")), legs);
    }

    @Test
    void billDiscountGrowsThePrincipal() {
        assertEquals(List.of(leg(20, true, "290000"), leg(23, false, "290000")),
                DealLifecyclePlanner.accrual(InstrumentKind.TREASURY_BILL, new BigDecimal("290000"), ZERO, ZERO, false, OFFICE, GL));
    }

    @Test
    void bondAccretesADiscountOrAmortisesAPremium() {
        assertEquals(List.of(leg(21, true, "77000"), leg(23, false, "77000"), leg(26, true, "500"), leg(23, false, "500")),
                DealLifecyclePlanner.accrual(InstrumentKind.GOVERNMENT_BOND, new BigDecimal("77000"), new BigDecimal("500"), ZERO, false,
                        OFFICE, GL));
        assertEquals(List.of(leg(23, true, "300"), leg(25, false, "300")),
                DealLifecyclePlanner.accrual(InstrumentKind.GOVERNMENT_BOND, ZERO, new BigDecimal("-300"), ZERO, true, OFFICE, GL));
    }

    @Test
    void borrowingAccruesExpense() {
        assertEquals(List.of(leg(24, true, "712328.77"), leg(22, false, "712328.77")),
                DealLifecyclePlanner.accrual(InstrumentKind.BORROWING, new BigDecimal("712328.77"), ZERO, ZERO, false, OFFICE, GL));
    }

    @Test
    void couponReceivedAndPrincipalRepaid() {
        assertEquals(List.of(leg(10, true, "7000000"), leg(21, false, "7000000")),
                DealLifecyclePlanner.cashflow(InstrumentKind.GOVERNMENT_BOND, FlowType.INTEREST, new BigDecimal("7000000"), OFFICE, GL));
        assertEquals(List.of(leg(20, true, "500000000"), leg(10, false, "500000000")),
                DealLifecyclePlanner.cashflow(InstrumentKind.BORROWING, FlowType.PRINCIPAL, new BigDecimal("500000000"), OFFICE, GL));
    }

    @Test
    void placementTerminatedAtALoss() {
        // 500,000,000 placed, 7,397,260.27 accrued after 45 days; the bank pays back 507,000,000
        final Termination t = DealLifecyclePlanner.termination(InstrumentKind.MONEY_MARKET_PLACEMENT,
                new Carrying(new BigDecimal("500000000"), ZERO, ZERO, new BigDecimal("7397260.27"), ZERO), new BigDecimal("507000000"),
                ZERO, OFFICE, GL);

        assertEquals(new BigDecimal("-397260.27"), t.realisedResult());
        assertEquals(
                List.of(leg(10, true, "507000000"), leg(20, false, "500000000"), leg(21, false, "7397260.27"), leg(30, true, "397260.27")),
                t.legs());
        assertTrue(TreasuryPostingPlanner.isBalancedPerOffice(t.legs()));
    }

    @Test
    void billSoldAtAGain() {
        // carried at 973,307,378.47 + 8,640,914.55 earned; sold for 985,000,000
        final Termination t = DealLifecyclePlanner.termination(InstrumentKind.TREASURY_BILL,
                new Carrying(new BigDecimal("981948293.02"), ZERO, ZERO, ZERO, ZERO), new BigDecimal("985000000"), ZERO, OFFICE, GL);

        assertEquals(new BigDecimal("3051706.98"), t.realisedResult());
        assertEquals(List.of(leg(10, true, "985000000"), leg(20, false, "981948293.02"), leg(29, false, "3051706.98")), t.legs());
    }

    @Test
    void bondSoldWithItsUnamortisedDiscountAndABreakCost() {
        final Termination t = DealLifecyclePlanner.termination(InstrumentKind.GOVERNMENT_BOND,
                new Carrying(new BigDecimal("100000000"), ZERO, new BigDecimal("1413040.31"), new BigDecimal("2000000"), ZERO),
                new BigDecimal("101000000"), new BigDecimal("50000"), OFFICE, GL);

        // carrying 100,000,000 - 1,413,040.31 + 2,000,000 = 100,586,959.69; gain 413,040.31 less the 50,000 break cost
        assertEquals(new BigDecimal("363040.31"), t.realisedResult());
        assertTrue(TreasuryPostingPlanner.isBalancedPerOffice(t.legs()));
        assertTrue(t.legs().contains(leg(26, true, "1413040.31")));
        assertTrue(t.legs().contains(leg(30, true, "50000")));
    }

    @Test
    void borrowingRepaidEarlyWithAPenalty() {
        // owes 2,000,000,000 and 10,000,000 interest; repays 2,015,000,000
        final Termination t = DealLifecyclePlanner.termination(InstrumentKind.BORROWING,
                new Carrying(new BigDecimal("2000000000"), ZERO, ZERO, new BigDecimal("10000000"), ZERO), new BigDecimal("2015000000"),
                ZERO, OFFICE, GL);

        assertEquals(new BigDecimal("-5000000"), t.realisedResult());
        assertEquals(
                List.of(leg(20, true, "2000000000"), leg(22, true, "10000000"), leg(10, false, "2015000000"), leg(30, true, "5000000")),
                t.legs());
    }

    @Test
    void residualReleasedAtMaturity() {
        assertEquals(List.of(leg(26, true, "1500000"), leg(23, false, "1500000")),
                DealLifecyclePlanner.residual(ZERO, new BigDecimal("1500000"), ZERO, OFFICE, GL));
    }

    private static Leg leg(final long gl, final boolean debit, final String amount) {
        return new Leg(OFFICE, gl, debit ? JournalEntryType.DEBIT : JournalEntryType.CREDIT, new BigDecimal(amount));
    }
}
