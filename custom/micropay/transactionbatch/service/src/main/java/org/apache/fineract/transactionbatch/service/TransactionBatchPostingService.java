/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatch;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItem;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItemRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchPostingRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchRun;
import org.apache.fineract.transactionbatch.domain.TransactionBatchStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchPostingService {

    private final TransactionBatchRepository batchRepository;
    private final TransactionBatchItemRepository itemRepository;
    private final TransactionBatchPostingRepository postingRepository;
    private final SavingsAccountRepository savingsAccountRepository;
    private final TransactionBatchJournalPoster journalPoster;
    private final TransactionBatchItemPoster itemPoster;
    private final TransactionBatchSuspensePoster suspensePoster;
    private final TransactionBatchRunTx runTx;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void postDueBatches() {
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        for (final Long batchId : this.batchRepository.findDueIds(TransactionBatchStatus.ACTIVE.getValue(), businessDate)) {
            try {
                postBatch(batchId, businessDate);
            } catch (final RuntimeException ex) {
                log.error("Transaction batch {} was not posted", batchId, ex);
            }
        }
    }

    private void postBatch(final Long batchId, final LocalDate businessDate) {
        while (true) {
            final TransactionBatch batch = this.batchRepository.findById(batchId).orElseThrow();
            if (!batch.isActiveStatus()) {
                return;
            }
            final LocalDate runDate;
            if (batch.isRecurring()) {
                runDate = batch.getNextPostingDate();
                if (runDate == null || runDate.isAfter(businessDate)) {
                    return;
                }
                if (batch.getExpiryDate() != null && runDate.isAfter(batch.getExpiryDate())) {
                    this.runTx.finalizeWithoutRun(batchId);
                    return;
                }
            } else {
                if (batch.getEffectiveDate() != null && batch.getEffectiveDate().isAfter(businessDate)) {
                    return;
                }
                runDate = businessDate;
            }
            final List<TransactionBatchItem> items = this.itemRepository.findByBatch_IdOrderByIdAsc(batchId);
            if (periodClosed(batch, items, runDate)) {
                log.info("Skipping transaction batch {} on {} because an office period is closed", batchId, runDate);
                return;
            }
            final TransactionBatchRun run = this.runTx.start(batchId, runDate);
            if (!run.isCompleted() && !postItems(run, items, runDate)) {
                return;
            }
            if (!run.isCompleted()) {
                this.runTx.complete(run.getId());
            }
            this.runTx.advance(batchId, run.getRunDate());
            if (!batch.isRecurring()) {
                return;
            }
        }
    }

    private boolean postItems(final TransactionBatchRun run, final List<TransactionBatchItem> items, final LocalDate runDate) {
        boolean suspenseFailed = false;
        for (final TransactionBatchItem item : items) {
            if (this.postingRepository.findByRunIdAndItemId(run.getId(), item.getId()).isPresent()) {
                continue;
            }
            try {
                this.itemPoster.post(run.getId(), item.getId(), runDate);
            } catch (final RuntimeException ex) {
                log.warn("Transaction batch item {} failed and will be suspended: {}", item.getId(), ex.toString());
                try {
                    this.suspensePoster.suspend(run.getId(), item.getId(), runDate, ex);
                } catch (final RuntimeException suspense) {
                    log.error("Suspense posting failed for item {}; the run stays in progress", item.getId(), suspense);
                    suspenseFailed = true;
                }
            }
        }
        if (suspenseFailed) {
            return false;
        }
        final Set<Long> posted = new HashSet<>(this.postingRepository.findItemIdsByRunId(run.getId()));
        return posted.size() >= items.size();
    }

    private boolean periodClosed(final TransactionBatch batch, final List<TransactionBatchItem> items, final LocalDate runDate) {
        for (final TransactionBatchItem item : items) {
            final LocalDate date = batch.isRecurring() ? runDate : item.getValueDate();
            if (this.journalPoster.isClosed(batch.getOfficeId(), date)) {
                return true;
            }
            if (item.accountType().isSavings()) {
                final SavingsAccount account = this.savingsAccountRepository.findById(item.getSavingsAccountId()).orElse(null);
                if (account != null && account.office() != null && this.journalPoster.isClosed(account.office().getId(), date)) {
                    return true;
                }
            }
        }
        return false;
    }
}
