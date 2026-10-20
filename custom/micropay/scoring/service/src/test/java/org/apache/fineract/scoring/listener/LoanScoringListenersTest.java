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
package org.apache.fineract.scoring.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.scoring.domain.ScoringDecision;
import org.apache.fineract.scoring.domain.ScoringMode;
import org.apache.fineract.scoring.domain.ScoringOutcome;
import org.apache.fineract.scoring.domain.ScoringProductConfig;
import org.apache.fineract.scoring.domain.ScoringTrigger;
import org.apache.fineract.scoring.exception.ScoringDeclinedException;
import org.apache.fineract.scoring.exception.ScoringLimitExceededException;
import org.apache.fineract.scoring.limit.LimitCheckService;
import org.apache.fineract.scoring.service.ScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanScoringListenersTest {

    private static final Long PRODUCT_ID = 7L;

    @Mock
    private BusinessEventNotifierService businessEventNotifierService;

    @Mock
    private ScoringService scoringService;

    @Mock
    private LimitCheckService limitCheckService;

    private LoanScoringListeners listeners;
    private Loan loan;

    @BeforeEach
    void setUp() {
        listeners = new LoanScoringListeners(businessEventNotifierService, scoringService, limitCheckService);
        loan = mock(Loan.class);
        final Money principal = mock(Money.class);
        when(principal.getAmount()).thenReturn(new BigDecimal("1000000"));
        when(loan.getId()).thenReturn(42L);
        when(loan.getClientId()).thenReturn(9L);
        when(loan.productId()).thenReturn(PRODUCT_ID);
        when(loan.getPrincipal()).thenReturn(principal);
        when(loan.getNumberOfRepayments()).thenReturn(6);
    }

    @Test
    void enforcedDeclineBlocksApproval() {
        configure(ScoringMode.ENFORCED);
        decide(ScoringOutcome.DECLINE);

        assertThrows(ScoringDeclinedException.class, () -> listeners.onApproved(loan));
    }

    @Test
    void overriddenDeclineLetsEnforcedApprovalContinue() {
        configure(ScoringMode.ENFORCED);
        decide(ScoringOutcome.DECLINE);
        when(scoringService.effectiveOutcome(any())).thenReturn(ScoringOutcome.REFER);

        assertDoesNotThrow(() -> listeners.onApproved(loan));
    }

    @Test
    void overrideToDeclineBlocksEnforcedApproval() {
        configure(ScoringMode.ENFORCED);
        decide(ScoringOutcome.APPROVE);
        when(scoringService.effectiveOutcome(any())).thenReturn(ScoringOutcome.DECLINE);

        assertThrows(ScoringDeclinedException.class, () -> listeners.onApproved(loan));
    }

    @Test
    void enforcedDeclineAtDisbursalStopsIt() {
        configure(ScoringMode.ENFORCED);
        decide(ScoringOutcome.DECLINE);

        assertThrows(ScoringDeclinedException.class, () -> listeners.onDisbursing(loan));
        verify(scoringService).scoreAndRecord(any(), any(), eq(ScoringTrigger.DISBURSE));
    }

    @Test
    void limitIsCheckedAtApprovalAndDisbursalEvenWithoutScoring() {
        when(scoringService.findActiveConfig(PRODUCT_ID)).thenReturn(Optional.empty());

        listeners.onApproved(loan);
        listeners.onDisbursing(loan);

        verify(limitCheckService, times(2)).check(42L, 9L, PRODUCT_ID, new BigDecimal("1000000"));
    }

    @Test
    void limitBreachStopsApproval() {
        when(scoringService.findActiveConfig(PRODUCT_ID)).thenReturn(Optional.empty());
        doThrow(new ScoringLimitExceededException(42L, "AGENT_FLOAT")).when(limitCheckService).check(any(), any(), any(), any());

        assertThrows(ScoringLimitExceededException.class, () -> listeners.onApproved(loan));
    }

    @Test
    void advisoryDeclineOnlyRecords() {
        configure(ScoringMode.ADVISORY);
        decide(ScoringOutcome.DECLINE);

        assertDoesNotThrow(() -> listeners.onApproved(loan));
        verify(scoringService).scoreAndRecord(any(), any(), eq(ScoringTrigger.APPROVE));
    }

    @Test
    void enforcedReferDoesNotBlock() {
        configure(ScoringMode.ENFORCED);
        decide(ScoringOutcome.REFER);

        assertDoesNotThrow(() -> listeners.onApproved(loan));
    }

    @Test
    void enforcedScoringFailureBlocksApproval() {
        configure(ScoringMode.ENFORCED);
        when(scoringService.scoreAndRecord(any(), any(), any())).thenThrow(new IllegalStateException("no scorecard"));

        assertThrows(IllegalStateException.class, () -> listeners.onApproved(loan));
    }

    @Test
    void advisoryScoringFailureLetsApprovalContinue() {
        configure(ScoringMode.ADVISORY);
        when(scoringService.scoreAndRecord(any(), any(), any())).thenThrow(new IllegalStateException("bad datatable"));

        assertDoesNotThrow(() -> listeners.onApproved(loan));
    }

    @Test
    void unconfiguredProductIsNotScored() {
        when(scoringService.findActiveConfig(PRODUCT_ID)).thenReturn(Optional.empty());

        listeners.onApproved(loan);
        listeners.onSubmitted(loan);

        verify(scoringService, never()).scoreAndRecord(any(), any(), any());
    }

    @Test
    void groupLoanIsNotScored() {
        when(loan.getClientId()).thenReturn(null);

        listeners.onApproved(loan);

        verify(scoringService, never()).findActiveConfig(any());
    }

    @Test
    void submissionScoresAndNeverThrows() {
        configure(ScoringMode.ENFORCED);
        when(scoringService.scoreAndRecord(any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        assertDoesNotThrow(() -> listeners.onSubmitted(loan));
        verify(scoringService).scoreAndRecord(any(), any(), eq(ScoringTrigger.SUBMIT));
    }

    private void configure(final ScoringMode mode) {
        when(scoringService.findActiveConfig(PRODUCT_ID)).thenReturn(Optional.of(ScoringProductConfig.create(PRODUCT_ID, "AGENT", mode)));
    }

    private void decide(final ScoringOutcome outcome) {
        final ScoringDecision decision = new ScoringDecision();
        decision.setOutcome(outcome);
        decision.setReasonCodes("ID_EXPIRED");
        when(scoringService.scoreAndRecord(any(), any(), any())).thenReturn(decision);
        when(scoringService.effectiveOutcome(decision)).thenReturn(outcome);
    }
}
