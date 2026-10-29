/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.exception;

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;

public class TransactionBatchRuleException extends AbstractPlatformDomainRuleException {

    public TransactionBatchRuleException(final String code, final String message, final Object... args) {
        super("error.msg.transactionbatch." + code, message, args);
    }
}
