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

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_TRANSFER_RESOURCE_NAME;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.interbranch.service.InterBranchGlAccountReadService;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryEventType;
import org.apache.fineract.treasury.domain.TreasurySegregation;
import org.apache.fineract.treasury.domain.TreasuryTransfer;
import org.apache.fineract.treasury.domain.TreasuryTransferRepository;
import org.apache.fineract.treasury.domain.TreasuryTransferStatus;
import org.apache.fineract.treasury.exception.TreasuryAccountNotFoundException;
import org.apache.fineract.treasury.exception.TreasuryTransferNotFoundException;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator.ActionRequest;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator.TransferRequest;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Funding transfers between treasury accounts. Nothing reaches the GL before the transfer is sent, so a rejected or
 * cancelled transfer leaves nothing to reverse.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryTransferWritePlatformServiceImpl implements TreasuryTransferWritePlatformService {

    static final String ENTITY = "transfer";

    private final TreasuryActor actor;
    private final TreasuryWorkflowDataValidator validator;
    private final TreasuryTransferRepository transferRepository;
    private final TreasuryAccountRepository accountRepository;
    private final TreasurySettingService settingService;
    private final TreasuryBalanceReader balanceReader;
    private final TreasuryJournalPoster journalPoster;
    private final InterBranchGlAccountReadService interBranchGlAccountReadService;

    @Override
    @Transactional
    public CommandProcessingResult createTransfer(final JsonCommand command) {
        final TransferRequest request = this.validator.validateTransferForCreate(command.json());
        final TreasuryAccount from = activeAccount(request.fromAccountId());
        final TreasuryAccount to = activeAccount(request.toAccountId());
        if (!from.getCurrencyCode().equals(to.getCurrencyCode())) {
            throw new TreasuryWorkflowException(ENTITY, "currency.mismatch", "Treasury accounts " + from.getCode() + " ("
                    + from.getCurrencyCode() + ") and " + to.getCode() + " (" + to.getCurrencyCode() + ") are in different currencies",
                    from.getId(), to.getId());
        }
        final LocalDate transferDate = request.transferDate() == null ? DateUtils.getBusinessLocalDate() : request.transferDate();
        notAfterBusinessDate(transferDate);

        final TreasuryTransfer transfer = new TreasuryTransfer();
        transfer.setFromAccountId(from.getId());
        transfer.setToAccountId(to.getId());
        transfer.setAmount(request.amount());
        transfer.setCurrencyCode(from.getCurrencyCode());
        transfer.setTransferDate(transferDate);
        transfer.setReference(request.reference());
        transfer.setDescription(request.description());
        transfer.setStatus(TreasuryTransferStatus.REQUESTED);
        transfer.setRequestedBy(this.actor.userId(command));
        this.transferRepository.saveAndFlush(transfer);
        return result(command, transfer, Map.of("status", transfer.getStatus().name()));
    }

    @Override
    @Transactional
    public CommandProcessingResult approveTransfer(final Long transferId, final JsonCommand command) {
        final ActionRequest action = this.validator.validateAction(TREASURY_TRANSFER_RESOURCE_NAME, command.json());
        final TreasuryTransfer transfer = find(transferId);
        requireStep(transfer, transfer.getStatus().canApprove(), "approved");
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(ENTITY, transferId, "requested", transfer.getRequestedBy(), "approve", userId);
        final TreasuryAccount from = activeAccount(transfer.getFromAccountId());
        final TreasuryAccount to = activeAccount(transfer.getToAccountId());
        // fail now rather than at receive when the offices have no inter-branch clearing account
        clearingAccount(from, to);

        transfer.setStatus(TreasuryTransferStatus.APPROVED);
        transfer.setApprovedBy(userId);
        transfer.setApprovedOnDate(DateUtils.getBusinessLocalDate());
        note(transfer, action);
        this.transferRepository.saveAndFlush(transfer);
        return result(command, transfer, Map.of("status", transfer.getStatus().name()));
    }

    @Override
    @Transactional
    public CommandProcessingResult sendTransfer(final Long transferId, final JsonCommand command) {
        final ActionRequest action = this.validator.validateAction(TREASURY_TRANSFER_RESOURCE_NAME, command.json());
        final TreasuryTransfer transfer = find(transferId);
        requireStep(transfer, transfer.getStatus().canSend(), "sent");
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(ENTITY, transferId, "approved", transfer.getApprovedBy(), "send", userId);
        final TreasuryAccount from = activeAccount(transfer.getFromAccountId());
        activeAccount(transfer.getToAccountId());
        final LocalDate sendDate = stepDate(action, transfer.getTransferDate(), "transfer date");
        requireBalance(transfer, from);

        final Long inTransitGl = this.settingService.requireCashInTransitGlAccount(transfer.getCurrencyCode());
        final List<Leg> legs = TreasuryPostingPlanner.transferSend(from.getOfficeId(), from.getGlAccountId(), inTransitGl,
                transfer.getAmount());
        final String transactionId = this.journalPoster.post(TreasuryEventType.TRANSFER_SEND, transferId, transfer.getCurrencyCode(),
                sendDate, description(transfer, "sent"), transfer.getReference(), legs);

        transfer.setStatus(TreasuryTransferStatus.SENT);
        transfer.setSentBy(userId);
        transfer.setSentOnDate(sendDate);
        transfer.setInTransitGlAccountId(inTransitGl);
        transfer.setSendTransactionId(transactionId);
        note(transfer, action);
        this.transferRepository.saveAndFlush(transfer);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("status", transfer.getStatus().name());
        changes.put("transactionId", transactionId);
        return result(command, transfer, changes);
    }

    @Override
    @Transactional
    public CommandProcessingResult receiveTransfer(final Long transferId, final JsonCommand command) {
        final ActionRequest action = this.validator.validateAction(TREASURY_TRANSFER_RESOURCE_NAME, command.json());
        final TreasuryTransfer transfer = find(transferId);
        requireStep(transfer, transfer.getStatus().canReceive(), "received");
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(ENTITY, transferId, "sent", transfer.getSentBy(), "receive", userId);
        final TreasuryAccount from = account(transfer.getFromAccountId());
        final TreasuryAccount to = activeAccount(transfer.getToAccountId());
        final LocalDate receiveDate = stepDate(action, transfer.getSentOnDate(), "send date");

        final List<Leg> legs = TreasuryPostingPlanner.transferReceive(from.getOfficeId(), to.getOfficeId(), to.getGlAccountId(),
                transfer.getInTransitGlAccountId(), clearingAccount(from, to), transfer.getAmount());
        final String transactionId = this.journalPoster.post(TreasuryEventType.TRANSFER_RECEIVE, transferId, transfer.getCurrencyCode(),
                receiveDate, description(transfer, "received"), transfer.getReference(), legs);

        transfer.setStatus(TreasuryTransferStatus.RECEIVED);
        transfer.setReceivedBy(userId);
        transfer.setReceivedOnDate(receiveDate);
        transfer.setReceiveTransactionId(transactionId);
        note(transfer, action);
        this.transferRepository.saveAndFlush(transfer);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("status", transfer.getStatus().name());
        changes.put("transactionId", transactionId);
        return result(command, transfer, changes);
    }

    /**
     * Undoes a sent or received transfer by reversing its postings, the receive first, each under its own transaction
     * id. The user who took the last step may not reverse it, and a reason is required.
     */
    @Override
    @Transactional
    public CommandProcessingResult reverseTransfer(final Long transferId, final JsonCommand command) {
        final ActionRequest action = this.validator.validateAction(TREASURY_TRANSFER_RESOURCE_NAME, command.json());
        final TreasuryTransfer transfer = find(transferId);
        requireStep(transfer, transfer.getStatus().canReverse(), "reversed");
        if (action.reason() == null) {
            throw new TreasuryWorkflowException(ENTITY, "reversal.reason.required",
                    "A reason is required to reverse transfer " + transferId, transferId);
        }
        final boolean received = transfer.getStatus() == TreasuryTransferStatus.RECEIVED;
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(ENTITY, transferId, received ? "received" : "sent",
                received ? transfer.getReceivedBy() : transfer.getSentBy(), "reverse", userId);
        final LocalDate date = stepDate(action, received ? transfer.getReceivedOnDate() : transfer.getSentOnDate(),
                received ? "receive date" : "send date");

        final String description = description(transfer, "reversed: " + action.reason());
        final Map<String, Object> changes = new LinkedHashMap<>();
        if (received) {
            changes.put("receiveReversalTransactionId", this.journalPoster.reverse(TreasuryEventType.TRANSFER_RECEIVE_REVERSAL, transferId,
                    transfer.getReceiveTransactionId(), date, description));
        }
        changes.put("sendReversalTransactionId", this.journalPoster.reverse(TreasuryEventType.TRANSFER_SEND_REVERSAL, transferId,
                transfer.getSendTransactionId(), date, description));

        transfer.setStatus(TreasuryTransferStatus.REVERSED);
        transfer.setReversedBy(userId);
        transfer.setReversedOnDate(date);
        transfer.setReversalReason(action.reason());
        note(transfer, action);
        this.transferRepository.saveAndFlush(transfer);
        changes.put("status", transfer.getStatus().name());
        return result(command, transfer, changes);
    }

    @Override
    @Transactional
    public CommandProcessingResult cancelTransfer(final Long transferId, final JsonCommand command) {
        final ActionRequest action = this.validator.validateAction(TREASURY_TRANSFER_RESOURCE_NAME, command.json());
        final TreasuryTransfer transfer = find(transferId);
        if (!transfer.getStatus().canCancel()) {
            throw new TreasuryWorkflowException(ENTITY, "cannot.cancel", "Transfer " + transferId + " is " + transfer.getStatus()
                    + "; only a requested or approved transfer can be cancelled, a sent one is reversed", transferId);
        }
        transfer.setStatus(TreasuryTransferStatus.CANCELLED);
        transfer.setCancelledBy(this.actor.userId(command));
        transfer.setCancelledOnDate(DateUtils.getBusinessLocalDate());
        note(transfer, action);
        this.transferRepository.saveAndFlush(transfer);
        return result(command, transfer, Map.of("status", transfer.getStatus().name()));
    }

    /**
     * Blocks a send that would take the source account's GL balance below zero, unless the currency's settings allow
     * it.
     */
    private void requireBalance(final TreasuryTransfer transfer, final TreasuryAccount from) {
        if (this.settingService.settingsFor(transfer.getCurrencyCode()).isAllowInsufficientBalance()) {
            return;
        }
        final BigDecimal balance = this.balanceReader.glBalances(List.of(from.getId()), null).getOrDefault(from.getId(), BigDecimal.ZERO);
        if (balance.compareTo(transfer.getAmount()) < 0) {
            throw new TreasuryWorkflowException(ENTITY, "insufficient.balance",
                    "Treasury account " + from.getCode() + " has " + transfer.getCurrencyCode() + " " + balance.toPlainString()
                            + ", less than the " + transfer.getAmount().toPlainString() + " to send",
                    from.getId(), balance, transfer.getAmount());
        }
    }

    /** {@code null} when both accounts are in one office. */
    private Long clearingAccount(final TreasuryAccount from, final TreasuryAccount to) {
        if (Objects.equals(from.getOfficeId(), to.getOfficeId())) {
            return null;
        }
        return this.interBranchGlAccountReadService.resolveClearingAccount(from.getOfficeId(), to.getOfficeId(), from.getCurrencyCode())
                .getId();
    }

    private LocalDate stepDate(final ActionRequest action, final LocalDate notBefore, final String notBeforeName) {
        final LocalDate date = action.transactionDate() == null ? DateUtils.getBusinessLocalDate() : action.transactionDate();
        notAfterBusinessDate(date);
        if (notBefore != null && date.isBefore(notBefore)) {
            throw new TreasuryWorkflowException(ENTITY, "date.before." + notBeforeName.replace(' ', '.'),
                    "The date " + date + " is before the " + notBeforeName + " " + notBefore, date, notBefore);
        }
        return date;
    }

    private static void notAfterBusinessDate(final LocalDate date) {
        if (DateUtils.isAfterBusinessDate(date)) {
            throw new TreasuryWorkflowException(ENTITY, "future.date",
                    "The date " + date + " is after the business date " + DateUtils.getBusinessLocalDate(), date);
        }
    }

    private static void requireStep(final TreasuryTransfer transfer, final boolean allowed, final String step) {
        if (!allowed) {
            throw new TreasuryWorkflowException(ENTITY, "invalid.status",
                    "Transfer " + transfer.getId() + " is " + transfer.getStatus() + " and cannot be " + step, transfer.getId(),
                    transfer.getStatus().name());
        }
    }

    private static void note(final TreasuryTransfer transfer, final ActionRequest action) {
        if (action.note() != null) {
            transfer.setNote(action.note());
        }
    }

    private static String description(final TreasuryTransfer transfer, final String step) {
        final String text = "Treasury transfer " + transfer.getId() + " " + step
                + (transfer.getDescription() == null ? "" : ": " + transfer.getDescription());
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private TreasuryTransfer find(final Long transferId) {
        return this.transferRepository.findById(transferId).orElseThrow(() -> new TreasuryTransferNotFoundException(transferId));
    }

    private TreasuryAccount account(final Long accountId) {
        return this.accountRepository.findById(accountId).orElseThrow(() -> new TreasuryAccountNotFoundException(accountId));
    }

    private TreasuryAccount activeAccount(final Long accountId) {
        final TreasuryAccount account = account(accountId);
        if (!account.isActive()) {
            throw new TreasuryWorkflowException(ENTITY, "account.closed", "Treasury account " + account.getCode() + " is closed",
                    accountId);
        }
        return account;
    }

    private static CommandProcessingResult result(final JsonCommand command, final TreasuryTransfer transfer,
            final Map<String, Object> changes) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(transfer.getId())
                .with(new LinkedHashMap<>(changes)).build();
    }
}
