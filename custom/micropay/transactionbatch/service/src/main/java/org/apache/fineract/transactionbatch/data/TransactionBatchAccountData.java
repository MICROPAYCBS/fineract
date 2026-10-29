/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.data;

public record TransactionBatchAccountData(Long id, String accountNo, String clientName, String currencyCode, Long officeId) {
}
