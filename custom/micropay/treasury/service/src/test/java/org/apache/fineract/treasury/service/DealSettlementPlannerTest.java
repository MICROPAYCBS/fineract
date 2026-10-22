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
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.treasury.deal.DayCount;
import org.apache.fineract.treasury.deal.DealPricer;
import org.apache.fineract.treasury.deal.DealPricer.Priced;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.service.DealSettlementPlanner.DealGlAccounts;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;
import org.junit.jupiter.api.Test;

class DealSettlementPlannerTest {

    private static final long OFFICE = 1L;
    private static final long BANK = 10L;
    private static final DealGlAccounts GL = new DealGlAccounts(20L, 21L, 22L, 23L, 24L);

    @Test
    void bondAtADiscountWithAccruedInterest() {
        final Priced priced = DealPricer.price(new DealPricer.Terms(InstrumentKind.GOVERNMENT_BOND, new BigDecimal("100000000"),
                new BigDecimal("14"), new BigDecimal("98.50"), DayCount.ACT_ACT, 2, false, LocalDate.of(2026, 10, 16),
                LocalDate.of(2031, 3, 15), null, 2));

        final List<Leg> legs = DealSettlementPlanner.legs(InstrumentKind.GOVERNMENT_BOND, priced, BigDecimal.ZERO, OFFICE, BANK, GL);

        assertEquals(List.of(new Leg(OFFICE, 20L, JournalEntryType.DEBIT, new BigDecimal("100000000.00")),
                new Leg(OFFICE, 23L, JournalEntryType.CREDIT, new BigDecimal("1500000.00")),
                new Leg(OFFICE, 21L, JournalEntryType.DEBIT, new BigDecimal("1198895.03")),
                new Leg(OFFICE, BANK, JournalEntryType.CREDIT, new BigDecimal("99698895.03"))), legs);
        assertTrue(TreasuryPostingPlanner.isBalancedPerOffice(legs));
    }

    @Test
    void bondAtAPremium() {
        final Priced priced = DealPricer.price(new DealPricer.Terms(InstrumentKind.GOVERNMENT_BOND, new BigDecimal("10000000"),
                new BigDecimal("15"), new BigDecimal("102.25"), DayCount.ACT_ACT, 2, false, LocalDate.of(2026, 9, 15),
                LocalDate.of(2031, 3, 15), null, 0));

        final List<Leg> legs = DealSettlementPlanner.legs(InstrumentKind.GOVERNMENT_BOND, priced, BigDecimal.ZERO, OFFICE, BANK, GL);

        // on a coupon date nothing is accrued
        assertEquals(List.of(new Leg(OFFICE, 20L, JournalEntryType.DEBIT, new BigDecimal("10000000")),
                new Leg(OFFICE, 22L, JournalEntryType.DEBIT, new BigDecimal("225000")),
                new Leg(OFFICE, BANK, JournalEntryType.CREDIT, new BigDecimal("10225000"))), legs);
    }

    @Test
    void placementWithAFee() {
        final Priced priced = DealPricer.price(new DealPricer.Terms(InstrumentKind.MONEY_MARKET_PLACEMENT, new BigDecimal("500000000"),
                new BigDecimal("12"), null, DayCount.ACT_365, 0, false, LocalDate.of(2026, 10, 16), LocalDate.of(2027, 1, 14), null, 0));

        final List<Leg> legs = DealSettlementPlanner.legs(InstrumentKind.MONEY_MARKET_PLACEMENT, priced, new BigDecimal("25000"), OFFICE,
                BANK, GL);

        assertEquals(List.of(new Leg(OFFICE, 20L, JournalEntryType.DEBIT, new BigDecimal("500000000")),
                new Leg(OFFICE, BANK, JournalEntryType.CREDIT, new BigDecimal("500000000")),
                new Leg(OFFICE, 24L, JournalEntryType.DEBIT, new BigDecimal("25000")),
                new Leg(OFFICE, BANK, JournalEntryType.CREDIT, new BigDecimal("25000"))), legs);
        assertEquals(new BigDecimal("500025000"),
                DealSettlementPlanner.cashOut(InstrumentKind.MONEY_MARKET_PLACEMENT, priced, new BigDecimal("25000")));
    }

    @Test
    void borrowingBringsCashIn() {
        final Priced priced = DealPricer.price(new DealPricer.Terms(InstrumentKind.BORROWING, new BigDecimal("2000000000"),
                new BigDecimal("13"), null, DayCount.ACT_365, 0, false, LocalDate.of(2026, 10, 16), LocalDate.of(2027, 10, 16), null, 0));

        final List<Leg> legs = DealSettlementPlanner.legs(InstrumentKind.BORROWING, priced, BigDecimal.ZERO, OFFICE, BANK, GL);

        assertEquals(List.of(new Leg(OFFICE, BANK, JournalEntryType.DEBIT, new BigDecimal("2000000000")),
                new Leg(OFFICE, 20L, JournalEntryType.CREDIT, new BigDecimal("2000000000"))), legs);
        assertEquals(0, DealSettlementPlanner.cashOut(InstrumentKind.BORROWING, priced, BigDecimal.ZERO).signum());
    }
}
