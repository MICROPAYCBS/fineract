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
package com.micropay.fineract.organisation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccount;
import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.staff.domain.StaffRepository;
import org.apache.fineract.organisation.teller.data.CashierTransactionDataValidator;
import org.apache.fineract.organisation.teller.domain.CashLegalTenderLine;
import org.apache.fineract.organisation.teller.domain.CashLegalTenderSourceType;
import org.apache.fineract.organisation.teller.domain.Cashier;
import org.apache.fineract.organisation.teller.domain.CashierRepository;
import org.apache.fineract.organisation.teller.domain.CashierTransaction;
import org.apache.fineract.organisation.teller.domain.CashierTransactionRepository;
import org.apache.fineract.organisation.teller.domain.Teller;
import org.apache.fineract.organisation.teller.domain.TellerRepositoryWrapper;
import org.apache.fineract.organisation.teller.serialization.TellerCommandFromApiJsonDeserializer;
import org.apache.fineract.organisation.teller.service.LegalTenderBreakdownValidator;
import org.apache.fineract.organisation.teller.service.LegalTenderBreakdownWritePlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TellerWritePlatformServiceJpaImplTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private TellerCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    @Mock
    private TellerRepositoryWrapper tellerRepositoryWrapper;
    @Mock
    private OfficeRepositoryWrapper officeRepositoryWrapper;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private CashierRepository cashierRepository;
    @Mock
    private CashierTransactionRepository cashierTxnRepository;
    @Mock
    private JournalEntryRepository glJournalEntryRepository;
    @Mock
    private FinancialActivityAccountRepositoryWrapper financialActivityAccountRepositoryWrapper;
    @Mock
    private CashierTransactionDataValidator cashierTransactionDataValidator;
    @Mock
    private LegalTenderBreakdownValidator legalTenderBreakdownValidator;
    @Mock
    private LegalTenderBreakdownWritePlatformService legalTenderBreakdownWritePlatformService;

    private MicropayTellerWritePlatformServiceJpaImpl service;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", null));
        ThreadLocalContextUtil.setActionContext(ActionContext.DEFAULT);
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 10, 9))));
        this.service = new MicropayTellerWritePlatformServiceJpaImpl(this.context, this.fromApiJsonDeserializer,
                this.tellerRepositoryWrapper, this.officeRepositoryWrapper, this.staffRepository, this.cashierRepository,
                this.cashierTxnRepository, this.glJournalEntryRepository, this.financialActivityAccountRepositoryWrapper,
                this.cashierTransactionDataValidator, this.legalTenderBreakdownValidator, this.legalTenderBreakdownWritePlatformService);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void allocateFlushesCashierTransactionBeforeLinkingLegalTenderLines() {
        final AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(1L);
        when(this.context.authenticatedUser()).thenReturn(user);

        final Office office = mock(Office.class);
        when(office.getId()).thenReturn(1L);
        final Teller teller = mock(Teller.class);
        when(teller.getOffice()).thenReturn(office);
        final Cashier cashier = mock(Cashier.class);
        when(cashier.getId()).thenReturn(9L);
        when(cashier.getTeller()).thenReturn(teller);
        when(this.cashierRepository.findById(1L)).thenReturn(Optional.of(cashier));

        final JsonCommand command = mock(JsonCommand.class);
        when(command.json()).thenReturn("{}");
        when(command.stringValueOfParameterNamed(anyString())).thenReturn(null);
        when(command.stringValueOfParameterNamed("currencyCode")).thenReturn("USD");
        final BigDecimal txnAmount = new BigDecimal("500");
        when(command.bigDecimalValueOfParameterNamed("txnAmount")).thenReturn(txnAmount);

        final CashLegalTenderLine line = new CashLegalTenderLine();
        when(this.legalTenderBreakdownValidator.validateAndBuildLinesRequired(command, "USD", txnAmount,
                CashLegalTenderSourceType.CASHIER_TXN, null)).thenReturn(List.of(line));
        when(this.cashierTxnRepository.saveAndFlush(any(CashierTransaction.class))).thenAnswer(invocation -> {
            final CashierTransaction saved = invocation.getArgument(0);
            saved.setId(77L);
            return saved;
        });

        final FinancialActivityAccount activityAccount = mock(FinancialActivityAccount.class);
        when(activityAccount.getGlAccount()).thenReturn(mock(GLAccount.class));
        when(this.financialActivityAccountRepositoryWrapper.findByFinancialActivityTypeWithNotFoundDetection(anyInt()))
                .thenReturn(activityAccount);

        final CommandProcessingResult result = this.service.allocateCashToCashier(1L, command);

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<CashLegalTenderLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(this.legalTenderBreakdownWritePlatformService).saveLines(lines.capture());
        assertEquals(77L, lines.getValue().get(0).getSourceId());
        assertEquals(77L, result.getSubResourceId());
        verify(this.cashierTxnRepository, never()).save(any());
    }
}
