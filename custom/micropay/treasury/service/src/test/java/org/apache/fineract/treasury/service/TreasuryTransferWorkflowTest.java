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
package org.apache.fineract.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
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
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.interbranch.service.InterBranchGlAccountReadService;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.domain.TreasuryEventType;
import org.apache.fineract.treasury.domain.TreasurySetting;
import org.apache.fineract.treasury.domain.TreasuryTransfer;
import org.apache.fineract.treasury.domain.TreasuryTransferRepository;
import org.apache.fineract.treasury.domain.TreasuryTransferStatus;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryTransferWorkflowTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 16);
    private static final long REQUESTER = 1L;
    private static final long APPROVER = 2L;
    private static final long SENDER = 3L;
    private static final long IN_TRANSIT_GL = 30L;
    private static final long CLEARING_GL = 40L;
    private static final BigDecimal AMOUNT = new BigDecimal("500");

    @Mock
    private TreasuryActor actor;
    @Mock
    private TreasuryTransferRepository transferRepository;
    @Mock
    private TreasuryAccountRepository accountRepository;
    @Mock
    private TreasurySettingService settingService;
    @Mock
    private TreasuryBalanceReader balanceReader;
    @Mock
    private TreasuryJournalPoster journalPoster;
    @Mock
    private InterBranchGlAccountReadService interBranchGlAccountReadService;

    private TreasuryTransferWritePlatformServiceImpl service;
    private final Map<Long, TreasuryAccount> accounts = new HashMap<>();

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", mock(FineractPlatformTenantConnection.class)));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(
                Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE, BusinessDateType.COB_DATE, BUSINESS_DATE.minusDays(1))));
        this.service = new TreasuryTransferWritePlatformServiceImpl(this.actor, new TreasuryWorkflowDataValidator(new FromJsonHelper()),
                this.transferRepository, this.accountRepository, this.settingService, this.balanceReader, this.journalPoster,
                this.interBranchGlAccountReadService);
        account(100L, 1L, 10L, "UGX");
        account(200L, 1L, 20L, "UGX");
        account(300L, 2L, 21L, "UGX");
        account(400L, 1L, 22L, "USD");
        lenient().when(this.accountRepository.findById(any()))
                .thenAnswer(call -> Optional.ofNullable(this.accounts.get(call.getArgument(0))));
        lenient().when(this.settingService.requireCashInTransitGlAccount("UGX")).thenReturn(IN_TRANSIT_GL);
        lenient().when(this.settingService.settingsFor("UGX")).thenReturn(new TreasurySetting());
        lenient().when(this.journalPoster.post(any(), any(), anyString(), any(), anyString(), any(), any()))
                .thenAnswer(call -> TreasuryJournalPoster.transactionId(call.getArgument(0), call.getArgument(1)));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void transferNeedsOneCurrency() {
        actAs(REQUESTER);
        final TreasuryWorkflowException exception = assertThrows(TreasuryWorkflowException.class,
                () -> this.service.createTransfer(command("{\"fromAccountId\": 100, \"toAccountId\": 400, \"amount\": 10}")));
        assertEquals("error.msg.treasury.transfer.currency.mismatch", exception.getGlobalisationMessageCode());
    }

    @Test
    void transferCannotBeDatedAfterTheBusinessDate() {
        actAs(REQUESTER);
        final TreasuryWorkflowException exception = assertThrows(TreasuryWorkflowException.class, () -> this.service.createTransfer(
                command("{\"fromAccountId\": 100, \"toAccountId\": 200, \"amount\": 10, \"transferDate\": \"2026-10-17\"}")));
        assertEquals("error.msg.treasury.transfer.future.date", exception.getGlobalisationMessageCode());
    }

    @Test
    void requesterCannotApprove() {
        final TreasuryTransfer transfer = transfer(TreasuryTransferStatus.REQUESTED, 200L);
        actAs(REQUESTER);

        final TreasuryWorkflowException exception = assertThrows(TreasuryWorkflowException.class,
                () -> this.service.approveTransfer(transfer.getId(), command("{}")));
        assertEquals("error.msg.treasury.transfer.same.user", exception.getGlobalisationMessageCode());
    }

    @Test
    void approverCannotSend() {
        final TreasuryTransfer transfer = transfer(TreasuryTransferStatus.APPROVED, 200L);
        actAs(APPROVER);

        assertThrows(TreasuryWorkflowException.class, () -> this.service.sendTransfer(transfer.getId(), command("{}")));
        verify(this.journalPoster, never()).post(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void stepsMustComeInOrder() {
        final TreasuryTransfer transfer = transfer(TreasuryTransferStatus.REQUESTED, 200L);
        actAs(SENDER);

        final TreasuryWorkflowException exception = assertThrows(TreasuryWorkflowException.class,
                () -> this.service.sendTransfer(transfer.getId(), command("{}")));
        assertEquals("error.msg.treasury.transfer.invalid.status", exception.getGlobalisationMessageCode());
    }

    @Test
    void sendIsBlockedWhenTheSourceLacksTheBalance() {
        final TreasuryTransfer transfer = transfer(TreasuryTransferStatus.APPROVED, 200L);
        when(this.balanceReader.glBalances(List.of(100L), null)).thenReturn(Map.of(100L, new BigDecimal("499.99")));
        actAs(SENDER);

        final TreasuryWorkflowException exception = assertThrows(TreasuryWorkflowException.class,
                () -> this.service.sendTransfer(transfer.getId(), command("{}")));
        assertEquals("error.msg.treasury.transfer.insufficient.balance", exception.getGlobalisationMessageCode());
    }

    @Test
    void sendIsAllowedWithoutTheBalanceWhenTheSettingSaysSo() {
        final TreasuryTransfer transfer = transfer(TreasuryTransferStatus.APPROVED, 200L);
        final TreasurySetting setting = new TreasurySetting();
        setting.setAllowInsufficientBalance(true);
        when(this.settingService.settingsFor("UGX")).thenReturn(setting);
        actAs(SENDER);

        this.service.sendTransfer(transfer.getId(), command("{}"));

        assertEquals(TreasuryTransferStatus.SENT, transfer.getStatus());
    }

    @Test
    void sendPostsSourceToCashInTransitOnTheBusinessDate() {
        final TreasuryTransfer transfer = transfer(TreasuryTransferStatus.APPROVED, 200L);
        when(this.balanceReader.glBalances(List.of(100L), null)).thenReturn(Map.of(100L, new BigDecimal("500")));
        actAs(SENDER);

        this.service.sendTransfer(transfer.getId(), command("{}"));

        final List<Leg> legs = postedLegs(TreasuryEventType.TRANSFER_SEND, BUSINESS_DATE);
        assertEquals(List.of(new Leg(1L, IN_TRANSIT_GL, JournalEntryType.DEBIT, AMOUNT), new Leg(1L, 10L, JournalEntryType.CREDIT, AMOUNT)),
                legs);
        assertEquals(TreasuryTransferStatus.SENT, transfer.getStatus());
        assertEquals(BUSINESS_DATE, transfer.getSentOnDate());
        assertEquals("TR-TFS-7", transfer.getSendTransactionId());
        assertEquals(IN_TRANSIT_GL, transfer.getInTransitGlAccountId());
        assertEquals(SENDER, transfer.getSentBy());
    }

    @Test
    void receiveAcrossOfficesUsesTheClearingAccount() {
        final TreasuryTransfer transfer = transfer(TreasuryTransferStatus.SENT, 300L);
        transfer.setSentOnDate(BUSINESS_DATE.minusDays(2));
        transfer.setInTransitGlAccountId(IN_TRANSIT_GL);
        final GLAccount clearing = mock(GLAccount.class);
        when(clearing.getId()).thenReturn(CLEARING_GL);
        when(this.interBranchGlAccountReadService.resolveClearingAccount(1L, 2L, "UGX")).thenReturn(clearing);
        actAs(SENDER);

        this.service.receiveTransfer(transfer.getId(), command("{\"transactionDate\": \"2026-10-15\"}"));

        final List<Leg> legs = postedLegs(TreasuryEventType.TRANSFER_RECEIVE, LocalDate.of(2026, 10, 15));
        assertEquals(List.of(new Leg(2L, 21L, JournalEntryType.DEBIT, AMOUNT), new Leg(2L, CLEARING_GL, JournalEntryType.CREDIT, AMOUNT),
                new Leg(1L, CLEARING_GL, JournalEntryType.DEBIT, AMOUNT), new Leg(1L, IN_TRANSIT_GL, JournalEntryType.CREDIT, AMOUNT)),
                legs);
        assertEquals(TreasuryTransferStatus.RECEIVED, transfer.getStatus());
    }

    @Test
    void receiveCannotPrecedeTheSend() {
        final TreasuryTransfer transfer = transfer(TreasuryTransferStatus.SENT, 200L);
        transfer.setSentOnDate(BUSINESS_DATE);
        actAs(SENDER);

        assertThrows(TreasuryWorkflowException.class,
                () -> this.service.receiveTransfer(transfer.getId(), command("{\"transactionDate\": \"2026-10-15\"}")));
    }

    @Test
    void sentTransferCannotBeCancelled() {
        final TreasuryTransfer transfer = transfer(TreasuryTransferStatus.SENT, 200L);

        final TreasuryWorkflowException exception = assertThrows(TreasuryWorkflowException.class,
                () -> this.service.cancelTransfer(transfer.getId(), command("{}")));
        assertEquals("error.msg.treasury.transfer.cannot.cancel", exception.getGlobalisationMessageCode());
    }

    @Test
    void approvedTransferCanBeCancelled() {
        final TreasuryTransfer transfer = transfer(TreasuryTransferStatus.APPROVED, 200L);
        actAs(APPROVER);

        this.service.cancelTransfer(transfer.getId(), command("{\"note\": \"Wrong amount\"}"));

        assertEquals(TreasuryTransferStatus.CANCELLED, transfer.getStatus());
        assertEquals(BUSINESS_DATE, transfer.getCancelledOnDate());
        assertEquals("Wrong amount", transfer.getNote());
    }

    @Test
    void senderCannotReceive() {
        final TreasuryTransfer transfer = transfer(TreasuryTransferStatus.SENT, 200L);
        transfer.setSentBy(SENDER);
        transfer.setSentOnDate(BUSINESS_DATE);
        actAs(SENDER);

        final TreasuryWorkflowException exception = assertThrows(TreasuryWorkflowException.class,
                () -> this.service.receiveTransfer(transfer.getId(), command("{}")));
        assertEquals("error.msg.treasury.transfer.same.user", exception.getGlobalisationMessageCode());
    }

    @Test
    void reversingAReceivedTransferReversesReceiveThenSend() {
        final TreasuryTransfer transfer = received();
        when(this.journalPoster.reverse(any(), any(), anyString(), any(), anyString()))
                .thenAnswer(call -> TreasuryJournalPoster.transactionId(call.getArgument(0), call.getArgument(1)));
        actAs(REQUESTER);

        this.service.reverseTransfer(transfer.getId(), command("{\"reason\": \"Duplicate of transfer 6\"}"));

        final InOrder order = inOrder(this.journalPoster);
        order.verify(this.journalPoster).reverse(eq(TreasuryEventType.TRANSFER_RECEIVE_REVERSAL), eq(7L), eq("TR-TFR-7"), eq(BUSINESS_DATE),
                anyString());
        order.verify(this.journalPoster).reverse(eq(TreasuryEventType.TRANSFER_SEND_REVERSAL), eq(7L), eq("TR-TFS-7"), eq(BUSINESS_DATE),
                anyString());
        assertEquals(TreasuryTransferStatus.REVERSED, transfer.getStatus());
        assertEquals("Duplicate of transfer 6", transfer.getReversalReason());
        assertEquals(REQUESTER, transfer.getReversedBy());
        assertEquals(BUSINESS_DATE, transfer.getReversedOnDate());
    }

    @Test
    void reversingASentTransferReversesOnlyTheSend() {
        final TreasuryTransfer transfer = transfer(TreasuryTransferStatus.SENT, 200L);
        transfer.setSentOnDate(BUSINESS_DATE.minusDays(1));
        transfer.setSendTransactionId("TR-TFS-7");
        when(this.journalPoster.reverse(any(), any(), anyString(), any(), anyString())).thenReturn("TR-TFSX-7");
        actAs(SENDER);

        this.service.reverseTransfer(transfer.getId(), command("{\"reason\": \"Bank rejected it\"}"));

        verify(this.journalPoster).reverse(eq(TreasuryEventType.TRANSFER_SEND_REVERSAL), eq(7L), eq("TR-TFS-7"), eq(BUSINESS_DATE),
                anyString());
        verify(this.journalPoster, never()).reverse(eq(TreasuryEventType.TRANSFER_RECEIVE_REVERSAL), any(), any(), any(), any());
        assertEquals(TreasuryTransferStatus.REVERSED, transfer.getStatus());
    }

    @Test
    void reversalNeedsAReason() {
        final TreasuryTransfer transfer = received();
        actAs(REQUESTER);

        final TreasuryWorkflowException exception = assertThrows(TreasuryWorkflowException.class,
                () -> this.service.reverseTransfer(transfer.getId(), command("{}")));
        assertEquals("error.msg.treasury.transfer.reversal.reason.required", exception.getGlobalisationMessageCode());
    }

    @Test
    void receiverCannotReverse() {
        final TreasuryTransfer transfer = received();
        actAs(SENDER);

        assertThrows(TreasuryWorkflowException.class, () -> this.service.reverseTransfer(transfer.getId(), command("{\"reason\": \"x\"}")));
        verify(this.journalPoster, never()).reverse(any(), any(), any(), any(), any());
    }

    @Test
    void unsentTransferCannotBeReversed() {
        final TreasuryTransfer transfer = transfer(TreasuryTransferStatus.APPROVED, 200L);

        final TreasuryWorkflowException exception = assertThrows(TreasuryWorkflowException.class,
                () -> this.service.reverseTransfer(transfer.getId(), command("{\"reason\": \"x\"}")));
        assertEquals("error.msg.treasury.transfer.invalid.status", exception.getGlobalisationMessageCode());
    }

    @Test
    void reversalCannotPrecedeTheLastStep() {
        final TreasuryTransfer transfer = received();
        actAs(REQUESTER);

        assertThrows(TreasuryWorkflowException.class,
                () -> this.service.reverseTransfer(transfer.getId(), command("{\"reason\": \"x\", \"transactionDate\": \"2026-10-14\"}")));
    }

    /** Received on the 15th by the sender role user. */
    private TreasuryTransfer received() {
        final TreasuryTransfer transfer = transfer(TreasuryTransferStatus.RECEIVED, 200L);
        transfer.setSentBy(APPROVER + 10);
        transfer.setSentOnDate(BUSINESS_DATE.minusDays(2));
        transfer.setSendTransactionId("TR-TFS-7");
        transfer.setReceivedBy(SENDER);
        transfer.setReceivedOnDate(BUSINESS_DATE.minusDays(1));
        transfer.setReceiveTransactionId("TR-TFR-7");
        return transfer;
    }

    private List<Leg> postedLegs(final TreasuryEventType eventType, final LocalDate date) {
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<Leg>> legs = ArgumentCaptor.forClass(List.class);
        verify(this.journalPoster).post(eq(eventType), eq(7L), eq("UGX"), eq(date), anyString(), any(), legs.capture());
        return legs.getValue();
    }

    private TreasuryTransfer transfer(final TreasuryTransferStatus status, final Long toAccountId) {
        final TreasuryTransfer transfer = new TreasuryTransfer();
        transfer.setId(7L);
        transfer.setFromAccountId(100L);
        transfer.setToAccountId(toAccountId);
        transfer.setAmount(AMOUNT);
        transfer.setCurrencyCode("UGX");
        transfer.setTransferDate(BUSINESS_DATE.minusDays(3));
        transfer.setStatus(status);
        transfer.setRequestedBy(REQUESTER);
        if (status != TreasuryTransferStatus.REQUESTED) {
            transfer.setApprovedBy(APPROVER);
            transfer.setApprovedOnDate(BUSINESS_DATE.minusDays(3));
        }
        if (status == TreasuryTransferStatus.SENT) {
            transfer.setSentBy(APPROVER + 10);
        }
        when(this.transferRepository.findById(7L)).thenReturn(Optional.of(transfer));
        return transfer;
    }

    private void account(final Long id, final Long officeId, final Long glAccountId, final String currency) {
        final TreasuryAccount account = new TreasuryAccount();
        account.setId(id);
        account.setCode("ACC" + id);
        account.setOfficeId(officeId);
        account.setGlAccountId(glAccountId);
        account.setCurrencyCode(currency);
        account.setStatus(TreasuryAccountStatus.ACTIVE);
        this.accounts.put(id, account);
    }

    private void actAs(final long userId) {
        lenient().when(this.actor.userId(any())).thenReturn(userId);
    }

    private static JsonCommand command(final String json) {
        return JsonCommand.from(json);
    }
}
