/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.data;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionBatchItemData(Long id, Long batchId, String accountType, Long glAccountId, Long savingsAccountId,
        String accountNumber, String accountTitle, String side, String currencyCode, BigDecimal amount, BigDecimal debitAmount,
        BigDecimal creditAmount, String narrative, String reference, LocalDate valueDate, String status, String error, Long createdBy,
        String createdOn, long suspendedRunCount, Long repairPostingId) {
}
