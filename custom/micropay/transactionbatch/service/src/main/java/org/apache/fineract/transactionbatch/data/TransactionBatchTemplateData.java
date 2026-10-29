/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.data;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.organisation.monetary.data.CurrencyData;

public record TransactionBatchTemplateData(LocalDate businessDate, List<String> statuses, List<String> accountTypes, List<String> sides,
        List<String> frequencyUnits, List<CurrencyData> currencies) {
}
