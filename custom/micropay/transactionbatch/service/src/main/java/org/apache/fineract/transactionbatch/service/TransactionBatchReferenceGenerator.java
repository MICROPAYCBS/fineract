/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Reference format from design Q4: {@code {batch code}{yyMMdd}{sequence}}. Kept in one class so the format can change.
 */
@Component
public class TransactionBatchReferenceGenerator {

    public String next(final String batchCode, final LocalDate date, final int sequence) {
        final String day = date.format(DateTimeFormatter.ofPattern("yyMMdd"));
        return batchCode + day + String.format("%04d", sequence);
    }
}
