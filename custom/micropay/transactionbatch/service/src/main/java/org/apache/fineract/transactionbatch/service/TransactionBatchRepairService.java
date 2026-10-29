/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.GL_ACCOUNT_ID;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.transactionbatch.domain.TransactionBatch;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItem;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItemRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItemStatus;
import org.apache.fineract.transactionbatch.domain.TransactionBatchPosting;
import org.apache.fineract.transactionbatch.domain.TransactionBatchPostingRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchPostingStatus;
import org.apache.fineract.transactionbatch.domain.TransactionBatchRun;
import org.apache.fineract.transactionbatch.domain.TransactionBatchRunRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchSetting;
import org.apache.fineract.transactionbatch.domain.TransactionBatchSettingRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchStatus;
import org.apache.fineract.transactionbatch.exception.TransactionBatchNotFoundException;
import org.apache.fineract.transactionbatch.exception.TransactionBatchRuleException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchRepairService {

    private final TransactionBatchAccess access;
    private final TransactionBatchItemRepository itemRepository;
    private final TransactionBatchPostingRepository postingRepository;
    private final TransactionBatchRunRepository runRepository;
    private final TransactionBatchSettingRepository settingRepository;
    private final TransactionBatchJournalPoster journalPoster;
    private final TransactionBatchCustomerPoster customerPoster;
    private final TransactionBatchAccountRules accountRules;

    @Transactional
    public CommandProcessingResult repair(final JsonCommand command) {
        final TransactionBatchItem item = this.itemRepository.findById(command.entityId())
                .orElseThrow(() -> TransactionBatchNotFoundException.item(command.entityId()));
        final TransactionBatch batch = item.getBatch();
        this.access.assertOffice(batch.getOfficeId());
        if (!batch.isActiveStatus() && !batch.isPartiallyPosted()) {
            throw new TransactionBatchRuleException("repair.status", "A line can be repaired only while the batch is Active or Partially Posted");
        }
        final TransactionBatchPosting posting = this.postingRepository.findByIdAndItemId(command.subentityId(), item.getId())
                .orElseThrow(() -> TransactionBatchNotFoundException.posting(command.subentityId()));
        if (!posting.isSuspended()) {
            throw new TransactionBatchRuleException("repair.not.suspended", "Only a suspended posting can be repaired");
        }
        final Long replacementGl = command.parameterExists(GL_ACCOUNT_ID) ? command.longValueOfParameterNamed(GL_ACCOUNT_ID) : null;
        if (replacementGl != null && !item.accountType().isGl()) {
            throw new TransactionBatchRuleException("repair.gl.customer", "A customer line cannot be repaired onto a GL account");
        }
        if (replacementGl != null) {
            this.accountRules.requirePostableGl(replacementGl);
            item.setGlAccountId(replacementGl);
        }
        final TransactionBatchSetting setting = this.settingRepository.findById(item.getCurrencyCode())
                .orElseThrow(() -> new TransactionBatchRuleException("setting.missing",
                        "No clearing and suspense accounts are configured for " + item.getCurrencyCode(), item.getCurrencyCode()));
        final TransactionBatchRun run = this.runRepository.findById(posting.getRunId()).orElseThrow();
        final String transactionId = TransactionBatchJournalPoster.transactionId("TBR", batch.getId(), run.getRunNo(), item.getId());
        final var businessDate = DateUtils.getBusinessLocalDate();
        if (item.accountType().isGl()) {
            if (item.isDebit()) {
                this.journalPoster.postPair(transactionId, batch.getOfficeId(), item.getCurrencyCode(), businessDate, item.getNarrative(),
                        item.getReference(), item.getGlAccountId(), setting.getSuspenseGlAccountId(), item.getAmount());
            } else {
                this.journalPoster.postPair(transactionId, batch.getOfficeId(), item.getCurrencyCode(), businessDate, item.getNarrative(),
                        item.getReference(), setting.getSuspenseGlAccountId(), item.getGlAccountId(), item.getAmount());
            }
            posting.setRepairJournalTransactionId(transactionId);
        } else {
            posting.setRepairSavingsTransactionId(this.customerPoster.post(batch, item, setting.getSuspenseGlAccountId(), businessDate));
        }
        posting.setStatus(TransactionBatchPostingStatus.REPAIRED.getValue());
        posting.setRepairedBy(this.access.user().getId());
        posting.setRepairedOn(businessDate);
        posting.setError(null);
        this.postingRepository.saveAndFlush(posting);
        item.setStatus(TransactionBatchItemStatus.POSTED.getValue());
        item.setLastError(null);
        this.access.touch(item);
        if (batch.isPartiallyPosted()
                && this.postingRepository.countUnrepaired(batch.getId(), TransactionBatchPostingStatus.SUSPENDED.getValue()) == 0) {
            batch.setStatus(TransactionBatchStatus.POSTED.getValue());
            this.access.touch(batch);
        }
        return new CommandProcessingResultBuilder().withEntityId(item.getId()).withOfficeId(batch.getOfficeId()).build();
    }
}
