/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.data;

/**
 * A customer account a batch line can use. {@code officeId}/{@code officeName} are the holder's branch, where the
 * line's customer entry posts. {@code crossBranch} is set only when the search names a batch: true when that branch is
 * outside the batch's branch and its sub-branches.
 */
public record TransactionBatchAccountData(Long id, String accountNo, String clientName, String currencyCode, Long officeId,
        String officeName, Boolean crossBranch) {
}
