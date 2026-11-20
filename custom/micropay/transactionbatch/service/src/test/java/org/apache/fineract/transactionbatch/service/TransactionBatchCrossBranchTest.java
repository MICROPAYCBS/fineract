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
        assertFalse(TransactionBatchReadPlatformService.isCrossBranch(".1.2.", ".1.2."));
    }

    @Test
    void subBranchIsNotCrossBranch() {
        assertFalse(TransactionBatchReadPlatformService.isCrossBranch(".1.", ".1.2."));
    }

    @Test
    void siblingOrParentBranchIsCrossBranch() {
        assertTrue(TransactionBatchReadPlatformService.isCrossBranch(".1.2.", ".1.3."));
        assertTrue(TransactionBatchReadPlatformService.isCrossBranch(".1.2.", ".1."));
    }

    @Test
    void unknownHierarchyIsNotCrossBranch() {
        assertFalse(TransactionBatchReadPlatformService.isCrossBranch(null, ".1."));
        assertFalse(TransactionBatchReadPlatformService.isCrossBranch(".1.", null));
    }
}
