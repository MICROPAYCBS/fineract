/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionBatchReferenceGeneratorTest {

    @Test
    void referenceIsCodeThenDayThenSequence() {
        final String reference = new TransactionBatchReferenceGenerator().next("1002639", LocalDate.of(2026, 6, 26), 7);
        assertEquals("10026392606260007", reference);
    }
}
