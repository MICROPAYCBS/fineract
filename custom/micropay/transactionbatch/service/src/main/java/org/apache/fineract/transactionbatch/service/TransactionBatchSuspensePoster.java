/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
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
import org.apache.fineract.transactionbatch.exception.TransactionBatchRuleException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Suspense against clearing, committed even though the failed line rolled back. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchSuspensePoster {

    private final TransactionBatchItemRepository itemRepository;
    private final TransactionBatchRunRepository runRepository;
    private final TransactionBatchPostingRepository postingRepository;
    private final TransactionBatchSettingRepository settingRepository;
    private final TransactionBatchJournalPoster journalPoster;
    private final TransactionBatchAccess access;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void suspend(final Long runId, final Long itemId, final LocalDate runDate, final Throwable failure) {
        if (this.postingRepository.findByRunIdAndItemId(runId, itemId).isPresent()) {
            return;
        }
        final TransactionBatchRun run = this.runRepository.findById(runId).orElseThrow();
        final TransactionBatchItem item = this.itemRepository.findById(itemId).orElseThrow();
        final TransactionBatch batch = item.getBatch();
        final LocalDate date = batch.isRecurring() ? runDate : item.getValueDate();
        if (batch.isRecurring()) {
            item.setValueDate(runDate);
        }
        final TransactionBatchSetting setting = this.settingRepository.findById(item.getCurrencyCode())
                .orElseThrow(() -> new TransactionBatchRuleException("setting.missing",
                        "No clearing and suspense accounts are configured for " + item.getCurrencyCode(), item.getCurrencyCode()));
        final String transactionId = TransactionBatchJournalPoster.transactionId("TBS", batch.getId(), run.getRunNo(), item.getId());
        if (item.isDebit()) {
            this.journalPoster.postPair(transactionId, batch.getOfficeId(), item.getCurrencyCode(), date, item.getNarrative(),
                    item.getReference(), setting.getSuspenseGlAccountId(), setting.getClearingGlAccountId(), item.getAmount());
        } else {
            this.journalPoster.postPair(transactionId, batch.getOfficeId(), item.getCurrencyCode(), date, item.getNarrative(),
                    item.getReference(), setting.getClearingGlAccountId(), setting.getSuspenseGlAccountId(), item.getAmount());
        }
        final TransactionBatchPosting posting = TransactionBatchItemPoster.newPosting(runId, itemId);
        posting.setStatus(TransactionBatchPostingStatus.SUSPENDED.getValue());
        posting.setSuspenseTransactionId(transactionId);
        posting.setError(message(failure));
        this.postingRepository.save(posting);
        item.setStatus(TransactionBatchItemStatus.SUSPENDED.getValue());
        item.setLastError(posting.getError());
        this.access.touch(item);
    }

    static String message(final Throwable failure) {
        String text = failure.getMessage();
        if (text == null || text.isBlank()) {
            text = failure.getClass().getSimpleName();
        }
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
