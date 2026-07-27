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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.fineract.accounting.closure.domain.GLClosure;
import org.apache.fineract.accounting.closure.domain.GLClosureRepository;
import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.glaccount.service.GLAccountReadPlatformService;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.accounting.journalentry.exception.JournalEntryInvalidException;
import org.apache.fineract.accounting.journalentry.serialization.JournalEntryCommandFromApiJsonDeserializer;
import org.apache.fineract.accounting.rule.domain.AccountingRuleRepository;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.interbranch.service.CrossBranchTransactionAccessService;
import org.apache.fineract.infrastructure.interbranch.service.InterBranchGlAccountReadService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
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
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JournalEntryWritePlatformServiceInterBranchTest {

    private static final Long HEAD_OFFICE_ID = 1L;
    private static final Long BRANCH_A_ID = 2L;
    private static final Long BRANCH_B_ID = 4L;
    private static final Long CASH_GL_ID = 11L;
    private static final Long SAVINGS_GL_ID = 12L;
    private static final Long EXPENSE_GL_ID = 13L;
    private static final Long CLEARING_GL_ID = 99L;

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
    @Mock
    private InterBranchGlAccountReadService interBranchGlAccountReadService;
    @Mock
    private CrossBranchTransactionAccessService crossBranchTransactionAccessService;
    @Mock
    private AppUser appUser;

    private final FromJsonHelper fromApiJsonHelper = new FromJsonHelper();
    private final JournalEntryCommandFromApiJsonDeserializer fromApiJsonDeserializer = new JournalEntryCommandFromApiJsonDeserializer(
            fromApiJsonHelper);

    private JournalEntryWritePlatformServiceJpaRepositoryImpl underTest;

    private Office headOffice;
    private Office branchA;
    private Office branchB;
    private GLAccount clearingAccount;

    @BeforeEach
    void setUp() {
        final LocalDate today = LocalDate.of(2026, 7, 26);
        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, today, BusinessDateType.COB_DATE, today)));

        underTest = new JournalEntryWritePlatformServiceJpaRepositoryImpl(glClosureRepository, glAccountRepository,
                glJournalEntryRepository, officeRepositoryWrapper, accountingProcessorForLoanFactory, accountingProcessorForSavingsFactory,
                accountingProcessorForSharesFactory, helper, fromApiJsonDeserializer, fromApiJsonHelper, accountingRuleRepository,
                glAccountReadPlatformService, organisationCurrencyRepository, context, paymentDetailWritePlatformService,
                financialActivityAccountRepositoryWrapper, accountingProcessorForClientTransactions, configurationReadPlatformService,
                accountingService, externalAssetOwnerRepository, loanAmortizationAllocationMappingRepository, loanTransactionRepository,
                officeDepartmentMappingValidator, interBranchGlAccountReadService, crossBranchTransactionAccessService);

        headOffice = office(HEAD_OFFICE_ID, ".");
        branchA = office(BRANCH_A_ID, ".2.");
        branchB = office(BRANCH_B_ID, ".4.");

        lenient().when(officeRepositoryWrapper.findOneWithNotFoundDetection(HEAD_OFFICE_ID)).thenReturn(headOffice);
        lenient().when(officeRepositoryWrapper.findOneWithNotFoundDetection(BRANCH_A_ID)).thenReturn(branchA);
        lenient().when(officeRepositoryWrapper.findOneWithNotFoundDetection(BRANCH_B_ID)).thenReturn(branchB);

        lenient().when(context.authenticatedUser()).thenReturn(appUser);
        lenient().when(appUser.getId()).thenReturn(7L);

        final GlobalConfigurationPropertyData disabledConfig = mock(GlobalConfigurationPropertyData.class);
        lenient().when(disabledConfig.isEnabled()).thenReturn(false);
        lenient().when(configurationReadPlatformService.retrieveGlobalConfiguration(anyString())).thenReturn(disabledConfig);

        lenient().when(glClosureRepository.getLatestGLClosureByBranch(anyLong())).thenReturn(null);
        lenient().when(paymentDetailWritePlatformService.createAndPersistPaymentDetail(any(), any())).thenReturn(null);
        lenient().when(helper.persistJournalEntry(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        stubGlAccount(CASH_GL_ID, "Cash");
        stubGlAccount(SAVINGS_GL_ID, "Savings Control");
        stubGlAccount(EXPENSE_GL_ID, "Sundry");
        clearingAccount = glAccount(CLEARING_GL_ID, "Inter-Branch Reconciliation");
        lenient().when(interBranchGlAccountReadService.resolveClearingAccount(anyLong(), anyLong(), anyString()))
                .thenReturn(clearingAccount);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void interBranchEntryCreatesSingleTransactionWithAutomaticClearingLegs() {
        when(appUser.getOffice()).thenReturn(headOffice);

        // debit cash 100 at head office (default), credit savings control 100 at branch A
        final String json = baseJson(HEAD_OFFICE_ID, "[{\"glAccountId\":" + CASH_GL_ID + ",\"amount\":100}]",
                "[{\"glAccountId\":" + SAVINGS_GL_ID + ",\"amount\":100,\"officeId\":" + BRANCH_A_ID + "}]");

        final CommandProcessingResult result = underTest.createJournalEntry(command(json));

        final List<JournalEntry> persisted = capturePersistedEntries(4);
        assertThat(persisted).allSatisfy(entry -> assertThat(entry.getTransactionId()).isEqualTo(result.getTransactionId()));

        final JournalEntry debit = persisted.get(0);
        assertThat(debit.getOffice().getId()).isEqualTo(HEAD_OFFICE_ID);
        assertThat(debit.getType()).isEqualTo(JournalEntryType.DEBIT.getValue());
        assertThat(debit.getGlAccount().getId()).isEqualTo(CASH_GL_ID);

        final JournalEntry credit = persisted.get(1);
        assertThat(credit.getOffice().getId()).isEqualTo(BRANCH_A_ID);
        assertThat(credit.getType()).isEqualTo(JournalEntryType.CREDIT.getValue());
        assertThat(credit.getGlAccount().getId()).isEqualTo(SAVINGS_GL_ID);

        final List<JournalEntry> clearingLegs = persisted.subList(2, 4);
        assertThat(clearingLegs).allSatisfy(entry -> {
            assertThat(entry.getGlAccount().getId()).isEqualTo(CLEARING_GL_ID);
            assertThat(entry.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        });
        // head office had a debit surplus -> clearing credit; branch A had a credit surplus -> clearing debit
        final Map<Long, Integer> clearingTypeByOffice = clearingLegs.stream()
                .collect(Collectors.toMap(e -> e.getOffice().getId(), JournalEntry::getType));
        assertThat(clearingTypeByOffice).containsEntry(HEAD_OFFICE_ID, JournalEntryType.CREDIT.getValue());
        assertThat(clearingTypeByOffice).containsEntry(BRANCH_A_ID, JournalEntryType.DEBIT.getValue());

        // per-office totals must balance
        assertOfficeBalanced(persisted, HEAD_OFFICE_ID);
        assertOfficeBalanced(persisted, BRANCH_A_ID);
    }

    @Test
    void interBranchEntryBalancedPerOfficeNeedsNoClearingLegs() {
        when(appUser.getOffice()).thenReturn(headOffice);

        final String json = baseJson(HEAD_OFFICE_ID,
                "[{\"glAccountId\":" + CASH_GL_ID + ",\"amount\":100},{\"glAccountId\":" + CASH_GL_ID + ",\"amount\":50,\"officeId\":"
                        + BRANCH_A_ID + "}]",
                "[{\"glAccountId\":" + SAVINGS_GL_ID + ",\"amount\":100},{\"glAccountId\":" + SAVINGS_GL_ID
                        + ",\"amount\":50,\"officeId\":" + BRANCH_A_ID + "}]");

        underTest.createJournalEntry(command(json));

        capturePersistedEntries(4);
        verify(interBranchGlAccountReadService, never()).resolveClearingAccount(anyLong(), anyLong(), anyString());
    }

    @Test
    void interBranchEntryOutsideUserHierarchyRequiresCrossBranchAccess() {
        when(appUser.getOffice()).thenReturn(branchA);
        when(crossBranchTransactionAccessService.isCrossBranchTransactionEnabledForCurrentUser()).thenReturn(false);

        final String json = baseJson(BRANCH_A_ID, "[{\"glAccountId\":" + CASH_GL_ID + ",\"amount\":100}]",
                "[{\"glAccountId\":" + SAVINGS_GL_ID + ",\"amount\":100,\"officeId\":" + BRANCH_B_ID + "}]");

        assertThatThrownBy(() -> underTest.createJournalEntry(command(json))).isInstanceOf(NoAuthorizationException.class);
        verify(helper, never()).persistJournalEntry(any());
    }

    @Test
    void interBranchEntryOutsideUserHierarchyAllowedWithCrossBranchAccess() {
        when(appUser.getOffice()).thenReturn(branchA);
        when(crossBranchTransactionAccessService.isCrossBranchTransactionEnabledForCurrentUser()).thenReturn(true);

        final String json = baseJson(BRANCH_A_ID, "[{\"glAccountId\":" + CASH_GL_ID + ",\"amount\":100}]",
                "[{\"glAccountId\":" + SAVINGS_GL_ID + ",\"amount\":100,\"officeId\":" + BRANCH_B_ID + "}]");

        underTest.createJournalEntry(command(json));

        final List<JournalEntry> persisted = capturePersistedEntries(4);
        assertOfficeBalanced(persisted, BRANCH_A_ID);
        assertOfficeBalanced(persisted, BRANCH_B_ID);
    }

    @Test
    void interBranchEntryWithMoreThanTwoUnbalancedOfficesIsRejected() {
        when(appUser.getOffice()).thenReturn(headOffice);

        final String json = baseJson(HEAD_OFFICE_ID,
                "[{\"glAccountId\":" + CASH_GL_ID + ",\"amount\":100},{\"glAccountId\":" + CASH_GL_ID + ",\"amount\":50,\"officeId\":"
                        + BRANCH_A_ID + "}]",
                "[{\"glAccountId\":" + SAVINGS_GL_ID + ",\"amount\":150,\"officeId\":" + BRANCH_B_ID + "}]");

        assertThatThrownBy(() -> underTest.createJournalEntry(command(json)))
                .isInstanceOf(GeneralPlatformDomainRuleException.class)
                .hasFieldOrPropertyWithValue("globalisationMessageCode", "error.msg.glJournalEntry.interbranch.offices.not.balanced");
    }

    @Test
    void interBranchEntryWithAccountingRuleIsRejected() {
        // rejected before any hierarchy/permission evaluation
        final String debits = "[{\"glAccountId\":" + CASH_GL_ID + ",\"amount\":100}]";
        final String credits = "[{\"glAccountId\":" + SAVINGS_GL_ID + ",\"amount\":100,\"officeId\":" + BRANCH_A_ID + "}]";
        final String json = "{\"officeId\":" + HEAD_OFFICE_ID + ",\"transactionDate\":\"26 July 2026\",\"currencyCode\":\"USD\","
                + "\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"accountingRule\":5,\"debits\":" + debits + ",\"credits\":" + credits
                + "}";

        assertThatThrownBy(() -> underTest.createJournalEntry(command(json))).isInstanceOf(GeneralPlatformDomainRuleException.class)
                .hasFieldOrPropertyWithValue("globalisationMessageCode",
                        "error.msg.glJournalEntry.interbranch.accounting.rule.not.supported");
    }

    @Test
    void closureOnLineOfficeBlocksInterBranchEntry() {
        when(appUser.getOffice()).thenReturn(headOffice);
        final GLClosure branchAClosure = mock(GLClosure.class);
        when(branchAClosure.getClosingDate()).thenReturn(LocalDate.of(2026, 7, 26));
        when(glClosureRepository.getLatestGLClosureByBranch(BRANCH_A_ID)).thenReturn(branchAClosure);

        final String json = baseJson(HEAD_OFFICE_ID, "[{\"glAccountId\":" + CASH_GL_ID + ",\"amount\":100}]",
                "[{\"glAccountId\":" + SAVINGS_GL_ID + ",\"amount\":100,\"officeId\":" + BRANCH_A_ID + "}]");

        assertThatThrownBy(() -> underTest.createJournalEntry(command(json))).isInstanceOf(JournalEntryInvalidException.class);
        verify(helper, never()).persistJournalEntry(any());
    }

    @Test
    void reversalChecksClosuresOfAllOfficesInTransaction() {
        final JournalEntry headOfficeDebit = manualEntry(headOffice, CASH_GL_ID, JournalEntryType.DEBIT);
        final JournalEntry branchACredit = manualEntry(branchA, SAVINGS_GL_ID, JournalEntryType.CREDIT);

        final GLClosure branchAClosure = mock(GLClosure.class);
        when(branchAClosure.getClosingDate()).thenReturn(LocalDate.of(2026, 7, 26));
        when(glClosureRepository.getLatestGLClosureByBranch(HEAD_OFFICE_ID)).thenReturn(null);
        when(glClosureRepository.getLatestGLClosureByBranch(BRANCH_A_ID)).thenReturn(branchAClosure);

        assertThatThrownBy(() -> underTest.revertJournalEntry(List.of(headOfficeDebit, branchACredit), "reversal"))
                .isInstanceOf(JournalEntryInvalidException.class);
        verify(helper, never()).persistJournalEntry(any());
    }

    @Test
    void reversalRevertsAllLegsAcrossOfficesUnderOneReversalTransaction() {
        final JournalEntry headOfficeDebit = manualEntry(headOffice, CASH_GL_ID, JournalEntryType.DEBIT);
        final JournalEntry branchACredit = manualEntry(branchA, SAVINGS_GL_ID, JournalEntryType.CREDIT);
        when(glClosureRepository.getLatestGLClosureByBranch(anyLong())).thenReturn(null);

        final String reversalTransactionId = underTest.revertJournalEntry(List.of(headOfficeDebit, branchACredit), "reversal");

        assertThat(headOfficeDebit.isReversed()).isTrue();
        assertThat(branchACredit.isReversed()).isTrue();

        final ArgumentCaptor<JournalEntry> captor = ArgumentCaptor.forClass(JournalEntry.class);
        verify(helper, times(4)).persistJournalEntry(captor.capture());
        final List<JournalEntry> reversalLegs = captor.getAllValues().stream()
                .filter(entry -> reversalTransactionId.equals(entry.getTransactionId())).collect(Collectors.toList());
        assertThat(reversalLegs).hasSize(2);
        assertThat(reversalLegs.stream().map(e -> e.getOffice().getId())).containsExactlyInAnyOrder(HEAD_OFFICE_ID, BRANCH_A_ID);
    }

    private void assertOfficeBalanced(final List<JournalEntry> entries, final Long officeId) {
        final BigDecimal net = entries.stream().filter(e -> e.getOffice().getId().equals(officeId))
                .map(e -> JournalEntryType.DEBIT.getValue().equals(e.getType()) ? e.getAmount() : e.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(net).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private List<JournalEntry> capturePersistedEntries(final int expectedCount) {
        final ArgumentCaptor<JournalEntry> captor = ArgumentCaptor.forClass(JournalEntry.class);
        verify(helper, times(expectedCount)).persistJournalEntry(captor.capture());
        return captor.getAllValues();
    }

    private String baseJson(final Long officeId, final String debits, final String credits) {
        return "{\"officeId\":" + officeId + ",\"transactionDate\":\"26 July 2026\",\"currencyCode\":\"USD\","
                + "\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"comments\":\"Inter-branch test\",\"debits\":" + debits
                + ",\"credits\":" + credits + "}";
    }

    private JsonCommand command(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), fromApiJsonHelper, "JOURNALENTRY", null, null, null, null, null, null,
                null, "/journalentries", null, null, null, null, null);
    }

    private Office office(final Long id, final String hierarchy) {
        final Office office = Office.headOffice("Office-" + id, LocalDate.now(ZoneId.systemDefault()), null);
        ReflectionTestUtils.setField(office, "id", id);
        ReflectionTestUtils.setField(office, "hierarchy", hierarchy);
        return office;
    }

    private GLAccount glAccount(final Long id, final String name) {
        final GLAccount glAccount = new GLAccount().setName(name).setGlCode("GL-" + id).setDisabled(false).setManualEntriesAllowed(true)
                .setType(1);
        ReflectionTestUtils.setField(glAccount, "id", id);
        return glAccount;
    }

    private void stubGlAccount(final Long id, final String name) {
        lenient().when(glAccountRepository.findById(id)).thenReturn(java.util.Optional.of(glAccount(id, name)));
    }

    private JournalEntry manualEntry(final Office office, final Long glAccountId, final JournalEntryType type) {
        final JournalEntry entry = JournalEntry.createNew(office, null, glAccount(glAccountId, "gl-" + glAccountId), "USD", "TXN-IB-1",
                true, LocalDate.of(2026, 7, 20), type, BigDecimal.valueOf(100), "line", null, null, null, null, null, null, null);
        return entry;
    }
}
