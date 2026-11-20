/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A batch line. {@code officeId}/{@code officeName} are the branch the line's own account posts in: the chosen branch
 * (else the batch's) for a GL line, the account holder's branch for a customer line. {@code crossBranch} is true when
 * that is not the batch's branch: the clearing entry still posts in the batch's branch, bridged by inter-branch
 * entries.
 */
public record TransactionBatchItemData(Long id, Long batchId, String accountType, Long glAccountId, Long savingsAccountId,
        String accountNumber, String accountTitle, String side, String currencyCode, BigDecimal amount, BigDecimal debitAmount,
        BigDecimal creditAmount, String narrative, String reference, LocalDate valueDate, String status, String error, Long createdBy,
        String createdOn, long suspendedRunCount, Long repairPostingId, Long officeId, String officeName, boolean crossBranch) {
}
