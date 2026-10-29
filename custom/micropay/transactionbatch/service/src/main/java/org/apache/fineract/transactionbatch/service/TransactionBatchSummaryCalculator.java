/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Summary rows per currency and value date. A batch balances only when every row does. */
public final class TransactionBatchSummaryCalculator {

    private TransactionBatchSummaryCalculator() {}

    public record Line(String currencyCode, LocalDate valueDate, boolean debit, BigDecimal amount) {}

    public record Row(String currencyCode, LocalDate valueDate, int numberOfDebits, int numberOfCredits, BigDecimal totalDebits,
            BigDecimal totalCredits, BigDecimal netDifference, boolean balanced) {}

    public static List<Row> summarise(final List<Line> lines) {
        final Map<String, Mutable> grouped = new LinkedHashMap<>();
        for (final Line line : lines) {
            final String key = line.currencyCode + "|" + line.valueDate;
            final Mutable row = grouped.computeIfAbsent(key, ignored -> new Mutable(line.currencyCode, line.valueDate));
            if (line.debit) {
                row.debits++;
                row.totalDebits = row.totalDebits.add(line.amount);
            } else {
                row.credits++;
                row.totalCredits = row.totalCredits.add(line.amount);
            }
        }
        final List<Row> rows = new ArrayList<>();
        for (final Mutable row : grouped.values()) {
            final BigDecimal net = row.totalDebits.subtract(row.totalCredits);
            rows.add(new Row(row.currencyCode, row.valueDate, row.debits, row.credits, row.totalDebits, row.totalCredits, net,
                    net.compareTo(BigDecimal.ZERO) == 0));
        }
        return rows;
    }

    public static boolean isBalanced(final List<Row> rows) {
        return !rows.isEmpty() && rows.stream().allMatch(Row::balanced);
    }

    private static final class Mutable {

        private final String currencyCode;
        private final LocalDate valueDate;
        private int debits;
        private int credits;
        private BigDecimal totalDebits = BigDecimal.ZERO;
        private BigDecimal totalCredits = BigDecimal.ZERO;

        private Mutable(final String currencyCode, final LocalDate valueDate) {
            this.currencyCode = currencyCode;
            this.valueDate = valueDate;
        }
    }
}
