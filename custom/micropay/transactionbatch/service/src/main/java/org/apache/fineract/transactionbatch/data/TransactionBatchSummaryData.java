/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.data;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionBatchSummaryData(String currencyCode, LocalDate valueDate, int numberOfDebits, int numberOfCredits,
        BigDecimal totalDebits, BigDecimal totalCredits, BigDecimal netDifference, boolean balanced) {
}
