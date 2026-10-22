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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.treasury.statement.TreasuryAutoMatcher.EntryCandidate;
import org.apache.fineract.treasury.statement.TreasuryAutoMatcher.LineCandidate;
import org.apache.fineract.treasury.statement.TreasuryAutoMatcher.Match;
import org.apache.fineract.treasury.statement.TreasuryAutoMatcher.Rule;
import org.apache.fineract.treasury.statement.TreasuryAutoMatcher.Rules;
import org.junit.jupiter.api.Test;

class TreasuryAutoMatcherTest {

    private static final LocalDate D15 = LocalDate.of(2026, 10, 15);

    @Test
    void bankCreditMatchesOurDebitByReference() {
        final List<Match> matches = TreasuryAutoMatcher.match(List.of(line(1L, D15, "100", "TR-TFR-12", null)),
                List.of(entry(10L, D15.plusDays(2), "100", "TR-TFR-12", null), entry(11L, D15, "100", "OTHER-1", null)), Rules.DEFAULT);

        assertEquals(List.of(new Match(1L, 10L, Rule.REFERENCE)), matches);
    }

    @Test
    void referenceNumberInTheDescriptionAlsoMatches() {
        final List<Match> matches = TreasuryAutoMatcher.match(List.of(line(1L, D15, "-5500", null, "Cheque 004512 paid")),
                List.of(entry(10L, D15, "-5500", "a30aff50b5a7", "004512"), entry(11L, D15, "-5500", "b30aff50b5a8", null)), Rules.DEFAULT);

        assertEquals(List.of(new Match(1L, 10L, Rule.REFERENCE)), matches);
    }

    @Test
    void shortReferencesDoNotCount() {
        assertTrue(!TreasuryAutoMatcher.mentions(line(1L, D15, "1", "Paid 12 items", null), entry(1L, D15, "1", "12", null)));
    }

    @Test
    void signsMustAgree() {
        assertTrue(TreasuryAutoMatcher
                .match(List.of(line(1L, D15, "100", null, null)), List.of(entry(10L, D15, "-100", null, null)), Rules.DEFAULT).isEmpty());
    }

    @Test
    void sameDateWhenNoReference() {
        assertEquals(List.of(new Match(1L, 11L, Rule.SAME_DATE)), TreasuryAutoMatcher.match(List.of(line(1L, D15, "100", null, null)),
                List.of(entry(10L, D15.minusDays(1), "100", null, null), entry(11L, D15, "100", null, null)), Rules.DEFAULT));
    }

    @Test
    void valueDateCountsAsTheSameDate() {
        final LineCandidate line = new LineCandidate(1L, D15, D15.plusDays(1), new BigDecimal("100"), null, null);

        assertEquals(List.of(new Match(1L, 10L, Rule.SAME_DATE)), TreasuryAutoMatcher.match(List.of(line),
                List.of(entry(10L, D15.plusDays(1), "100", null, null)), new Rules(false, true, false, 3)));
    }

    @Test
    void windowNeedsExactlyOneCandidate() {
        final List<LineCandidate> lines = List.of(line(1L, D15, "100", null, null));

        assertEquals(List.of(new Match(1L, 10L, Rule.DATE_WINDOW)),
                TreasuryAutoMatcher.match(lines, List.of(entry(10L, D15.plusDays(3), "100", null, null)), Rules.DEFAULT));
        assertTrue(TreasuryAutoMatcher.match(lines,
                List.of(entry(10L, D15.plusDays(2), "100", null, null), entry(11L, D15.minusDays(2), "100", null, null)), Rules.DEFAULT)
                .isEmpty(), "two candidates: left for a person");
        assertTrue(TreasuryAutoMatcher.match(lines, List.of(entry(10L, D15.plusDays(4), "100", null, null)), Rules.DEFAULT).isEmpty(),
                "outside the window");
    }

    @Test
    void ambiguousSameDateIsLeftAlone() {
        assertTrue(TreasuryAutoMatcher.match(List.of(line(1L, D15, "100", null, null)),
                List.of(entry(10L, D15, "100", null, null), entry(11L, D15, "100", null, null)), Rules.DEFAULT).isEmpty());
    }

    @Test
    void anEntryIsUsedOnce() {
        final List<Match> matches = TreasuryAutoMatcher.match(List.of(line(1L, D15, "100", null, null), line(2L, D15, "100", null, null)),
                List.of(entry(10L, D15, "100", null, null)), Rules.DEFAULT);

        assertEquals(List.of(new Match(1L, 10L, Rule.SAME_DATE)), matches);
    }

    @Test
    void rulesCanBeSwitchedOff() {
        final List<LineCandidate> lines = List.of(line(1L, D15, "100", "TR-TFR-12", null));
        final List<EntryCandidate> entries = List.of(entry(10L, D15.plusDays(1), "100", "TR-TFR-12", null));

        assertEquals(Rule.DATE_WINDOW, TreasuryAutoMatcher.match(lines, entries, new Rules(false, true, true, 3)).getFirst().rule());
        assertTrue(TreasuryAutoMatcher.match(lines, entries, new Rules(false, true, false, 3)).isEmpty());
    }

    private static LineCandidate line(final Long id, final LocalDate date, final String amount, final String reference,
            final String description) {
        return new LineCandidate(id, date, date, new BigDecimal(amount), reference, description);
    }

    private static EntryCandidate entry(final Long id, final LocalDate date, final String amount, final String transactionId,
            final String referenceNumber) {
        return new EntryCandidate(id, date, new BigDecimal(amount), transactionId, referenceNumber);
    }
}
