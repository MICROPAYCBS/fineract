/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionBatchFrequencyTest {

    @Test
    void plusStepsByTheUnit() {
        final LocalDate start = LocalDate.of(2026, 1, 31);
        assertEquals(LocalDate.of(2026, 2, 2), TransactionBatchFrequency.DAYS.plus(start, 2));
        assertEquals(LocalDate.of(2026, 2, 28), TransactionBatchFrequency.MONTHS.plus(start, 1));
        assertEquals(LocalDate.of(2027, 1, 31), TransactionBatchFrequency.YEARS.plus(start, 1));
    }
}
