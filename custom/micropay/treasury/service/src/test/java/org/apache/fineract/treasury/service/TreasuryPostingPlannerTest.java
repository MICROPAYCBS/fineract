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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;
import org.junit.jupiter.api.Test;

class TreasuryPostingPlannerTest {

    private static final BigDecimal AMOUNT = new BigDecimal("1500000");
    private static final long SOURCE_OFFICE = 1L;
    private static final long DESTINATION_OFFICE = 2L;
    private static final long SOURCE_GL = 10L;
    private static final long DESTINATION_GL = 20L;
    private static final long IN_TRANSIT_GL = 30L;
    private static final long CLEARING_GL = 40L;

    @Test
    void sendMovesTheSourceToCashInTransit() {
        final List<Leg> legs = TreasuryPostingPlanner.transferSend(SOURCE_OFFICE, SOURCE_GL, IN_TRANSIT_GL, AMOUNT);

        assertEquals(List.of(new Leg(SOURCE_OFFICE, IN_TRANSIT_GL, JournalEntryType.DEBIT, AMOUNT),
                new Leg(SOURCE_OFFICE, SOURCE_GL, JournalEntryType.CREDIT, AMOUNT)), legs);
        assertTrue(TreasuryPostingPlanner.isBalancedPerOffice(legs));
    }

    @Test
    void receiveInTheSameOfficeClearsCashInTransit() {
        final List<Leg> legs = TreasuryPostingPlanner.transferReceive(SOURCE_OFFICE, SOURCE_OFFICE, DESTINATION_GL, IN_TRANSIT_GL, null,
                AMOUNT);

        assertEquals(List.of(new Leg(SOURCE_OFFICE, DESTINATION_GL, JournalEntryType.DEBIT, AMOUNT),
                new Leg(SOURCE_OFFICE, IN_TRANSIT_GL, JournalEntryType.CREDIT, AMOUNT)), legs);
        assertTrue(TreasuryPostingPlanner.isBalancedPerOffice(legs));
    }

    @Test
    void receiveAcrossOfficesBalancesEachOfficeThroughClearing() {
        final List<Leg> legs = TreasuryPostingPlanner.transferReceive(SOURCE_OFFICE, DESTINATION_OFFICE, DESTINATION_GL, IN_TRANSIT_GL,
                CLEARING_GL, AMOUNT);

        assertEquals(4, legs.size());
        assertTrue(legs.contains(new Leg(DESTINATION_OFFICE, DESTINATION_GL, JournalEntryType.DEBIT, AMOUNT)));
        assertTrue(legs.contains(new Leg(DESTINATION_OFFICE, CLEARING_GL, JournalEntryType.CREDIT, AMOUNT)));
        assertTrue(legs.contains(new Leg(SOURCE_OFFICE, CLEARING_GL, JournalEntryType.DEBIT, AMOUNT)));
        // cash in transit is cleared in the office the send put it in
        assertTrue(legs.contains(new Leg(SOURCE_OFFICE, IN_TRANSIT_GL, JournalEntryType.CREDIT, AMOUNT)));
        assertTrue(TreasuryPostingPlanner.isBalancedPerOffice(legs));
    }

    @Test
    void sendAndReceiveTogetherLeaveCashInTransitAtZero() {
        final List<Leg> send = TreasuryPostingPlanner.transferSend(SOURCE_OFFICE, SOURCE_GL, IN_TRANSIT_GL, AMOUNT);
        final List<Leg> receive = TreasuryPostingPlanner.transferReceive(SOURCE_OFFICE, DESTINATION_OFFICE, DESTINATION_GL, IN_TRANSIT_GL,
                CLEARING_GL, AMOUNT);

        BigDecimal inTransit = BigDecimal.ZERO;
        for (final Leg leg : Stream.concat(send.stream(), receive.stream()).toList()) {
            if (leg.glAccountId() == IN_TRANSIT_GL) {
                inTransit = leg.type() == JournalEntryType.DEBIT ? inTransit.add(leg.amount()) : inTransit.subtract(leg.amount());
            }
        }
        assertEquals(0, inTransit.signum());
    }

    @Test
    void crossOfficeReceiveNeedsAClearingAccount() {
        assertThrows(NullPointerException.class, () -> TreasuryPostingPlanner.transferReceive(SOURCE_OFFICE, DESTINATION_OFFICE,
                DESTINATION_GL, IN_TRANSIT_GL, null, AMOUNT));
    }

    @Test
    void unbalancedOfficeIsDetected() {
        assertFalse(TreasuryPostingPlanner.isBalancedPerOffice(List.of(new Leg(SOURCE_OFFICE, SOURCE_GL, JournalEntryType.DEBIT, AMOUNT),
                new Leg(DESTINATION_OFFICE, DESTINATION_GL, JournalEntryType.CREDIT, AMOUNT))));
    }
}
