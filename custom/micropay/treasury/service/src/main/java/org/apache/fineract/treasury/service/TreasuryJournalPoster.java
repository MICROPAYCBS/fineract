/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.treasury.service;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.TRANSACTION_ID_PREFIX;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.closure.domain.GLClosure;
import org.apache.fineract.accounting.closure.domain.GLClosureRepository;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.accounting.journalentry.exception.JournalEntryInvalidException;
import org.apache.fineract.accounting.journalentry.exception.JournalEntryInvalidException.GlJournalEntryInvalidReason;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.treasury.domain.TreasuryEventType;
import org.apache.fineract.treasury.domain.TreasuryPosting;
import org.apache.fineract.treasury.domain.TreasuryPostingRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;
import org.springframework.stereotype.Component;

/**
 * Posts treasury events as upstream journal entries (system entries, no entity type) under a transaction id
 * {@code TR-<event>-<id>}, and records the link in {@code m_treasury_posting}. Upstream GL closures are respected and
 * nothing is dated after the business date.
 */
@Component
@RequiredArgsConstructor
public class TreasuryJournalPoster {

    private final OfficeRepositoryWrapper officeRepository;
    private final GLAccountRepositoryWrapper glAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final GLClosureRepository glClosureRepository;
    private final TreasuryPostingRepository postingRepository;

    public static String transactionId(final TreasuryEventType eventType, final Long eventId) {
        return TRANSACTION_ID_PREFIX + "-" + eventType.code() + "-" + eventId;
    }

    /**
     * @return the journal transaction id
     */
    public String post(final TreasuryEventType eventType, final Long eventId, final String currencyCode, final LocalDate transactionDate,
            final String description, final String reference, final List<Leg> legs) {
        if (!TreasuryPostingPlanner.isBalancedPerOffice(legs)) {
            throw new IllegalStateException("Treasury posting " + eventType + " " + eventId + " does not balance per office: "
                    + TreasuryPostingPlanner.netPerOffice(legs));
        }
        validateDate(transactionDate, legs);
        final String transactionId = transactionId(eventType, eventId);
        for (final Leg leg : legs) {
            final Office office = this.officeRepository.findOneWithNotFoundDetection(leg.officeId());
            final GLAccount glAccount = this.glAccountRepository.findOneWithNotFoundDetection(leg.glAccountId());
            if (glAccount.isHeaderAccount() || glAccount.isDisabled()) {
                throw new TreasuryWorkflowException("posting", "gl.account.not.postable",
                        "GL account " + glAccount.getGlCode() + " is a header or disabled account and cannot be posted to",
                        glAccount.getId());
            }
            this.journalEntryRepository.save(JournalEntry.createNew(office, null, glAccount, currencyCode, transactionId, false,
                    transactionDate, leg.type(), leg.amount(), description, null, null, reference, null, null, null, null));
        }
        final TreasuryPosting posting = new TreasuryPosting();
        posting.setEventType(eventType);
        posting.setEventId(eventId);
        posting.setTransactionId(transactionId);
        posting.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.postingRepository.save(posting);
        return transactionId;
    }

    private void validateDate(final LocalDate transactionDate, final List<Leg> legs) {
        if (DateUtils.isAfterBusinessDate(transactionDate)) {
            throw new TreasuryWorkflowException("posting", "future.date",
                    "Treasury postings cannot be dated after the business date " + DateUtils.getBusinessLocalDate(), transactionDate);
        }
        final Set<Long> officeIds = new LinkedHashSet<>();
        legs.forEach(leg -> officeIds.add(leg.officeId()));
        for (final Long officeId : officeIds) {
            final GLClosure closure = this.glClosureRepository.getLatestGLClosureByBranch(officeId);
            if (closure != null && !DateUtils.isBefore(closure.getClosingDate(), transactionDate)) {
                throw new JournalEntryInvalidException(GlJournalEntryInvalidReason.ACCOUNTING_CLOSED, closure.getClosingDate(), null, null);
            }
        }
    }
}
