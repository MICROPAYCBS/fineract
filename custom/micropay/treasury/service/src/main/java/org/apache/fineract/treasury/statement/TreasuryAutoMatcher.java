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
package org.apache.fineract.treasury.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;

/**
 * One-to-one automatic matching of statement lines to GL journal entries. Both sides are signed the same way: a
 * statement line is positive for money into the account, and a journal entry on the account's asset GL account is
 * positive for a debit, so a bank credit matches our debit of the same amount.
 *
 * <p>
 * Each line is tried against the rules in order, and a rule matches only when exactly one unused entry passes it; an
 * entry is never used twice. Lines are taken in statement order.
 * </p>
 */
public final class TreasuryAutoMatcher {

    /** Shorter transaction ids or reference numbers are too likely to appear by chance in a statement text. */
    static final int MIN_REFERENCE_LENGTH = 4;

    private TreasuryAutoMatcher() {}

    public enum Rule {
        /** Same amount and the statement reference or description contains the entry's transaction id or reference. */
        REFERENCE,
        /** Same amount and the same date (the line's date or value date). */
        SAME_DATE,
        /** Same amount within the date window. */
        DATE_WINDOW
    }

    public record Rules(boolean reference, boolean sameDate, boolean dateWindow, int windowDays) {

        public static final Rules DEFAULT = new Rules(true, true, true, 3);
    }

    public record LineCandidate(Long id, LocalDate date, LocalDate valueDate, BigDecimal amount, String reference, String description) {
    }

    public record EntryCandidate(Long id, LocalDate date, BigDecimal amount, String transactionId, String referenceNumber) {
    }

    public record Match(Long lineId, Long entryId, Rule rule) {
    }

    public static List<Match> match(final List<LineCandidate> lines, final List<EntryCandidate> entries, final Rules rules) {
        final Set<Long> used = new LinkedHashSet<>();
        final List<Match> matches = new ArrayList<>();
        for (final LineCandidate line : lines) {
            final List<EntryCandidate> sameAmount = entries.stream()
                    .filter(entry -> !used.contains(entry.id()) && entry.amount().compareTo(line.amount()) == 0).toList();
            if (sameAmount.isEmpty()) {
                continue;
            }
            Match match = null;
            if (rules.reference()) {
                match = only(line, sameAmount, entry -> mentions(line, entry), Rule.REFERENCE);
            }
            if (match == null && rules.sameDate()) {
                match = only(line, sameAmount,
                        entry -> entry.date().equals(line.date()) || (line.valueDate() != null && entry.date().equals(line.valueDate())),
                        Rule.SAME_DATE);
            }
            if (match == null && rules.dateWindow()) {
                match = only(line, sameAmount, entry -> Math.abs(ChronoUnit.DAYS.between(line.date(), entry.date())) <= rules.windowDays(),
                        Rule.DATE_WINDOW);
            }
            if (match != null) {
                used.add(match.entryId());
                matches.add(match);
            }
        }
        return matches;
    }

    private static Match only(final LineCandidate line, final List<EntryCandidate> candidates, final Predicate<EntryCandidate> test,
            final Rule rule) {
        final List<EntryCandidate> passing = candidates.stream().filter(test).toList();
        return passing.size() == 1 ? new Match(line.id(), passing.getFirst().id(), rule) : null;
    }

    static boolean mentions(final LineCandidate line, final EntryCandidate entry) {
        final String text = (Objects.toString(line.reference(), "") + " " + Objects.toString(line.description(), ""))
                .toUpperCase(Locale.ROOT);
        return contains(text, entry.transactionId()) || contains(text, entry.referenceNumber());
    }

    private static boolean contains(final String text, final String token) {
        return StringUtils.length(StringUtils.trim(token)) >= MIN_REFERENCE_LENGTH && text.contains(token.trim().toUpperCase(Locale.ROOT));
    }
}
