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
package org.apache.fineract.treasury.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.junit.jupiter.api.Test;

class TreasuryWorkflowStatusTest {

    @Test
    void transferMovesForwardOneStepAtATime() {
        assertEquals(Set.of(TreasuryTransferStatus.REQUESTED), matching(TreasuryTransferStatus::canApprove));
        assertEquals(Set.of(TreasuryTransferStatus.APPROVED), matching(TreasuryTransferStatus::canSend));
        assertEquals(Set.of(TreasuryTransferStatus.SENT), matching(TreasuryTransferStatus::canReceive));
    }

    @Test
    void onlyUnsentTransfersCanBeCancelled() {
        assertEquals(Set.of(TreasuryTransferStatus.REQUESTED, TreasuryTransferStatus.APPROVED),
                matching(TreasuryTransferStatus::canCancel));
    }

    @Test
    void floatRequestMovesForwardOneStepAtATime() {
        assertTrue(TreasuryFloatRequestStatus.REQUESTED.canApprove());
        assertFalse(TreasuryFloatRequestStatus.REQUESTED.canExecute());
        assertTrue(TreasuryFloatRequestStatus.APPROVED.canExecute());
        assertFalse(TreasuryFloatRequestStatus.APPROVED.canApprove());
        assertTrue(TreasuryFloatRequestStatus.APPROVED.canCancel());
        assertFalse(TreasuryFloatRequestStatus.EXECUTED.canCancel());
        assertFalse(TreasuryFloatRequestStatus.CANCELLED.canExecute());
    }

    @Test
    void onlyPostedStepsCanBeReversed() {
        assertEquals(Set.of(TreasuryTransferStatus.SENT, TreasuryTransferStatus.RECEIVED), matching(TreasuryTransferStatus::canReverse));
        assertTrue(TreasuryFloatRequestStatus.EXECUTED.canReverse());
        assertFalse(TreasuryFloatRequestStatus.APPROVED.canReverse());
        assertFalse(TreasuryFloatRequestStatus.REVERSED.canReverse());
    }

    @Test
    void sameUserCannotTakeTwoStepsInARow() {
        final TreasuryWorkflowException exception = assertThrows(TreasuryWorkflowException.class,
                () -> TreasurySegregation.requireDifferentUser("transfer", 5L, "requested", 7L, "approve", 7L));
        assertEquals("error.msg.treasury.transfer.same.user", exception.getGlobalisationMessageCode());
    }

    @Test
    void differentUserOrNoPreviousUserIsAllowed() {
        assertDoesNotThrow(() -> TreasurySegregation.requireDifferentUser("transfer", 5L, "requested", 7L, "approve", 8L));
        assertDoesNotThrow(() -> TreasurySegregation.requireDifferentUser("transfer", 5L, "requested", null, "approve", 8L));
    }

    private static Set<TreasuryTransferStatus> matching(final Predicate<TreasuryTransferStatus> predicate) {
        final Set<TreasuryTransferStatus> result = EnumSet.noneOf(TreasuryTransferStatus.class);
        for (final TreasuryTransferStatus status : TreasuryTransferStatus.values()) {
            if (predicate.test(status)) {
                result.add(status);
            }
        }
        return result;
    }
}
