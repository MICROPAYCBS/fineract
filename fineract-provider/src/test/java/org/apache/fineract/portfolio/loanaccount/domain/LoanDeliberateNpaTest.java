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
package org.apache.fineract.portfolio.loanaccount.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;

class LoanDeliberateNpaTest {

    @Test
    void markDeliberateNpaSetsFlagReasonAndNote() {
        final Loan loan = new Loan();
        final CodeValue reason = mock(CodeValue.class);
        final AppUser user = mock(AppUser.class);

        loan.markDeliberateNpa(reason, "Borrower fraud", user, LocalDate.of(2026, 10, 15));

        assertTrue(loan.isNpa());
        assertTrue(loan.isDeliberateNpa());
        assertEquals(reason, loan.getNpaReason());
        assertEquals("Borrower fraud", loan.getNpaReasonNote());
        assertEquals(LocalDate.of(2026, 10, 15), loan.getDeliberateNpaOnDate());
    }

    @Test
    void undoWhileStillOverdueKeepsNpaFlag() {
        final Loan loan = classifiedLoan();

        loan.undoDeliberateNpa(true);

        assertTrue(loan.isNpa());
        assertFalse(loan.isDeliberateNpa());
        assertNull(loan.getNpaReason());
        assertNull(loan.getNpaReasonNote());
        assertNull(loan.getDeliberateNpaOnDate());
    }

    @Test
    void undoBeforeOverdueThresholdClearsNpaFlag() {
        final Loan loan = classifiedLoan();

        loan.undoDeliberateNpa(false);

        assertFalse(loan.isNpa());
        assertFalse(loan.isDeliberateNpa());
        assertNull(loan.getNpaReasonNote());
    }

    private static Loan classifiedLoan() {
        final Loan loan = new Loan();
        loan.markDeliberateNpa(mock(CodeValue.class), "Court case", mock(AppUser.class), LocalDate.of(2026, 10, 1));
        return loan;
    }
}
