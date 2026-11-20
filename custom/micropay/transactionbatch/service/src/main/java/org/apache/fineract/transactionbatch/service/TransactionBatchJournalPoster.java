/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.closure.domain.GLClosure;
import org.apache.fineract.accounting.closure.domain.GLClosureRepository;
import org.apache.fineract.accounting.financialactivityaccount.exception.FinancialActivityAccountNotFoundException;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.accounting.journalentry.exception.JournalEntryInvalidException;
import org.apache.fineract.accounting.journalentry.exception.JournalEntryInvalidException.GlJournalEntryInvalidReason;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.interbranch.service.InterBranchGlAccountReadService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.transactionbatch.exception.TransactionBatchRuleException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * System journal entries for batch lines. A line in the batch's branch is a balanced pair there; a line in another branch
 * is bridged through the inter-branch clearing account ({@link #postAcross}). Transaction ids are TB-, TBS- and TBR-.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchJournalPoster {

    private final OfficeRepositoryWrapper officeRepository;
    private final GLAccountRepositoryWrapper glAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final GLClosureRepository glClosureRepository;
    private final InterBranchGlAccountReadService interBranchGlAccountReadService;

    public static String transactionId(final String prefix, final Long batchId, final Integer runNo, final Long itemId) {
        return prefix + "-" + batchId + "-" + runNo + "-" + itemId;
    }

    public String postPair(final String transactionId, final Long officeId, final String currencyCode, final LocalDate date,
            final String description, final String reference, final Long debitGlAccountId, final Long creditGlAccountId,
            final BigDecimal amount) {
        requireOpen(officeId, date);
        final Office office = this.officeRepository.findOneWithNotFoundDetection(officeId);
        save(office, currencyCode, transactionId, date, description, reference, debitGlAccountId, JournalEntryType.DEBIT, amount);
        save(office, currencyCode, transactionId, date, description, reference, creditGlAccountId, JournalEntryType.CREDIT, amount);
        return transactionId;
    }

    /**
     * Posts one line whose own account ({@code lineGlAccountId}) is in {@code lineOfficeId} against a batch-side account
     * ({@code batchGlAccountId}: clearing, or suspense on repair) in {@code batchOfficeId}. In the same branch this is
     * one pair; otherwise each branch gets a pair against the inter-branch clearing account, so the batch-side account
     * stays in the batch's branch and both branches balance. All entries share {@code transactionId}.
     */
    public String postAcross(final String transactionId, final String currencyCode, final LocalDate date, final String description,
            final String reference, final Long batchOfficeId, final Long batchGlAccountId, final Long lineOfficeId,
            final Long lineGlAccountId, final boolean lineIsDebit, final BigDecimal amount) {
        if (lineOfficeId == null || lineOfficeId.equals(batchOfficeId)) {
            return lineIsDebit
                    ? postPair(transactionId, batchOfficeId, currencyCode, date, description, reference, lineGlAccountId, batchGlAccountId,
                            amount)
                    : postPair(transactionId, batchOfficeId, currencyCode, date, description, reference, batchGlAccountId, lineGlAccountId,
                            amount);
        }
        requireOpen(batchOfficeId, date);
        requireOpen(lineOfficeId, date);
        final Long bridge = interBranchClearingAccountId(batchOfficeId, lineOfficeId, currencyCode);
        final Office batchOffice = this.officeRepository.findOneWithNotFoundDetection(batchOfficeId);
        final Office lineOffice = this.officeRepository.findOneWithNotFoundDetection(lineOfficeId);
        final JournalEntryType lineSide = lineIsDebit ? JournalEntryType.DEBIT : JournalEntryType.CREDIT;
        final JournalEntryType otherSide = lineIsDebit ? JournalEntryType.CREDIT : JournalEntryType.DEBIT;
        save(lineOffice, currencyCode, transactionId, date, description, reference, lineGlAccountId, lineSide, amount);
        save(lineOffice, currencyCode, transactionId, date, description, reference, bridge, otherSide, amount);
        save(batchOffice, currencyCode, transactionId, date, description, reference, bridge, lineSide, amount);
        save(batchOffice, currencyCode, transactionId, date, description, reference, batchGlAccountId, otherSide, amount);
        return transactionId;
    }

    /** Inter-branch clearing account for the batch's branch (servicing) and the line's branch (home). */
    public Long interBranchClearingAccountId(final Long batchOfficeId, final Long lineOfficeId, final String currencyCode) {
        try {
            return this.interBranchGlAccountReadService.resolveClearingAccount(batchOfficeId, lineOfficeId, currencyCode).getId();
        } catch (final FinancialActivityAccountNotFoundException ex) {
            throw new TransactionBatchRuleException("interbranch.clearing.missing",
                    "No inter-branch clearing account is set up for " + currencyCode
                            + ": add an inter-branch rule for these branches or map the inter-branch reconciliation financial activity",
                    batchOfficeId, lineOfficeId, currencyCode);
        }
    }

    private void requireOpen(final Long officeId, final LocalDate date) {
        if (isClosed(officeId, date)) {
            final GLClosure closure = this.glClosureRepository.getLatestGLClosureByBranch(officeId);
            throw new JournalEntryInvalidException(GlJournalEntryInvalidReason.ACCOUNTING_CLOSED,
                    closure == null ? null : closure.getClosingDate(), null, null);
        }
        if (DateUtils.isAfterBusinessDate(date)) {
            throw new JournalEntryInvalidException(GlJournalEntryInvalidReason.FUTURE_DATE, date, null, null);
        }
    }

    public boolean isClosed(final Long officeId, final LocalDate date) {
        if (officeId == null || date == null) {
            return false;
        }
        final GLClosure closure = this.glClosureRepository.getLatestGLClosureByBranch(officeId);
        return closure != null && !DateUtils.isBefore(closure.getClosingDate(), date);
    }

    private void save(final Office office, final String currencyCode, final String transactionId, final LocalDate date,
            final String description, final String reference, final Long glAccountId, final JournalEntryType type, final BigDecimal amount) {
        final GLAccount glAccount = this.glAccountRepository.findOneWithNotFoundDetection(glAccountId);
        this.journalEntryRepository.save(JournalEntry.createNew(office, null, glAccount, currencyCode, transactionId, false, date, type,
                amount, description, null, null, reference, null, null, null, null));
    }
}
