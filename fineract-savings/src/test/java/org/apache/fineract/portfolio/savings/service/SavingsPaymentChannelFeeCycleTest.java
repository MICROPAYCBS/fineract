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
package org.apache.fineract.portfolio.savings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.service.SavingsPaymentChannelFeeCycle.HoldWindow;
import org.junit.jupiter.api.Test;

class SavingsPaymentChannelFeeCycleTest {

    private static final BigDecimal FEE = new BigDecimal("50");
    private static final LocalDate HOLD_START = LocalDate.of(2026, 1, 10);
    private static final LocalDate HOLD_END = LocalDate.of(2026, 3, 5);

    @Test
    void dueDateInsideHoldRollsToFirstCycleOnOrAfterHoldEnd() {
        final SavingsAccountCharge charge = monthlyFee(LocalDate.of(2026, 1, 15));

        final boolean moved = SavingsPaymentChannelFeeCycle.skipCyclesDuringHolds(charge, List.of(new HoldWindow(HOLD_START, HOLD_END)));

        assertTrue(moved);
        assertEquals(LocalDate.of(2026, 3, 15), charge.getDueDate());
        assertEquals(0, charge.amoutOutstanding().compareTo(FEE));
        assertTrue(charge.isActive());
        assertFalse(charge.isRecurrenceEnded());
    }

    @Test
    void cycleAlreadyDueBeforeHoldStaysCollectible() {
        final SavingsAccountCharge charge = monthlyFee(LocalDate.of(2026, 1, 1));

        final boolean moved = SavingsPaymentChannelFeeCycle.skipCyclesDuringHolds(charge, List.of(new HoldWindow(HOLD_START, HOLD_END)));

        assertFalse(moved);
        assertEquals(LocalDate.of(2026, 1, 1), charge.getDueDate());
        assertEquals(0, charge.amoutOutstanding().compareTo(FEE));
    }

    @Test
    void holdEndIsExclusiveSoCycleDueThatDayIsKept() {
        final LocalDate resume = LocalDate.of(2026, 2, 15);
        final SavingsAccountCharge charge = monthlyFee(resume);

        final boolean moved = SavingsPaymentChannelFeeCycle.skipCyclesDuringHolds(charge,
                List.of(new HoldWindow(LocalDate.of(2026, 1, 15), resume)));

        assertFalse(moved);
        assertEquals(resume, charge.getDueDate());
    }

    @Test
    void withdrawalFeeIsNotRolled() {
        final Charge definition = mock(Charge.class);
        when(definition.isPenalty()).thenReturn(false);
        when(definition.isTiered()).thenReturn(false);
        final LocalDate due = LocalDate.of(2026, 2, 1);
        final SavingsAccountCharge charge = SavingsAccountCharge.createNewWithoutSavingsAccount(definition, FEE,
                ChargeTimeType.WITHDRAWAL_FEE, ChargeCalculationType.FLAT, due, true, null, null);

        final boolean moved = SavingsPaymentChannelFeeCycle.skipCyclesDuringHolds(charge, List.of(new HoldWindow(HOLD_START, HOLD_END)));

        assertFalse(moved);
        assertEquals(due, charge.getDueDate());
    }

    private static SavingsAccountCharge monthlyFee(final LocalDate dueDate) {
        final Charge definition = mock(Charge.class);
        when(definition.isPenalty()).thenReturn(false);
        when(definition.isTiered()).thenReturn(false);
        return SavingsAccountCharge.createNewWithoutSavingsAccount(definition, FEE, ChargeTimeType.MONTHLY_FEE, ChargeCalculationType.FLAT,
                dueDate, true, MonthDay.from(dueDate), 1);
    }
}
