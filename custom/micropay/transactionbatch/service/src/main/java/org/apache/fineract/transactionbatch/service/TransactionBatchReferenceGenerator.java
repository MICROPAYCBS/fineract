/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;
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

    /**
     * First reference from {@code startSequence} upwards that {@code taken} does not claim. Counting lines alone
     * repeats a reference after a line is deleted (three lines, delete the first, add one: the count gives sequence 3
     * again).
     */
    public String nextUnused(final String batchCode, final LocalDate date, final int startSequence, final Predicate<String> taken) {
        int sequence = Math.max(1, startSequence);
        String reference = next(batchCode, date, sequence);
        while (taken.test(reference)) {
            sequence++;
            reference = next(batchCode, date, sequence);
        }
        return reference;
    }
}
