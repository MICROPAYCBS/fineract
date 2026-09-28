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

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;

/**
 * Rolls a monthly or annual channel fee past cycles whose due date fell inside a closed hold. Cycles already due
 * before the hold started are left in place so that outstanding amount stays collectible. No payment is posted.
 */
public final class SavingsPaymentChannelFeeCycle {

    private static final int MAX_CYCLES = 360;

    private SavingsPaymentChannelFeeCycle() {}

    public record HoldWindow(LocalDate start, LocalDate end) {}

    public static boolean fellDuringHold(final LocalDate dueDate, final LocalDate holdStart, final LocalDate holdEnd) {
        return dueDate != null && holdStart != null && holdEnd != null && !dueDate.isBefore(holdStart) && dueDate.isBefore(holdEnd);
    }

    /**
     * @return true when at least one cycle was skipped
     */
    public static boolean skipCyclesDuringHolds(final SavingsAccountCharge charge, final List<HoldWindow> holds) {
        if (charge == null || holds == null || holds.isEmpty() || !isPausableFee(charge)) {
            return false;
        }
        boolean moved = false;
        for (int guard = 0; guard < MAX_CYCLES && fallsInAnyHold(charge.getDueDate(), holds); guard++) {
            final LocalDate before = charge.getDueDate();
            charge.updateNextDueDateForRecurringFees();
            if (charge.getDueDate() == null || !charge.getDueDate().isAfter(before)) {
                break;
            }
            charge.resetPropertiesForRecurringFees();
            moved = true;
        }
        return moved;
    }

    public static boolean isPausableFee(final SavingsAccountCharge charge) {
        return (charge.isMonthlyFee() || charge.isAnnualFee()) && charge.isActive() && !charge.isRecurrenceEnded();
    }

    private static boolean fallsInAnyHold(final LocalDate dueDate, final List<HoldWindow> holds) {
        for (final HoldWindow hold : holds) {
            if (fellDuringHold(dueDate, hold.start(), hold.end())) {
                return true;
            }
        }
        return false;
    }
}
