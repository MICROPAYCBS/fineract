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
package org.apache.fineract.accounting.journalentry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.closure.domain.GLClosureRepository;
import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.glaccount.service.GLAccountReadPlatformService;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.accounting.journalentry.exception.JournalEntriesNotFoundException;
import org.apache.fineract.accounting.journalentry.serialization.JournalEntryCommandFromApiJsonDeserializer;
import org.apache.fineract.accounting.rule.domain.AccountingRuleRepository;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.investor.domain.ExternalAssetOwnerRepository;
import org.apache.fineract.investor.service.AccountingService;
import org.apache.fineract.organisation.monetary.domain.OrganisationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.portfolio.department.service.OfficeDepartmentMappingValidator;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAmortizationAllocationMappingRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JournalEntryWritePlatformServiceUpdateNarrationTest {

    private static final String TRANSACTION_ID = "TXN-NARRATION-1";

    @Mock
    private GLClosureRepository glClosureRepository;
    @Mock
    private GLAccountRepository glAccountRepository;
    @Mock
    private JournalEntryRepository glJournalEntryRepository;
    @Mock
    private OfficeRepositoryWrapper officeRepositoryWrapper;
    @Mock
    private AccountingProcessorForLoanFactory accountingProcessorForLoanFactory;
    @Mock
    private AccountingProcessorForSavingsFactory accountingProcessorForSavingsFactory;
    @Mock
    private AccountingProcessorForSharesFactory accountingProcessorForSharesFactory;
    @Mock
    private AccountingProcessorHelper helper;
    @Mock
    private JournalEntryCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    @Mock
    private AccountingRuleRepository accountingRuleRepository;
    @Mock
    private GLAccountReadPlatformService glAccountReadPlatformService;
    @Mock
    private OrganisationCurrencyRepositoryWrapper organisationCurrencyRepository;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private PaymentDetailWritePlatformService paymentDetailWritePlatformService;
    @Mock
    private FinancialActivityAccountRepositoryWrapper financialActivityAccountRepositoryWrapper;
    @Mock
    private CashBasedAccountingProcessorForClientTransactions accountingProcessorForClientTransactions;
    @Mock
    private ConfigurationReadPlatformService configurationReadPlatformService;
    @Mock
    private AccountingService accountingService;
    @Mock
    private ExternalAssetOwnerRepository externalAssetOwnerRepository;
    @Mock
    private LoanAmortizationAllocationMappingRepository loanAmortizationAllocationMappingRepository;
    @Mock
    private LoanTransactionRepository loanTransactionRepository;
    @Mock
    private OfficeDepartmentMappingValidator officeDepartmentMappingValidator;

    private JournalEntryWritePlatformServiceJpaRepositoryImpl underTest;
    private final FromJsonHelper fromApiJsonHelper = new FromJsonHelper();

    @BeforeEach
    void setUp() {
        final LocalDate today = LocalDate.of(2026, 7, 26);
        ThreadLocalContextUtil.setBusinessDates(
                new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, today, BusinessDateType.COB_DATE, today)));
        underTest = new JournalEntryWritePlatformServiceJpaRepositoryImpl(glClosureRepository, glAccountRepository,
                glJournalEntryRepository, officeRepositoryWrapper, accountingProcessorForLoanFactory, accountingProcessorForSavingsFactory,
                accountingProcessorForSharesFactory, helper, fromApiJsonDeserializer, fromApiJsonHelper, accountingRuleRepository,
                glAccountReadPlatformService, organisationCurrencyRepository, context, paymentDetailWritePlatformService,
                financialActivityAccountRepositoryWrapper, accountingProcessorForClientTransactions, configurationReadPlatformService,
                accountingService, externalAssetOwnerRepository, loanAmortizationAllocationMappingRepository, loanTransactionRepository,
                officeDepartmentMappingValidator);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void updateNarrationUpdatesTransactionCommentOnly() {
        final JournalEntry debit = journalEntry(101L, JournalEntryType.DEBIT, "IT Salaries", "Old month");
        final JournalEntry credit = journalEntry(102L, JournalEntryType.CREDIT, "Ops Salaries", "Old month");
        when(glJournalEntryRepository.findUnReversedManualJournalEntriesByTransactionId(TRANSACTION_ID))
                .thenReturn(List.of(debit, credit));
        when(helper.persistJournalEntry(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final CommandProcessingResult result = underTest
                .updateJournalEntryNarration(txCommand("{\"transactionComments\":\"January Salaries\"}"));

        assertThat(result.getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(debit.getTransactionComment()).isEqualTo("January Salaries");
        assertThat(credit.getTransactionComment()).isEqualTo("January Salaries");
        assertThat(debit.getDescription()).isEqualTo("IT Salaries");
        assertThat(credit.getDescription()).isEqualTo("Ops Salaries");
        verify(helper, times(2)).persistJournalEntry(any(JournalEntry.class));
    }

    @Test
    void updateLineNarrationUpdatesDescriptionOnly() {
        final JournalEntry debit = journalEntry(101L, JournalEntryType.DEBIT, "old", "January Salaries");
        when(glJournalEntryRepository.findById(101L)).thenReturn(Optional.of(debit));
        when(helper.persistJournalEntry(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final CommandProcessingResult result = underTest
                .updateJournalEntryLineNarration(lineCommand(101L, "{\"comments\":\"IT Salaries\"}"));

        assertThat(result.getResourceId()).isEqualTo(101L);
        assertThat(debit.getDescription()).isEqualTo("IT Salaries");
        assertThat(debit.getTransactionComment()).isEqualTo("January Salaries");
    }

    @Test
    void updateLineNarrationsBatchUpdatesMatchingLines() {
        final JournalEntry debit = journalEntry(101L, JournalEntryType.DEBIT, "old1", "January Salaries");
        final JournalEntry credit = journalEntry(102L, JournalEntryType.CREDIT, "old2", "January Salaries");
        when(glJournalEntryRepository.findUnReversedManualJournalEntriesByTransactionId(TRANSACTION_ID))
                .thenReturn(List.of(debit, credit));
        when(helper.persistJournalEntry(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        underTest.updateJournalEntryLineNarrations(txCommand(
                "{\"entries\":[{\"id\":101,\"comments\":\"IT Salaries\"},{\"id\":102,\"comments\":\"Ops Salaries\"}]}"));

        assertThat(debit.getDescription()).isEqualTo("IT Salaries");
        assertThat(credit.getDescription()).isEqualTo("Ops Salaries");
        assertThat(debit.getTransactionComment()).isEqualTo("January Salaries");
    }

    @Test
    void failsWhenTransactionHasNoUnreversedManualEntries() {
        when(glJournalEntryRepository.findUnReversedManualJournalEntriesByTransactionId(TRANSACTION_ID))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> underTest.updateJournalEntryNarration(txCommand("{\"transactionComments\":\"x\"}")))
                .isInstanceOf(JournalEntriesNotFoundException.class);
        verify(helper, never()).persistJournalEntry(any());
    }

    @Test
    void rejectsUnsupportedParametersOnTransactionNarration() {
        assertThatThrownBy(() -> underTest.updateJournalEntryNarration(txCommand("{\"transactionComments\":\"ok\",\"officeId\":1}")))
                .isInstanceOf(UnsupportedParameterException.class);
        verify(glJournalEntryRepository, never()).findUnReversedManualJournalEntriesByTransactionId(any());
    }

    @Test
    void rejectsOversizedTransactionComments() {
        final String tooLong = "x".repeat(501);

        assertThatThrownBy(() -> underTest.updateJournalEntryNarration(txCommand("{\"transactionComments\":\"" + tooLong + "\"}")))
                .isInstanceOf(PlatformApiDataValidationException.class);
        verify(glJournalEntryRepository, never()).findUnReversedManualJournalEntriesByTransactionId(eq(TRANSACTION_ID));
    }

    @Test
    void rejectsMissingTransactionComments() {
        assertThatThrownBy(() -> underTest.updateJournalEntryNarration(txCommand("{}")))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void rejectsSystemGeneratedLineNarration() {
        final JournalEntry systemLine = journalEntry(101L, JournalEntryType.DEBIT, "x", null);
        ReflectionTestUtils.setField(systemLine, "manualEntry", false);
        when(glJournalEntryRepository.findById(101L)).thenReturn(Optional.of(systemLine));

        assertThatThrownBy(() -> underTest.updateJournalEntryLineNarration(lineCommand(101L, "{\"comments\":\"nope\"}")))
                .isInstanceOf(JournalEntriesNotFoundException.class);
        verify(helper, never()).persistJournalEntry(any());
    }

    private JsonCommand txCommand(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), fromApiJsonHelper, "JOURNALENTRY", null, null, null, null, null, null,
                TRANSACTION_ID, "/journalentries/" + TRANSACTION_ID, null, null, null, null, null);
    }

    private JsonCommand lineCommand(final Long journalEntryId, final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), fromApiJsonHelper, "JOURNALENTRY", journalEntryId, null, null, null,
                null, null, null, "/journalentries/entries/" + journalEntryId, null, null, null, null, null);
    }

    private JournalEntry journalEntry(final Long id, final JournalEntryType type, final String description,
            final String transactionComment) {
        final Office office = Office.headOffice("HO", LocalDate.now(ZoneId.systemDefault()), null);
        final GLAccount glAccount = mock(GLAccount.class);
        final JournalEntry entry = JournalEntry.createNew(office, null, glAccount, "USD", TRANSACTION_ID, true,
                LocalDate.now(ZoneId.systemDefault()), type, BigDecimal.TEN, description, null, null, null, null, null, null, null);
        entry.updateTransactionComment(transactionComment);
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }
}
