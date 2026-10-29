/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.transactionbatch.domain.TransactionBatch;
import org.apache.fineract.transactionbatch.domain.TransactionBatchFrequency;
import org.apache.fineract.transactionbatch.domain.TransactionBatchPosting;
import org.apache.fineract.transactionbatch.domain.TransactionBatchPostingRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchPostingStatus;
import org.apache.fineract.transactionbatch.domain.TransactionBatchRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchRun;
import org.apache.fineract.transactionbatch.domain.TransactionBatchRunRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchRunStatus;
import org.apache.fineract.transactionbatch.domain.TransactionBatchStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Run and batch updates each commit on their own. A line transaction cannot see an uncommitted run row, and the batch
 * lock must not be held while lines post.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchRunTx {

    private final TransactionBatchRepository batchRepository;
    private final TransactionBatchRunRepository runRepository;
    private final TransactionBatchPostingRepository postingRepository;
    private final TransactionBatchAccess access;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionBatchRun start(final Long batchId, final LocalDate runDate) {
        this.batchRepository.findByIdForUpdate(batchId).orElseThrow();
        return this.runRepository.findByBatchIdAndRunDate(batchId, runDate).orElseGet(() -> {
            final TransactionBatchRun run = new TransactionBatchRun();
            run.setBatchId(batchId);
            run.setRunNo(this.runRepository.maxRunNo(batchId) + 1);
            run.setRunDate(runDate);
            run.setStatus(TransactionBatchRunStatus.IN_PROGRESS.getValue());
            run.setPostedCount(0);
            run.setSuspendedCount(0);
            run.setStartedOnUtc(DateUtils.getAuditOffsetDateTime());
            run.setRunBy(this.access.user().getId());
            return this.runRepository.saveAndFlush(run);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(final Long runId) {
        final TransactionBatchRun run = this.runRepository.findById(runId).orElseThrow();
        int posted = 0;
        int suspended = 0;
        for (final TransactionBatchPosting posting : this.postingRepository.findByRunId(runId)) {
            if (TransactionBatchPostingStatus.SUSPENDED.getValue().equals(posting.getStatus())) {
                suspended++;
            } else if (TransactionBatchPostingStatus.POSTED.getValue().equals(posting.getStatus())) {
                posted++;
            }
        }
        run.setPostedCount(posted);
        run.setSuspendedCount(suspended);
        run.setStatus(TransactionBatchRunStatus.COMPLETED.getValue());
        run.setCompletedOnUtc(DateUtils.getAuditOffsetDateTime());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void advance(final Long batchId, final LocalDate runDate) {
        final TransactionBatch batch = this.batchRepository.findById(batchId).orElseThrow();
        if (!batch.isRecurring()) {
            finish(batch);
            return;
        }
        final LocalDate next = TransactionBatchFrequency.fromInt(batch.getFrequencyUnit()).plus(runDate, batch.getFrequencyNumber());
        batch.setNextPostingDate(next);
        if (batch.getExpiryDate() != null && next.isAfter(batch.getExpiryDate())) {
            finish(batch);
        }
        this.access.touch(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeWithoutRun(final Long batchId) {
        final TransactionBatch batch = this.batchRepository.findById(batchId).orElseThrow();
        if (batch.isActiveStatus()) {
            finish(batch);
            this.access.touch(batch);
        }
    }

    private void finish(final TransactionBatch batch) {
        final long unrepaired = this.postingRepository.countUnrepaired(batch.getId(), TransactionBatchPostingStatus.SUSPENDED.getValue());
        batch.setStatus(unrepaired == 0 ? TransactionBatchStatus.POSTED.getValue() : TransactionBatchStatus.PARTIALLY_POSTED.getValue());
        batch.setPostedBy(this.access.user().getId());
        batch.setPostedOn(DateUtils.getBusinessLocalDate());
    }
}
