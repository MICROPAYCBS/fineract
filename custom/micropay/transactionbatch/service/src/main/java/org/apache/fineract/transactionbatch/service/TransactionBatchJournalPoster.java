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
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.accounting.journalentry.exception.JournalEntryInvalidException;
import org.apache.fineract.accounting.journalentry.exception.JournalEntryInvalidException.GlJournalEntryInvalidReason;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Balanced pair of system journal entries in the batch origin branch. Transaction ids are TB-, TBS- and TBR-. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchJournalPoster {

    private final OfficeRepositoryWrapper officeRepository;
    private final GLAccountRepositoryWrapper glAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final GLClosureRepository glClosureRepository;

    public static String transactionId(final String prefix, final Long batchId, final Integer runNo, final Long itemId) {
        return prefix + "-" + batchId + "-" + runNo + "-" + itemId;
    }

    public String postPair(final String transactionId, final Long officeId, final String currencyCode, final LocalDate date,
            final String description, final String reference, final Long debitGlAccountId, final Long creditGlAccountId,
            final BigDecimal amount) {
        if (isClosed(officeId, date)) {
            final GLClosure closure = this.glClosureRepository.getLatestGLClosureByBranch(officeId);
            throw new JournalEntryInvalidException(GlJournalEntryInvalidReason.ACCOUNTING_CLOSED,
                    closure == null ? null : closure.getClosingDate(), null, null);
        }
        if (DateUtils.isAfterBusinessDate(date)) {
            throw new JournalEntryInvalidException(GlJournalEntryInvalidReason.FUTURE_DATE, date, null, null);
        }
        final Office office = this.officeRepository.findOneWithNotFoundDetection(officeId);
        save(office, currencyCode, transactionId, date, description, reference, debitGlAccountId, JournalEntryType.DEBIT, amount);
        save(office, currencyCode, transactionId, date, description, reference, creditGlAccountId, JournalEntryType.CREDIT, amount);
        return transactionId;
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
