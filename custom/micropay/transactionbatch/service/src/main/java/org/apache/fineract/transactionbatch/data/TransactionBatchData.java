/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TransactionBatchData(Long id, String code, String description, Long officeId, String officeName, boolean recurring,
        LocalDate effectiveDate, Integer frequencyNumber, String frequencyUnit, LocalDate nextPostingDate, LocalDate expiryDate,
        String status, Long pendingCommandId, Long submittedBy, LocalDate submittedOn, Long decidedBy, LocalDate decidedOn, Long postedBy,
        LocalDate postedOn, Long copiedFromBatchId, Long createdBy, String createdByUsername, String createdOn, Long lastModifiedBy,
        String lastModifiedOn, List<TransactionBatchItemData> items, List<TransactionBatchSummaryData> summary) {
}
