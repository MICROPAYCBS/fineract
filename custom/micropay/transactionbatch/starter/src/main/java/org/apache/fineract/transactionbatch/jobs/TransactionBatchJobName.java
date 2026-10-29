/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.jobs;

/** Spring Batch name. The scheduler row uses the human name and the 8-character short name TXN_BTCH. */
public enum TransactionBatchJobName {

    TXN_BATCH_POST("Transaction Batch Posting");

    private final String name;

    TransactionBatchJobName(final String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
