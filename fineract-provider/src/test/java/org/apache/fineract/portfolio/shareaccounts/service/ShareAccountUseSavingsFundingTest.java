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
package org.apache.fineract.portfolio.shareaccounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatRepositoryWrapper;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetailRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetails;
import org.apache.fineract.portfolio.account.domain.AccountTransferTransaction;
import org.apache.fineract.portfolio.account.service.AccountNumberGenerator;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.service.SavingsAccountDomainService;
import org.apache.fineract.portfolio.shareaccounts.domain.ShareAccount;
import org.apache.fineract.portfolio.shareaccounts.domain.ShareAccountRepositoryWrapper;
import org.apache.fineract.portfolio.shareaccounts.domain.ShareAccountTransaction;
import org.apache.fineract.portfolio.shareaccounts.serialization.ShareAccountDataSerializer;
import org.apache.fineract.portfolio.shareproducts.domain.ShareProductRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShareAccountUseSavingsFundingTest {

    @Mock
    private ShareAccountDataSerializer accountDataSerializer;
    @Mock
    private ShareAccountRepositoryWrapper shareAccountRepository;
    @Mock
    private ShareProductRepositoryWrapper shareProductRepository;
    @Mock
    private AccountNumberGenerator accountNumberGenerator;
    @Mock
    private AccountNumberFormatRepositoryWrapper accountNumberFormatRepository;
    @Mock
    private JournalEntryWritePlatformService journalEntryWritePlatformService;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private BusinessEventNotifierService businessEventNotifierService;
    @Mock
    private SavingsAccountDomainService savingsAccountDomainService;
    @Mock
    private SavingsAccountAssembler savingsAccountAssembler;
    @Mock
    private AccountTransferDetailRepository accountTransferDetailRepository;

    private ShareAccountWritePlatformServiceJpaRepositoryImpl service;

    @BeforeEach
    void setUp() {
        this.service = new ShareAccountWritePlatformServiceJpaRepositoryImpl(this.accountDataSerializer, this.shareAccountRepository,
                this.shareProductRepository, this.accountNumberGenerator, this.accountNumberFormatRepository,
                this.journalEntryWritePlatformService, this.noteRepository, this.businessEventNotifierService,
                this.savingsAccountDomainService, this.savingsAccountAssembler, this.accountTransferDetailRepository);
    }

    @Test
    void fundPurchasesFromSavingsWithdrawsAndRecordsTransferWhenUseSavings() {
        final ShareAccount account = mock(ShareAccount.class);
        final SavingsAccount linked = mock(SavingsAccount.class);
        final SavingsAccount savings = mock(SavingsAccount.class);
        final Client savingsClient = mock(Client.class);
        final Client shareClient = mock(Client.class);
        final Office office = mock(Office.class);
        final MonetaryCurrency currency = new MonetaryCurrency("UGX", 2, 1);
        final ShareAccountTransaction purchase = new ShareAccountTransaction(LocalDate.of(2026, 7, 21), 10L, new BigDecimal("100.00"));
        purchase.setUseSavings(true);
        ReflectionTestUtils.setField(purchase, "id", 55L);

        when(account.getSavingsAccount()).thenReturn(linked);
        when(account.getClient()).thenReturn(shareClient);
        when(shareClient.getOffice()).thenReturn(office);
        when(linked.getId()).thenReturn(9L);
        when(this.savingsAccountAssembler.assembleFrom(9L, false)).thenReturn(savings);
        when(account.getAccountNumber()).thenReturn("SA0001");
        when(savings.getWithdrawableBalance()).thenReturn(new BigDecimal("5000.00"));
        when(savings.isWithdrawalFeeApplicableForTransfer()).thenReturn(false);
        when(savings.office()).thenReturn(office);
        when(savings.getClient()).thenReturn(savingsClient);
        when(savings.getCurrency()).thenReturn(currency);
        final SavingsAccountTransaction withdrawal = mock(SavingsAccountTransaction.class);
        when(this.savingsAccountDomainService.handleWithdrawal(eq(savings), any(), any(), eq(new BigDecimal("1000.00")), any(), any(),
                anyBoolean())).thenReturn(withdrawal);

        final Set<ShareAccountTransaction> transactions = new HashSet<>();
        transactions.add(purchase);
        ReflectionTestUtils.invokeMethod(this.service, "fundPurchasesFromSavingsIfRequired", account, transactions,
                LocalDate.of(2026, 7, 21));

        verify(this.savingsAccountAssembler).assembleFrom(9L, false);
        verify(this.savingsAccountDomainService).handleWithdrawal(eq(savings), any(), eq(LocalDate.of(2026, 7, 21)),
                eq(new BigDecimal("1000.00")), any(), any(), eq(false));
        final ArgumentCaptor<AccountTransferDetails> transferCaptor = ArgumentCaptor.forClass(AccountTransferDetails.class);
        verify(this.accountTransferDetailRepository).saveAndFlush(transferCaptor.capture());
        final AccountTransferDetails transferDetails = transferCaptor.getValue();
        assertThat(transferDetails.getAccountTransferTransactions()).hasSize(1);
        final AccountTransferTransaction transferTxn = transferDetails.getAccountTransferTransactions().get(0);
        assertThat(transferTxn.getFromTransaction()).isSameAs(withdrawal);
        assertThat(ReflectionTestUtils.getField(transferTxn, "description")).asString()
                .contains("Share purchase - SA0001 - 10 shares");
        final ArgumentCaptor<Note> noteCaptor = ArgumentCaptor.forClass(Note.class);
        verify(this.noteRepository).save(noteCaptor.capture());
        assertThat(ReflectionTestUtils.getField(noteCaptor.getValue(), "note")).asString().contains("Share purchase - SA0001 - 10 shares");
    }

    @Test
    void fundPurchasesFromSavingsSkippedWhenUseSavingsFalse() {
        final ShareAccount account = mock(ShareAccount.class);
        final ShareAccountTransaction purchase = new ShareAccountTransaction(LocalDate.of(2026, 7, 21), 10L, new BigDecimal("100.00"));
        purchase.setUseSavings(false);

        final Set<ShareAccountTransaction> transactions = Set.of(purchase);
        ReflectionTestUtils.invokeMethod(this.service, "fundPurchasesFromSavingsIfRequired", account, transactions,
                LocalDate.of(2026, 7, 21));

        verify(this.savingsAccountAssembler, never()).assembleFrom(any(), anyBoolean());
        verify(this.savingsAccountDomainService, never()).handleWithdrawal(any(), any(), any(), any(), any(), any(), anyBoolean());
        verify(this.accountTransferDetailRepository, never()).saveAndFlush(any());
        verify(this.noteRepository, never()).save(any());
    }

    @Test
    void fundPurchasesFromSavingsFailsWhenAvailableBalanceInsufficient() {
        final ShareAccount account = mock(ShareAccount.class);
        final SavingsAccount linked = mock(SavingsAccount.class);
        final SavingsAccount savings = mock(SavingsAccount.class);
        final ShareAccountTransaction purchase = new ShareAccountTransaction(LocalDate.of(2026, 7, 21), 10L, new BigDecimal("100.00"));
        purchase.setUseSavings(true);

        when(account.getSavingsAccount()).thenReturn(linked);
        when(linked.getId()).thenReturn(9L);
        when(this.savingsAccountAssembler.assembleFrom(9L, false)).thenReturn(savings);
        when(savings.getWithdrawableBalance()).thenReturn(new BigDecimal("50.00"));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(this.service, "fundPurchasesFromSavingsIfRequired", account,
                Set.of(purchase), LocalDate.of(2026, 7, 21))).isInstanceOf(GeneralPlatformDomainRuleException.class);

        verify(this.savingsAccountDomainService, never()).handleWithdrawal(any(), any(), any(), any(), any(), any(), anyBoolean());
        verify(this.accountTransferDetailRepository, never()).saveAndFlush(any());
    }

    @Test
    void creditRedemptionToSavingsDepositsAndRecordsInwardTransferWhenUseSavings() {
        final ShareAccount account = mock(ShareAccount.class);
        final SavingsAccount linked = mock(SavingsAccount.class);
        final SavingsAccount savings = mock(SavingsAccount.class);
        final Client savingsClient = mock(Client.class);
        final Client shareClient = mock(Client.class);
        final Office office = mock(Office.class);
        final MonetaryCurrency currency = new MonetaryCurrency("UGX", 2, 1);
        final ShareAccountTransaction redeem = ShareAccountTransaction.createRedeemTransaction(LocalDate.of(2026, 7, 22), 5L,
                new BigDecimal("200.00"));
        redeem.setUseSavings(true);
        ReflectionTestUtils.setField(redeem, "id", 66L);

        when(account.getSavingsAccount()).thenReturn(linked);
        when(account.getClient()).thenReturn(shareClient);
        when(shareClient.getOffice()).thenReturn(office);
        when(linked.getId()).thenReturn(9L);
        when(this.savingsAccountAssembler.assembleFrom(9L, false)).thenReturn(savings);
        when(account.getAccountNumber()).thenReturn("SA0001");
        when(savings.office()).thenReturn(office);
        when(savings.getClient()).thenReturn(savingsClient);
        when(savings.getCurrency()).thenReturn(currency);
        final SavingsAccountTransaction deposit = mock(SavingsAccountTransaction.class);
        when(this.savingsAccountDomainService.handleDeposit(eq(savings), any(), any(), eq(new BigDecimal("1000.00")), any(), eq(true),
                eq(true), eq(false))).thenReturn(deposit);

        ReflectionTestUtils.invokeMethod(this.service, "creditRedemptionToSavingsIfRequired", account, Set.of(redeem),
                LocalDate.of(2026, 7, 22));

        verify(this.savingsAccountDomainService).handleDeposit(eq(savings), any(), eq(LocalDate.of(2026, 7, 22)),
                eq(new BigDecimal("1000.00")), any(), eq(true), eq(true), eq(false));
        final ArgumentCaptor<AccountTransferDetails> transferCaptor = ArgumentCaptor.forClass(AccountTransferDetails.class);
        verify(this.accountTransferDetailRepository).saveAndFlush(transferCaptor.capture());
        final AccountTransferTransaction transferTxn = transferCaptor.getValue().getAccountTransferTransactions().get(0);
        assertThat(transferTxn.getToSavingsTransaction()).isSameAs(deposit);
        assertThat(ReflectionTestUtils.getField(transferTxn, "description")).asString()
                .contains("Share redemption - SA0001 - 5 shares");
        verify(this.noteRepository).save(any(Note.class));
    }

    @Test
    void creditRedemptionToSavingsSkippedWhenUseSavingsFalse() {
        final ShareAccount account = mock(ShareAccount.class);
        final ShareAccountTransaction redeem = ShareAccountTransaction.createRedeemTransaction(LocalDate.of(2026, 7, 22), 5L,
                new BigDecimal("200.00"));
        redeem.setUseSavings(false);

        ReflectionTestUtils.invokeMethod(this.service, "creditRedemptionToSavingsIfRequired", account, Set.of(redeem),
                LocalDate.of(2026, 7, 22));

        verify(this.savingsAccountAssembler, never()).assembleFrom(any(), anyBoolean());
        verify(this.savingsAccountDomainService, never()).handleDeposit(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean());
        verify(this.accountTransferDetailRepository, never()).saveAndFlush(any());
    }
}
