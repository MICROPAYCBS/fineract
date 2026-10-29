/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.exception;

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformResourceNotFoundException;

public class TransactionBatchNotFoundException extends AbstractPlatformResourceNotFoundException {

    public TransactionBatchNotFoundException(final Long id) {
        super("error.msg.transactionbatch.not.found", "Transaction batch with id " + id + " does not exist", id);
    }

    public static TransactionBatchNotFoundException item(final Long itemId) {
        return new TransactionBatchNotFoundException("error.msg.transactionbatch.item.not.found",
                "Transaction batch item with id " + itemId + " does not exist", itemId);
    }

    public static TransactionBatchNotFoundException posting(final Long postingId) {
        return new TransactionBatchNotFoundException("error.msg.transactionbatch.posting.not.found",
                "Transaction batch posting with id " + postingId + " does not exist", postingId);
    }

    private TransactionBatchNotFoundException(final String code, final String message, final Long id) {
        super(code, message, id);
    }
}
