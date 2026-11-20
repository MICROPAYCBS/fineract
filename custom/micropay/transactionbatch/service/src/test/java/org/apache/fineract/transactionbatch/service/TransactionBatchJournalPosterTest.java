/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.accounting.closure.domain.GLClosureRepository;
import org.apache.fineract.accounting.financialactivityaccount.exception.FinancialActivityAccountNotFoundException;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.interbranch.service.InterBranchGlAccountReadService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.transactionbatch.exception.TransactionBatchRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionBatchJournalPosterTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);
    private static final Long BATCH_OFFICE = 1L;
    private static final Long LINE_OFFICE = 2L;
    private static final Long LINE_GL = 100L;
    private static final Long CLEARING_GL = 200L;
    private static final Long BRIDGE_GL = 300L;

    private final List<JournalEntry> saved = new ArrayList<>();
    private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
    private final InterBranchGlAccountReadService interBranch = mock(InterBranchGlAccountReadService.class);
    private TransactionBatchJournalPoster poster;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, DAY)));
        final OfficeRepositoryWrapper offices = mock(OfficeRepositoryWrapper.class);
        when(offices.findOneWithNotFoundDetection(anyLong())).thenAnswer(call -> office(call.getArgument(0)));
        final GLAccountRepositoryWrapper glAccounts = mock(GLAccountRepositoryWrapper.class);
        when(glAccounts.findOneWithNotFoundDetection(anyLong())).thenAnswer(call -> gl(call.getArgument(0)));
        when(this.journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(call -> {
            this.saved.add(call.getArgument(0));
            return call.getArgument(0);
        });
        final GLAccount bridge = gl(BRIDGE_GL);
        when(this.interBranch.resolveClearingAccount(anyLong(), anyLong(), anyString())).thenReturn(bridge);
        this.poster = new TransactionBatchJournalPoster(offices, glAccounts, this.journalEntryRepository, mock(GLClosureRepository.class),
                this.interBranch);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void sameBranchPostsOnePair() {
        this.poster.postAcross("TB-1-1-1", "UGX", DAY, "Rent", "R1", BATCH_OFFICE, CLEARING_GL, BATCH_OFFICE, LINE_GL, true,
                new BigDecimal("500"));
        assertEquals(2, this.saved.size());
        assertEntry(0, BATCH_OFFICE, LINE_GL, true);
        assertEntry(1, BATCH_OFFICE, CLEARING_GL, false);
    }

    @Test
    void debitLineInAnotherBranchIsBridgedAndBalancesPerBranch() {
        this.poster.postAcross("TB-1-1-1", "UGX", DAY, "Rent", "R1", BATCH_OFFICE, CLEARING_GL, LINE_OFFICE, LINE_GL, true,
                new BigDecimal("500"));
        assertEquals(4, this.saved.size());
        assertEntry(0, LINE_OFFICE, LINE_GL, true);
        assertEntry(1, LINE_OFFICE, BRIDGE_GL, false);
        assertEntry(2, BATCH_OFFICE, BRIDGE_GL, true);
        assertEntry(3, BATCH_OFFICE, CLEARING_GL, false);
        // Servicing is the batch's branch, home is the line's branch.
        verify(this.interBranch).resolveClearingAccount(BATCH_OFFICE, LINE_OFFICE, "UGX");
    }

    @Test
    void creditLineInAnotherBranchIsBridged() {
        this.poster.postAcross("TB-1-1-1", "UGX", DAY, "Rent", "R1", BATCH_OFFICE, CLEARING_GL, LINE_OFFICE, LINE_GL, false,
                new BigDecimal("500"));
        assertEntry(0, LINE_OFFICE, LINE_GL, false);
        assertEntry(1, LINE_OFFICE, BRIDGE_GL, true);
        assertEntry(2, BATCH_OFFICE, BRIDGE_GL, false);
        assertEntry(3, BATCH_OFFICE, CLEARING_GL, true);
    }

    @Test
    void missingInterBranchClearingAccountIsARuleError() {
        when(this.interBranch.resolveClearingAccount(anyLong(), anyLong(), anyString()))
                .thenThrow(new FinancialActivityAccountNotFoundException(200));
        assertThrows(TransactionBatchRuleException.class, () -> this.poster.postAcross("TB-1-1-1", "UGX", DAY, "Rent", "R1",
                BATCH_OFFICE, CLEARING_GL, LINE_OFFICE, LINE_GL, true, new BigDecimal("500")));
        assertEquals(0, this.saved.size());
    }

    private void assertEntry(final int index, final Long officeId, final Long glAccountId, final boolean debit) {
        final JournalEntry entry = this.saved.get(index);
        assertEquals(officeId, entry.getOffice().getId(), "office of entry " + index);
        assertEquals(glAccountId, entry.getGlAccount().getId(), "account of entry " + index);
        assertEquals(debit, entry.isDebitEntry(), "side of entry " + index);
        assertEquals("TB-1-1-1", entry.getTransactionId());
    }

    private static Office office(final Long id) {
        final Office office = mock(Office.class);
        when(office.getId()).thenReturn(id);
        return office;
    }

    private static GLAccount gl(final Long id) {
        final GLAccount account = mock(GLAccount.class);
        when(account.getId()).thenReturn(id);
        return account;
    }
}
