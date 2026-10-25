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
package org.apache.fineract.fraud.hold.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.fineract.fraud.hold.data.FraudHoldEffectData;
import org.apache.fineract.fraud.hold.data.FraudHoldEffectType;
import org.apache.fineract.fraud.hold.data.FraudHoldEntityType;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.service.LoanWritePlatformService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FraudHoldEffectServiceTest {

    private static final Long HOLD_ID = 10L;

    @Mock
    private FraudHoldRepository holdRepository;
    @Mock
    private FraudHoldTargetRepository targetRepository;
    @Mock
    private SavingsAccountWritePlatformService savingsService;
    @Mock
    private LoanWritePlatformService loanService;

    private FraudHoldEffectService underTest;

    @BeforeEach
    void setUp() {
        underTest = new FraudHoldEffectService(holdRepository, targetRepository, savingsService, loanService, new FromJsonHelper());
    }

    @Test
    void savingsHoldBlocksDebitsAndRecordsEffect() {
        when(targetRepository.isSavingsAccountActive(5L)).thenReturn(true);
        when(targetRepository.areSavingsDebitsBlocked(5L)).thenReturn(false);

        underTest.applyEffects(HOLD_ID, FraudHoldEntityType.SAVINGS_ACCOUNT, 5L, "velocity spike");

        verify(savingsService).blockDebits(eq(5L),
                argThat(cmd -> cmd.stringValueOfParameterNamed("reasonForBlock").equals("Fraud hold 10: velocity spike")));
        verify(holdRepository).insertEffect(HOLD_ID, FraudHoldEffectType.SAVINGS_DEBIT_BLOCK, 5L);
    }

    @Test
    void savingsHoldLeavesAlreadyBlockedAccountAlone() {
        when(targetRepository.isSavingsAccountActive(5L)).thenReturn(true);
        when(targetRepository.areSavingsDebitsBlocked(5L)).thenReturn(true);

        underTest.applyEffects(HOLD_ID, FraudHoldEntityType.SAVINGS_ACCOUNT, 5L, "reason");

        verify(savingsService, never()).blockDebits(anyLong(), any());
        verify(holdRepository, never()).insertEffect(any(), any(), any());
    }

    @Test
    void clientHoldBlocksEveryActiveSavingsAccount() {
        when(targetRepository.findActiveSavingsAccountIdsOfClient(3L)).thenReturn(List.of(7L, 8L));
        when(targetRepository.isSavingsAccountActive(anyLong())).thenReturn(true);
        when(targetRepository.areSavingsDebitsBlocked(anyLong())).thenReturn(false);

        underTest.applyEffects(HOLD_ID, FraudHoldEntityType.CLIENT, 3L, "watchlist hit");

        verify(holdRepository).insertEffect(HOLD_ID, FraudHoldEffectType.SAVINGS_DEBIT_BLOCK, 7L);
        verify(holdRepository).insertEffect(HOLD_ID, FraudHoldEffectType.SAVINGS_DEBIT_BLOCK, 8L);
    }

    @Test
    void loanHoldFlagsLoanAsFraud() {
        when(targetRepository.isLoanFlaggedAsFraud(4L)).thenReturn(false);

        underTest.applyEffects(HOLD_ID, FraudHoldEntityType.LOAN, 4L, "reason");

        verify(loanService).markLoanAsFraud(eq(4L), argThat(cmd -> cmd.booleanPrimitiveValueOfParameterNamed("fraud")));
        verify(holdRepository).insertEffect(HOLD_ID, FraudHoldEffectType.LOAN_FRAUD_FLAG, 4L);
    }

    @Test
    void releaseUnblocksWhenNoOtherHoldCoversTheAccount() {
        final FraudHoldEffectData effect = new FraudHoldEffectData(1L, HOLD_ID, FraudHoldEffectType.SAVINGS_DEBIT_BLOCK, 5L);
        when(holdRepository.findEffects(HOLD_ID)).thenReturn(List.of(effect));
        when(holdRepository.findOtherActiveHoldCoveringSavings(5L, HOLD_ID)).thenReturn(Optional.empty());
        when(targetRepository.areSavingsDebitsBlocked(5L)).thenReturn(true);

        underTest.revertEffects(HOLD_ID);

        verify(savingsService).unblockDebits(5L);
        verify(holdRepository).deleteEffect(1L);
    }

    @Test
    void releaseHandsTheBlockToAnotherActiveHold() {
        final FraudHoldEffectData effect = new FraudHoldEffectData(1L, HOLD_ID, FraudHoldEffectType.SAVINGS_DEBIT_BLOCK, 5L);
        when(holdRepository.findEffects(HOLD_ID)).thenReturn(List.of(effect));
        when(holdRepository.findOtherActiveHoldCoveringSavings(5L, HOLD_ID)).thenReturn(Optional.of(11L));

        underTest.revertEffects(HOLD_ID);

        verify(holdRepository).reassignEffect(1L, 11L);
        verify(savingsService, never()).unblockDebits(anyLong());
        verify(holdRepository, never()).deleteEffect(anyLong());
    }

    @Test
    void releaseUnflagsLoan() {
        final FraudHoldEffectData effect = new FraudHoldEffectData(2L, HOLD_ID, FraudHoldEffectType.LOAN_FRAUD_FLAG, 4L);
        when(holdRepository.findEffects(HOLD_ID)).thenReturn(List.of(effect));
        when(holdRepository.findOtherActiveHoldCoveringLoan(4L, HOLD_ID)).thenReturn(Optional.empty());
        when(targetRepository.isLoanFlaggedAsFraud(4L)).thenReturn(true);

        underTest.revertEffects(HOLD_ID);

        verify(loanService).markLoanAsFraud(eq(4L), argThat(cmd -> !cmd.booleanPrimitiveValueOfParameterNamed("fraud")));
        verify(holdRepository).deleteEffect(2L);
    }
}
