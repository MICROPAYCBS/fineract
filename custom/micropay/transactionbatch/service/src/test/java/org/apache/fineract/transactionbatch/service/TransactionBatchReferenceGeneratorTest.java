/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransactionBatchReferenceGeneratorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 26);

    @Test
    void referenceIsCodeThenDayThenSequence() {
        final String reference = new TransactionBatchReferenceGenerator().next("1002639", DAY, 7);
        assertEquals("10026392606260007", reference);
    }

    @Test
    void nextUnusedKeepsTheStartSequenceWhenFree() {
        final String reference = new TransactionBatchReferenceGenerator().nextUnused("TB12", DAY, 3, Set.of("TB122606260001")::contains);
        assertEquals("TB122606260003", reference);
    }

    @Test
    void nextUnusedSkipsReferencesStillInTheBatchAfterADelete() {
        // Lines 0001..0003 were added, 0001 deleted: two lines remain, so counting suggests 0003, which is taken.
        final Set<String> remaining = Set.of("TB122606260002", "TB122606260003");
        final String reference = new TransactionBatchReferenceGenerator().nextUnused("TB12", DAY, 3, remaining::contains);
        assertEquals("TB122606260004", reference);
    }
}
