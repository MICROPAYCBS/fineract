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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.arrears.LoanArrearsData;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanDeliberateNpaWritePlatformServiceTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 15);
    private static final Long LOAN_ID = 15L;
    private static final Long REASON_ID = 4L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private LoanAssembler loanAssembler;
    @Mock
    private CodeValueRepositoryWrapper codeValueRepository;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private LoanArrearsAgingService loanArrearsAgingService;
    @Mock
    private JsonCommand command;
    @Mock
    private Loan loan;
    @Mock
    private LoanProduct loanProduct;
    @Mock
    private CodeValue reason;
    @Mock
    private AppUser user;

    @InjectMocks
    private LoanDeliberateNpaWritePlatformService underTest;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE)));
        when(command.getLoanId()).thenReturn(LOAN_ID);
        when(loanAssembler.assembleFrom(LOAN_ID)).thenReturn(loan);
        when(loan.getId()).thenReturn(LOAN_ID);
        when(loan.isOpen()).thenReturn(true);
        when(loan.loanProduct()).thenReturn(loanProduct);
        when(context.authenticatedUser()).thenReturn(user);
        when(loanRepository.saveAndFlush(loan)).thenReturn(loan);
        when(codeValueRepository.findOneByCodeNameAndIdWithNotFoundDetection(LoanApiConstants.LOAN_NPA_REASONS, REASON_ID))
                .thenReturn(reason);
        when(reason.isActive()).thenReturn(true);
        when(command.longValueOfParameterNamed(LoanApiConstants.npaReasonIdParamName)).thenReturn(REASON_ID);
        when(command.stringValueOfParameterNamedAllowingNull(LoanApiConstants.noteParamName)).thenReturn("Borrower fraud");
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void markAsNpaRequiresReason() {
        when(command.longValueOfParameterNamed(LoanApiConstants.npaReasonIdParamName)).thenReturn(null);

        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.markAsNpa(command));

        assertEquals(LoanApiConstants.npaReasonIdParamName, exception.getErrors().get(0).getParameterName());
    }

    @Test
    void markAsNpaStoresOptionalNote() {
        underTest.markAsNpa(command);

        verify(loan).markDeliberateNpa(reason, "Borrower fraud", user, BUSINESS_DATE);
    }

    @Test
    void markAsNpaOmitsBlankNote() {
        when(command.stringValueOfParameterNamedAllowingNull(LoanApiConstants.noteParamName)).thenReturn("  ");

        underTest.markAsNpa(command);

        verify(loan).markDeliberateNpa(eq(reason), isNull(), eq(user), eq(BUSINESS_DATE));
    }

    @Test
    void markAsNpaRejectsInactiveLoan() {
        when(loan.isOpen()).thenReturn(false);

        final GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> underTest.markAsNpa(command));

        assertEquals("error.msg.loan.is.not.active", exception.getGlobalisationMessageCode());
    }

    @Test
    void markAsNpaRejectsLoanAlreadyDeliberatelyClassified() {
        when(loan.isDeliberateNpa()).thenReturn(true);

        final GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> underTest.markAsNpa(command));

        assertEquals("error.msg.loan.npa.already.deliberate", exception.getGlobalisationMessageCode());
    }

    @Test
    void undoWhileStillOverdueKeepsNpa() {
        when(loan.isDeliberateNpa()).thenReturn(true);
        when(loanProduct.getOverdueDaysForNPA()).thenReturn(90);
        when(loanArrearsAgingService.calculateArrearsForLoan(loan)).thenReturn(arrearsSince(LocalDate.of(2026, 6, 1)));

        underTest.undoMarkAsNpa(command);

        verify(loan).undoDeliberateNpa(true);
    }

    @Test
    void undoBeforeOverdueThresholdClearsNpa() {
        when(loan.isDeliberateNpa()).thenReturn(true);
        when(loanProduct.getOverdueDaysForNPA()).thenReturn(90);
        when(loanArrearsAgingService.calculateArrearsForLoan(loan)).thenReturn(arrearsSince(LocalDate.of(2026, 9, 1)));

        underTest.undoMarkAsNpa(command);

        verify(loan).undoDeliberateNpa(false);
    }

    private static LoanArrearsData arrearsSince(final LocalDate overdueSince) {
        final LoanArrearsData arrears = new LoanArrearsData();
        arrears.setOverDueSince(overdueSince);
        return arrears;
    }
}
