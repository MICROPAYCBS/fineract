/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

import com.micropay.fineract.infrastructure.interbranch.MicropayCrossBranchTransactionAccessService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItem;
import org.junit.jupiter.api.Test;

class TransactionBatchCustomerPosterTest {

    private final TransactionBatchCustomerPoster poster = new TransactionBatchCustomerPoster(mock(SavingsAccountWritePlatformService.class),
            mock(MicropayCrossBranchTransactionAccessService.class), mock(OfficeRepositoryWrapper.class), new FromJsonHelper(),
            mock(TransactionBatchAccess.class), mock(TransactionBatchAccountRules.class));

    private static TransactionBatchItem item() {
        final TransactionBatchItem item = new TransactionBatchItem();
        item.setSavingsAccountId(42L);
        item.setAmount(new BigDecimal("1500.00"));
        item.setNarrative("November salary");
        item.setReference("TB122611200003");
        return item;
    }

    @Test
    void externalIdIsThePostingIdNotTheLineReference() {
        final JsonCommand command = this.poster.command(item(), 7L, LocalDate.of(2026, 11, 20), "TB-12-1-99");
        assertEquals("TB-12-1-99", command.stringValueOfParameterNamed("externalId"));
    }

    @Test
    void eachRunOfARecurringLineGetsItsOwnExternalId() {
        // Savings transaction external ids are unique across the tenant: run 2 must not reuse run 1's id.
        final TransactionBatchItem line = item();
        final String run1 = TransactionBatchJournalPoster.transactionId("TB", 12L, 1, 99L);
        final String run2 = TransactionBatchJournalPoster.transactionId("TB", 12L, 2, 99L);
        final String repair = TransactionBatchJournalPoster.transactionId("TBR", 12L, 2, 99L);
        assertNotEquals(this.poster.command(line, 7L, LocalDate.of(2026, 11, 20), run1).stringValueOfParameterNamed("externalId"),
                this.poster.command(line, 7L, LocalDate.of(2026, 12, 20), run2).stringValueOfParameterNamed("externalId"));
        assertNotEquals(run2, repair);
    }

    @Test
    void noteCarriesTheLineReference() {
        final JsonCommand command = this.poster.command(item(), 7L, LocalDate.of(2026, 11, 20), "TB-12-1-99");
        assertEquals("November salary (Ref TB122611200003)", command.stringValueOfParameterNamed("note"));
    }

    @Test
    void noteWithoutNarrativeIsJustTheReference() {
        final TransactionBatchItem line = item();
        line.setNarrative(null);
        assertEquals("Ref TB122611200003", TransactionBatchCustomerPoster.note(line));
    }
}
