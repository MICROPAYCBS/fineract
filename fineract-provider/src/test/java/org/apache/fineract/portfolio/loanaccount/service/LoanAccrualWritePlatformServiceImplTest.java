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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanInterestRecalculationDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanAccrualWritePlatformServiceImplTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 2);
    private static final Long LOAN_ID = 15L;

    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;
    @Mock
    private LoanAccrualsProcessingService loanAccrualsProcessingService;
    @Mock
    private FromJsonHelper fromApiJsonHelper;
    @Mock
    private JsonCommand command;
    @Mock
    private Loan loan;

    @InjectMocks
    private LoanAccrualWritePlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE)));
        when(command.getLoanId()).thenReturn(LOAN_ID);
        when(loanRepositoryWrapper.findOneWithNotFoundDetection(LOAN_ID, true)).thenReturn(loan);
        lenient().when(loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()).thenReturn(true);
        lenient().when(loan.isOpen()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void accrueLoan_postsPeriodicAccrualsThroughBusinessDateWhenTillDateIsOmitted() throws Exception {
        when(loan.getId()).thenReturn(LOAN_ID);
        when(command.json()).thenReturn(null);

        final CommandProcessingResult result = underTest.accrueLoan(command);

        assertEquals(LOAN_ID, result.getResourceId());
        verify(loanAccrualsProcessingService).addPeriodicAccruals(BUSINESS_DATE, loan);
        verify(loanRepositoryWrapper).saveAndFlush(loan);
        verify(loanAccrualsProcessingService, never()).addIncomePostingAndAccruals(LOAN_ID);
    }

    @Test
    void accrueLoan_postsIncomeAccrualsWhenCompoundingIsPostedAsATransaction() throws Exception {
        final LoanInterestRecalculationDetails recalculationDetails = org.mockito.Mockito.mock(LoanInterestRecalculationDetails.class);
        when(recalculationDetails.isCompoundingToBePostedAsTransaction()).thenReturn(true);
        when(loan.getLoanInterestRecalculationDetails()).thenReturn(recalculationDetails);
        when(loan.isProgressiveSchedule()).thenReturn(false);
        when(loan.getId()).thenReturn(LOAN_ID);

        underTest.accrueLoan(command);

        verify(loanAccrualsProcessingService).addIncomePostingAndAccruals(LOAN_ID);
        verify(loanAccrualsProcessingService, never()).addPeriodicAccruals(BUSINESS_DATE, loan);
    }

    @Test
    void accrueLoan_rejectsALoanThatDoesNotUsePeriodicAccrualAccounting() {
        when(loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()).thenReturn(false);

        assertThrows(GeneralPlatformDomainRuleException.class, () -> underTest.accrueLoan(command));
    }

    @Test
    void accrueLoan_rejectsATillDateAfterTheBusinessDate() {
        when(command.json()).thenReturn("{\"tillDate\":\"03 October 2026\"}");
        when(command.parameterExists("tillDate")).thenReturn(true);
        when(command.localDateValueOfParameterNamed("tillDate")).thenReturn(BUSINESS_DATE.plusDays(1));

        assertThrows(PlatformApiDataValidationException.class, () -> underTest.accrueLoan(command));
    }
}
