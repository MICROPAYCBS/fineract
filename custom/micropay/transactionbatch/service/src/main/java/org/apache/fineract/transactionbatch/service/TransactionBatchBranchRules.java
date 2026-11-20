/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.transactionbatch.domain.TransactionBatch;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Branch checks for one line, run when it is saved or imported and again for every line at submit and approval: the
 * current user may use the line's branch, and a line outside the batch's branch has an inter-branch clearing account,
 * so it does not fail into suspense at posting.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchBranchRules {

    private final TransactionBatchAccess access;
    private final TransactionBatchAccountRules accountRules;
    private final TransactionBatchJournalPoster journalPoster;

    public void requireLineBranch(final TransactionBatch batch, final TransactionBatchItem item) {
        final Long officeId = this.accountRules.postingOfficeId(batch, item);
        this.access.assertLineBranchAllowed(officeId);
        if (!officeId.equals(batch.getOfficeId())) {
            this.journalPoster.interBranchClearingAccountId(batch.getOfficeId(), officeId, item.getCurrencyCode());
        }
    }
}
