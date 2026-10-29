/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.infrastructure.dataqueries.service.CleanupService;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.transactionbatch.domain.TransactionBatch;
import org.apache.fineract.transactionbatch.domain.TransactionBatchRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** A rejected SUBMIT leaves the batch Rejected. The hold itself never stored SUBMITTED. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchRejectionCleanupService implements CleanupService {

    private final TransactionBatchRepository batchRepository;

    @Override
    public void cleanup(final CommandSource commandSource) {
        if (!"TRANSACTIONBATCH".equals(commandSource.getEntityName()) || !"SUBMIT".equals(commandSource.getActionName())) {
            return;
        }
        if (commandSource.getResourceId() == null) {
            return;
        }
        this.batchRepository.findById(commandSource.getResourceId()).ifPresent(this::reject);
    }

    private void reject(final TransactionBatch batch) {
        if (!batch.isInactive()) {
            return;
        }
        batch.setStatus(TransactionBatchStatus.REJECTED.getValue());
        batch.setDecidedOn(DateUtils.getBusinessLocalDate());
        if (batch.getSubmittedOn() == null) {
            batch.setSubmittedOn(DateUtils.getBusinessLocalDate());
        }
    }
}
