/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TransactionBatchCrossBranchTest {

    @Test
    void sameBranchIsNotCrossBranch() {
        assertFalse(TransactionBatchReadPlatformService.isCrossBranch(2L, 2L));
    }

    @Test
    void anyOtherBranchIsCrossBranchSubBranchesIncluded() {
        assertTrue(TransactionBatchReadPlatformService.isCrossBranch(1L, 2L));
        assertTrue(TransactionBatchReadPlatformService.isCrossBranch(2L, 3L));
    }

    @Test
    void unknownBranchIsNotCrossBranch() {
        assertFalse(TransactionBatchReadPlatformService.isCrossBranch(null, 1L));
        assertFalse(TransactionBatchReadPlatformService.isCrossBranch(1L, null));
    }
}
