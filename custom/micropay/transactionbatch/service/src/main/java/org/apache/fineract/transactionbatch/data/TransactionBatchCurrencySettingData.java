/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.data;

public record TransactionBatchCurrencySettingData(String currencyCode, Long clearingGlAccountId, Long suspenseGlAccountId) {
}
