/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.data;

import java.util.List;

public record TransactionBatchPageData(int totalFilteredRecords, List<TransactionBatchData> pageItems) {
}
