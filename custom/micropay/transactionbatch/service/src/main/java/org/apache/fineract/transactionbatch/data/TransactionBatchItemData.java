/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A batch line. {@code officeId}/{@code officeName} are the branch the line posts in: the batch's branch for a GL line,
 * the account holder's branch for a customer line. {@code crossBranch} is true for a customer line whose branch is
 * outside the batch's branch and its sub-branches: its clearing entry then posts in the batch's branch, balanced by
 * inter-branch entries.
 */
public record TransactionBatchItemData(Long id, Long batchId, String accountType, Long glAccountId, Long savingsAccountId,
        String accountNumber, String accountTitle, String side, String currencyCode, BigDecimal amount, BigDecimal debitAmount,
        BigDecimal creditAmount, String narrative, String reference, LocalDate valueDate, String status, String error, Long createdBy,
        String createdOn, long suspendedRunCount, Long repairPostingId, Long officeId, String officeName, boolean crossBranch) {
}
