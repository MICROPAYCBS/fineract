/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.transactionbatch.service.TransactionBatchSummaryCalculator.Line;
import org.apache.fineract.transactionbatch.service.TransactionBatchSummaryCalculator.Row;
import org.junit.jupiter.api.Test;

class TransactionBatchSummaryCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 29);

    @Test
    void emptyBatchIsNotBalanced() {
        assertFalse(TransactionBatchSummaryCalculator.isBalanced(TransactionBatchSummaryCalculator.summarise(List.of())));
    }

    @Test
    void oneValueDateOffMakesTheBatchUnbalanced() {
        final List<Row> rows = TransactionBatchSummaryCalculator.summarise(List.of(new Line("UGX", DAY, true, new BigDecimal("10")),
                new Line("UGX", DAY, false, new BigDecimal("10")), new Line("UGX", DAY.plusDays(1), true, new BigDecimal("5"))));
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).balanced());
        assertFalse(rows.get(1).balanced());
        assertEquals(new BigDecimal("5"), rows.get(1).netDifference());
        assertFalse(TransactionBatchSummaryCalculator.isBalanced(rows));
    }

    @Test
    void currenciesBalanceSeparately() {
        final List<Row> rows = TransactionBatchSummaryCalculator.summarise(List.of(new Line("UGX", DAY, true, new BigDecimal("10")),
                new Line("UGX", DAY, false, new BigDecimal("10")), new Line("USD", DAY, true, new BigDecimal("2")),
                new Line("USD", DAY, false, new BigDecimal("2"))));
        assertTrue(TransactionBatchSummaryCalculator.isBalanced(rows));
    }
}
