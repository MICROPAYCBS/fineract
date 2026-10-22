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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.domain.TreasuryJournalEntryRepository;
import org.apache.fineract.treasury.domain.TreasuryLineMatchStatus;
import org.apache.fineract.treasury.domain.TreasuryReconMatch;
import org.apache.fineract.treasury.domain.TreasuryReconMatchItemRepository;
import org.apache.fineract.treasury.domain.TreasuryReconMatchRepository;
import org.apache.fineract.treasury.domain.TreasuryStatement;
import org.apache.fineract.treasury.domain.TreasuryStatementFormat;
import org.apache.fineract.treasury.domain.TreasuryStatementFormatRepository;
import org.apache.fineract.treasury.domain.TreasuryStatementFormatType;
import org.apache.fineract.treasury.domain.TreasuryStatementLine;
import org.apache.fineract.treasury.domain.TreasuryStatementLineRepository;
import org.apache.fineract.treasury.domain.TreasuryStatementRepository;
import org.apache.fineract.treasury.domain.TreasuryStatementStatus;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryStatementDataValidator;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;
import org.apache.fineract.treasury.statement.StatementParseException;
import org.apache.fineract.treasury.statement.TreasuryAutoMatcher.EntryCandidate;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TreasuryReconciliationWriteServiceTest {

    private static final long IMPORTER = 5L;
    private static final long CHECKER = 6L;
    private static final long MAKER = 7L;

    private final TreasuryActor actor = mock(TreasuryActor.class);
    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final TreasuryAccountRepository accountRepository = mock(TreasuryAccountRepository.class);
    private final TreasuryStatementFormatRepository formatRepository = mock(TreasuryStatementFormatRepository.class);
    private final TreasuryStatementRepository statementRepository = mock(TreasuryStatementRepository.class);
    private final TreasuryStatementLineRepository lineRepository = mock(TreasuryStatementLineRepository.class);
    private final TreasuryReconMatchRepository matchRepository = mock(TreasuryReconMatchRepository.class);
    private final TreasuryReconciliationReadService readService = mock(TreasuryReconciliationReadService.class);
    private final TreasuryJournalPoster journalPoster = mock(TreasuryJournalPoster.class);
    private final TreasuryReconciliationWriteService service = new TreasuryReconciliationWriteService(this.actor, this.context,
            new TreasuryStatementDataValidator(new FromJsonHelper()), this.accountRepository, this.formatRepository,
            this.statementRepository, this.lineRepository, this.matchRepository, mock(TreasuryReconMatchItemRepository.class),
            this.readService, mock(TreasurySettingService.class), mock(TreasuryReferenceDataLookup.class), this.journalPoster,
            mock(TreasuryJournalEntryRepository.class), mock(JdbcTemplate.class));
    private final TreasuryAccount account = new TreasuryAccount();

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", mock(FineractPlatformTenantConnection.class)));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 10, 16))));
        this.account.setId(3L);
        this.account.setCode("STANBIC");
        this.account.setGlAccountId(30L);
        this.account.setOfficeId(1L);
        this.account.setCurrencyCode("UGX");
        this.account.setStatus(TreasuryAccountStatus.ACTIVE);
        when(this.accountRepository.findById(3L)).thenReturn(Optional.of(this.account));
        final TreasuryStatementFormat mt940 = new TreasuryStatementFormat();
        mt940.setId(1L);
        mt940.setCode("MT940");
        mt940.setFormatType(TreasuryStatementFormatType.MT940);
        mt940.setActive(true);
        lenient().when(this.formatRepository.findByCode("MT940")).thenReturn(Optional.of(mt940));
        lenient().when(this.actor.userId(any())).thenReturn(MAKER);
        lenient().when(this.matchRepository.saveAndFlush(any())).thenAnswer(call -> {
            final TreasuryReconMatch match = call.getArgument(0);
            match.setId(40L);
            return match;
        });
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void sameFileTwiceIsRefused() {
        when(this.statementRepository.existsByTreasuryAccountIdAndFileHash(eq(3L), anyString())).thenReturn(true);

        assertCode("statement.duplicate.file", () -> this.service.importStatement(importCommand("sample.sta")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void linesAlreadyImportedAreRefused() {
        when(this.lineRepository.findExistingKeys(eq(3L), anyCollection())).thenAnswer(call -> List.of(
                ((java.util.Collection<String>) call.getArgument(1)).iterator().next()));

        final TreasuryWorkflowException e = assertThrows(TreasuryWorkflowException.class,
                () -> this.service.importStatement(importCommand("sample.sta")));
        assertEquals("error.msg.treasury.statement.duplicate.lines", e.getGlobalisationMessageCode());
        verify(this.statementRepository, never()).saveAndFlush(any());
    }

    @Test
    void statementInAnotherCurrencyIsRefused() {
        this.account.setCurrencyCode("USD");

        assertCode("statement.currency.mismatch", () -> this.service.importStatement(importCommand("sample.sta")));
    }

    @Test
    void statementThatDoesNotAddUpIsRefused() {
        assertThrows(StatementParseException.class, () -> this.service.importStatement(importCommand("bad_closing.sta")));
        verify(this.statementRepository, never()).saveAndFlush(any());
    }

    @Test
    void goodStatementIsStoredUnmatched() {
        when(this.lineRepository.findExistingKeys(eq(3L), anyCollection())).thenReturn(List.of());
        when(this.statementRepository.saveAndFlush(any())).thenAnswer(call -> {
            final TreasuryStatement statement = call.getArgument(0);
            statement.setId(9L);
            return statement;
        });

        assertEquals(9L, this.service.importStatement(importCommand("sample.sta")).getResourceId());
        verify(this.statementRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(s -> s.getImportedBy() == MAKER
                && s.getStatus() == TreasuryStatementStatus.IMPORTED && s.getLineCount() == 3
                && s.getClosingBalance().compareTo(new BigDecimal("6244500")) == 0));
    }

    @Test
    void manualMatchNeedsEqualTotals() {
        lines(line(1L, "100"), line(2L, "50"));
        when(this.readService.matchableEntries(eq(this.account), any())).thenReturn(List.of(entry(10L, "149")));

        assertCode("reconciliation.totals.differ", () -> this.service.createMatch(matchCommand(null)));
    }

    @Test
    void manualMatchOfSeveralLinesToOneEntry() {
        lines(line(1L, "100"), line(2L, "50"));
        when(this.readService.matchableEntries(eq(this.account), any())).thenReturn(List.of(entry(10L, "150")));
        statementImportedBy(IMPORTER);

        assertEquals(40L, this.service.createMatch(matchCommand(null)).getResourceId());
    }

    @Test
    void entryNotMatchableIsRefused() {
        lines(line(1L, "100"), line(2L, "50"));
        when(this.readService.matchableEntries(eq(this.account), any())).thenReturn(List.of());

        assertCode("reconciliation.entry.not.matchable", () -> this.service.createMatch(matchCommand(null)));
    }

    @Test
    void importerCannotApproveAHeldMatch() {
        lines(line(1L, "100"), line(2L, "50"));
        when(this.readService.matchableEntries(eq(this.account), any())).thenReturn(List.of(entry(10L, "150")));
        statementImportedBy(CHECKER);
        final AppUser checker = mock(AppUser.class);
        when(checker.getId()).thenReturn(CHECKER);
        when(this.context.authenticatedUser()).thenReturn(checker);

        assertCode("reconciliation.importer.cannot.approve", () -> this.service.createMatch(matchCommand(1762L)));
    }

    @Test
    void anotherCheckerMayApproveAHeldMatch() {
        lines(line(1L, "100"), line(2L, "50"));
        when(this.readService.matchableEntries(eq(this.account), any())).thenReturn(List.of(entry(10L, "150")));
        statementImportedBy(IMPORTER);
        final AppUser checker = mock(AppUser.class);
        when(checker.getId()).thenReturn(CHECKER);
        when(this.context.authenticatedUser()).thenReturn(checker);

        assertEquals(40L, this.service.createMatch(matchCommand(1762L)).getResourceId());
    }

    @Test
    void matchedLineCannotBeMatchedAgain() {
        final TreasuryStatementLine matched = line(1L, "100");
        matched.setMatchStatus(TreasuryLineMatchStatus.MATCHED);
        lines(matched, line(2L, "50"));

        assertCode("reconciliation.line.already.matched", () -> this.service.createMatch(matchCommand(null)));
    }

    @Test
    void moneyInIsBookedAsADebitOfTheTreasuryAccount() {
        assertEquals(
                List.of(new Leg(1L, 30L, JournalEntryType.DEBIT, new BigDecimal("1200")),
                        new Leg(1L, 77L, JournalEntryType.CREDIT, new BigDecimal("1200"))),
                TreasuryReconciliationWriteService.adjustmentLegs(this.account, 77L, new BigDecimal("1200")));
        assertEquals(
                List.of(new Leg(1L, 30L, JournalEntryType.CREDIT, new BigDecimal("5500")),
                        new Leg(1L, 77L, JournalEntryType.DEBIT, new BigDecimal("5500"))),
                TreasuryReconciliationWriteService.adjustmentLegs(this.account, 77L, new BigDecimal("-5500")));
    }

    @Test
    void unmatchNeedsAReason() {
        assertThrows(org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException.class,
                () -> this.service.unmatch(40L, JsonCommand.from("{}")));
        verify(this.journalPoster, never()).reverse(any(), anyLong(), any(), any(), any());
    }

    private void lines(final TreasuryStatementLine... lines) {
        when(this.lineRepository.findAllById(any())).thenReturn(List.of(lines));
    }

    private void statementImportedBy(final long userId) {
        final TreasuryStatement statement = new TreasuryStatement();
        statement.setId(9L);
        statement.setImportedBy(userId);
        statement.setStatus(TreasuryStatementStatus.IMPORTED);
        when(this.statementRepository.findById(9L)).thenReturn(Optional.of(statement));
    }

    private static TreasuryStatementLine line(final Long id, final String amount) {
        final TreasuryStatementLine line = new TreasuryStatementLine();
        line.setId(id);
        line.setStatementId(9L);
        line.setTreasuryAccountId(3L);
        line.setAmount(new BigDecimal(amount));
        line.setMatchStatus(TreasuryLineMatchStatus.UNMATCHED);
        return line;
    }

    private static EntryCandidate entry(final Long id, final String amount) {
        return new EntryCandidate(id, LocalDate.of(2026, 10, 15), new BigDecimal(amount), "TX" + id, null);
    }

    private static JsonCommand matchCommand(final Long commandId) {
        final String json = "{\"lineIds\": [1, 2], \"journalEntryIds\": [10]}";
        return commandId == null ? JsonCommand.from(json)
                : JsonCommand.fromExistingCommand(commandId, json, JsonParser.parseString(json), new FromJsonHelper(), "TREASURYRECONMATCH",
                        null, null, null, null, null, null, null, null);
    }

    private static JsonCommand importCommand(final String file) {
        try (InputStream in = TreasuryReconciliationWriteServiceTest.class.getResourceAsStream("/statements/" + file + ".txt")) {
            return JsonCommand.from("{\"treasuryAccountId\": 3, \"formatCode\": \"mt940\", \"fileName\": \"" + file + "\", \"content\": \""
                    + Base64.getEncoder().encodeToString(in.readAllBytes()) + "\"}");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertCode(final String suffix, final org.junit.jupiter.api.function.Executable call) {
        assertEquals("error.msg.treasury." + suffix, assertThrows(TreasuryWorkflowException.class, call).getGlobalisationMessageCode());
    }
}
