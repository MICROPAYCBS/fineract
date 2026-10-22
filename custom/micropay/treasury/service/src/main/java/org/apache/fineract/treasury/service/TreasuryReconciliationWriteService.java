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

import static org.apache.fineract.treasury.api.TreasuryApiConstants.ACTIVE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.AMOUNT_COLUMN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.BALANCE_COLUMN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.CREDIT_COLUMN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.DATE_COLUMN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.DATE_FORMAT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.DEBIT_COLUMN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.DECIMAL_SEPARATOR_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.DELIMITER_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.DESCRIPTION_COLUMN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.HEADER_ROWS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.INVERT_AMOUNT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.RECON_EXPLANATION_CODE;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.REFERENCE_COLUMN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.VALUE_DATE_COLUMN_PARAM;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryEventType;
import org.apache.fineract.treasury.domain.TreasuryJournalEntryRepository;
import org.apache.fineract.treasury.domain.TreasuryLineMatchStatus;
import org.apache.fineract.treasury.domain.TreasuryReconMatch;
import org.apache.fineract.treasury.domain.TreasuryReconMatchItem;
import org.apache.fineract.treasury.domain.TreasuryReconMatchItemRepository;
import org.apache.fineract.treasury.domain.TreasuryReconMatchRepository;
import org.apache.fineract.treasury.domain.TreasuryReconMatchRule;
import org.apache.fineract.treasury.domain.TreasuryReconMatchStatus;
import org.apache.fineract.treasury.domain.TreasurySetting;
import org.apache.fineract.treasury.domain.TreasuryStatement;
import org.apache.fineract.treasury.domain.TreasuryStatementFormat;
import org.apache.fineract.treasury.domain.TreasuryStatementFormatRepository;
import org.apache.fineract.treasury.domain.TreasuryStatementFormatType;
import org.apache.fineract.treasury.domain.TreasuryStatementLine;
import org.apache.fineract.treasury.domain.TreasuryStatementLineRepository;
import org.apache.fineract.treasury.domain.TreasuryStatementRepository;
import org.apache.fineract.treasury.domain.TreasuryStatementStatus;
import org.apache.fineract.treasury.exception.TreasuryAccountNotFoundException;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryStatementDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryStatementDataValidator.ExplainRequest;
import org.apache.fineract.treasury.serialization.TreasuryStatementDataValidator.FormatRequest;
import org.apache.fineract.treasury.serialization.TreasuryStatementDataValidator.ImportRequest;
import org.apache.fineract.treasury.serialization.TreasuryStatementDataValidator.MatchRequest;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;
import org.apache.fineract.treasury.service.TreasuryReferenceDataLookup.GlAccountInfo;
import org.apache.fineract.treasury.statement.CsvStatementParser;
import org.apache.fineract.treasury.statement.Mt940StatementParser;
import org.apache.fineract.treasury.statement.ParsedStatement;
import org.apache.fineract.treasury.statement.StatementBalanceCheck;
import org.apache.fineract.treasury.statement.StatementFiles;
import org.apache.fineract.treasury.statement.TreasuryAutoMatcher;
import org.apache.fineract.treasury.statement.TreasuryAutoMatcher.EntryCandidate;
import org.apache.fineract.treasury.statement.TreasuryAutoMatcher.LineCandidate;
import org.apache.fineract.treasury.statement.TreasuryAutoMatcher.Match;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statement import and reconciliation. Every operation that matches or unmatches locks the treasury account row first,
 * so two of them on one account cannot tie the same journal entry twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryReconciliationWriteService {

    private static final String STATEMENT = "statement";
    private static final String RECON = "reconciliation";
    private static final String FORMAT = "statement.format";

    private final TreasuryActor actor;
    private final PlatformSecurityContext context;
    private final TreasuryStatementDataValidator validator;
    private final TreasuryAccountRepository accountRepository;
    private final TreasuryStatementFormatRepository formatRepository;
    private final TreasuryStatementRepository statementRepository;
    private final TreasuryStatementLineRepository lineRepository;
    private final TreasuryReconMatchRepository matchRepository;
    private final TreasuryReconMatchItemRepository itemRepository;
    private final TreasuryReconciliationReadService readService;
    private final TreasurySettingService settingService;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final TreasuryJournalPoster journalPoster;
    private final TreasuryJournalEntryRepository treasuryJournalEntryRepository;
    private final JdbcTemplate jdbcTemplate;

    // ---------- import ----------

    @Transactional
    public CommandProcessingResult importStatement(final JsonCommand command) {
        final ImportRequest request = this.validator.validateImport(command.json());
        final TreasuryAccount account = activeAccount(request.treasuryAccountId());
        lockAccount(account.getId());
        final TreasuryStatementFormat format = this.formatRepository.findByCode(request.formatCode())
                .orElseThrow(() -> new TreasuryWorkflowException(STATEMENT, "format.not.found",
                        "Statement format " + request.formatCode() + " does not exist", request.formatCode()));
        if (!format.isActive()) {
            throw new TreasuryWorkflowException(STATEMENT, "format.inactive", "Statement format " + format.getCode() + " is inactive",
                    format.getCode());
        }
        final String fileHash = StatementFiles.sha256(request.content());
        if (this.statementRepository.existsByTreasuryAccountIdAndFileHash(account.getId(), fileHash)) {
            throw new TreasuryWorkflowException(STATEMENT, "duplicate.file",
                    "This file has already been imported for treasury account " + account.getCode(), account.getId());
        }

        final ParsedStatement parsed = parse(format, StatementFiles.text(request.content()));
        if (DateUtils.isAfterBusinessDate(parsed.periodTo())) {
            throw new TreasuryWorkflowException(STATEMENT, "future.dated", "The statement has lines dated up to " + parsed.periodTo()
                    + ", after the business date " + DateUtils.getBusinessLocalDate(), parsed.periodTo());
        }
        if (parsed.currencyCode() != null && !parsed.currencyCode().equals(account.getCurrencyCode())) {
            throw new TreasuryWorkflowException(STATEMENT, "currency.mismatch", "The statement is in " + parsed.currencyCode()
                    + " but treasury account " + account.getCode() + " is in " + account.getCurrencyCode(), parsed.currencyCode(),
                    account.getCurrencyCode());
        }
        final StatementBalanceCheck.Balances balances = StatementBalanceCheck.check(parsed, request.openingBalance(),
                request.closingBalance());
        final List<String> keys = StatementFiles.lineKeys(parsed.lines());
        final List<String> existing = this.lineRepository.findExistingKeys(account.getId(), keys);
        if (!existing.isEmpty()) {
            final int lineNumber = parsed.lines().get(keys.indexOf(existing.getFirst())).lineNumber();
            throw new TreasuryWorkflowException(STATEMENT, "duplicate.lines",
                    existing.size() + " lines of this statement were already imported for treasury account " + account.getCode()
                            + ", the first at line " + lineNumber,
                    account.getId(), existing.size(), lineNumber);
        }

        final List<Long> overlapping = this.statementRepository.findOverlapping(account.getId(), parsed.periodFrom(), parsed.periodTo());
        final TreasuryStatement statement = new TreasuryStatement();
        statement.setTreasuryAccountId(account.getId());
        statement.setFormatId(format.getId());
        statement.setStatementReference(StringUtils.abbreviate(parsed.statementReference(), 100));
        statement.setFileName(request.fileName());
        statement.setFileHash(fileHash);
        statement.setCurrencyCode(account.getCurrencyCode());
        statement.setPeriodFrom(parsed.periodFrom());
        statement.setPeriodTo(parsed.periodTo());
        statement.setOpeningBalance(balances.opening());
        statement.setClosingBalance(balances.closing());
        statement.setLineCount(parsed.lines().size());
        statement.setStatus(TreasuryStatementStatus.IMPORTED);
        statement.setImportedBy(this.actor.userId(command));
        statement.setImportedOnDate(DateUtils.getBusinessLocalDate());
        this.statementRepository.saveAndFlush(statement);

        final List<TreasuryStatementLine> lines = new ArrayList<>();
        for (int i = 0; i < parsed.lines().size(); i++) {
            final ParsedStatement.Line parsedLine = parsed.lines().get(i);
            final TreasuryStatementLine line = new TreasuryStatementLine();
            line.setStatementId(statement.getId());
            line.setTreasuryAccountId(account.getId());
            line.setLineNumber(parsedLine.lineNumber());
            line.setTransactionDate(parsedLine.transactionDate());
            line.setValueDate(parsedLine.valueDate());
            line.setAmount(parsedLine.amount());
            line.setDescription(StringUtils.abbreviate(parsedLine.description(), 500));
            line.setReference(StringUtils.abbreviate(parsedLine.reference(), 150));
            line.setRunningBalance(parsedLine.balance());
            line.setLineKey(keys.get(i));
            line.setMatchStatus(TreasuryLineMatchStatus.UNMATCHED);
            lines.add(line);
        }
        this.lineRepository.saveAll(lines);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("lineCount", lines.size());
        if (!overlapping.isEmpty()) {
            // accepted: a bank may send an interim and a final statement for the same days; the lines themselves are
            // already refused if imported before
            changes.put("warning", "The period " + statement.getPeriodFrom() + " to " + statement.getPeriodTo() + " overlaps statements "
                    + overlapping + " of this account");
            changes.put("overlappingStatementIds", overlapping);
        }
        changes.put("periodFrom", statement.getPeriodFrom().toString());
        changes.put("periodTo", statement.getPeriodTo().toString());
        changes.put("openingBalance", balances.opening());
        changes.put("closingBalance", balances.closing());
        return result(command, statement.getId(), account, changes);
    }

    static ParsedStatement parse(final TreasuryStatementFormat format, final String text) {
        return format.getFormatType() == TreasuryStatementFormatType.MT940 ? Mt940StatementParser.parse(text)
                : CsvStatementParser.parse(text, format.toCsvFormat());
    }

    // ---------- automatic matching ----------

    /**
     * Matches the statement's unmatched lines one to one with the account's unmatched journal entries from the window
     * before the statement starts to the window after it ends, by the currency's rules.
     */
    @Transactional
    public CommandProcessingResult automatch(final Long statementId, final JsonCommand command) {
        final TreasuryStatement statement = statement(statementId);
        final TreasuryAccount account = account(statement.getTreasuryAccountId());
        lockAccount(account.getId());
        final TreasurySetting setting = this.settingService.settingsFor(account.getCurrencyCode());
        final TreasuryAutoMatcher.Rules rules = new TreasuryAutoMatcher.Rules(setting.isReconReferenceRule(), setting.isReconSameDateRule(),
                setting.isReconWindowRule(), setting.getReconWindowDays());
        final List<TreasuryStatementLine> lines = this.lineRepository.findByStatementIdAndMatchStatusOrderByLineNumberAsc(statementId,
                TreasuryLineMatchStatus.UNMATCHED);
        final int window = Math.max(rules.windowDays(), 0);
        final List<EntryCandidate> entries = this.readService.unmatchedEntries(account, statement.getPeriodFrom().minusDays(window),
                statement.getPeriodTo().plusDays(window));
        final List<Match> matches = TreasuryAutoMatcher.match(lines.stream().map(line -> new LineCandidate(line.getId(),
                line.getTransactionDate(), line.getValueDate(), line.getAmount(), line.getReference(), line.getDescription())).toList(),
                entries, rules);

        final Long userId = this.actor.userId(command);
        final Map<Long, TreasuryStatementLine> lineById = new LinkedHashMap<>();
        lines.forEach(line -> lineById.put(line.getId(), line));
        final Map<String, Integer> byRule = new LinkedHashMap<>();
        for (final Match match : matches) {
            final TreasuryStatementLine line = lineById.get(match.lineId());
            final TreasuryReconMatch recon = newMatch(account, TreasuryReconMatchRule.valueOf(match.rule().name()), userId, null);
            this.itemRepository.save(TreasuryReconMatchItem.line(recon.getId(), line.getId(), line.getAmount()));
            this.itemRepository.save(TreasuryReconMatchItem.entry(recon.getId(), match.entryId(), line.getAmount()));
            markLine(line, recon, TreasuryLineMatchStatus.MATCHED);
            byRule.merge(match.rule().name(), 1, Integer::sum);
        }
        refreshStatus(Set.of(statementId));
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("matched", matches.size());
        changes.put("byRule", byRule);
        changes.put("stillUnmatched", lines.size() - matches.size());
        changes.put("status", statement(statementId).getStatus().name());
        return result(command, statementId, account, changes);
    }

    // ---------- manual matching ----------

    /**
     * Ties any number of unmatched lines of one account to any number of its unmatched journal entries whose total is
     * the same. When the match was held for a checker, the checker may not have imported any of the statements.
     */
    @Transactional
    public CommandProcessingResult createMatch(final JsonCommand command) {
        final MatchRequest request = this.validator.validateMatch(command.json());
        final List<TreasuryStatementLine> lines = this.lineRepository.findAllById(request.lineIds());
        if (lines.size() != request.lineIds().size()) {
            final Set<Long> missing = new LinkedHashSet<>(request.lineIds());
            lines.forEach(line -> missing.remove(line.getId()));
            throw new TreasuryWorkflowException(RECON, "line.not.found", "Statement lines " + missing + " do not exist",
                    missing.toString());
        }
        final Set<Long> accountIds = new LinkedHashSet<>();
        lines.forEach(line -> accountIds.add(line.getTreasuryAccountId()));
        if (accountIds.size() > 1) {
            throw new TreasuryWorkflowException(RECON, "lines.of.several.accounts",
                    "The statement lines belong to more than one treasury account " + accountIds, accountIds.toString());
        }
        final TreasuryAccount account = account(accountIds.iterator().next());
        lockAccount(account.getId());
        for (final TreasuryStatementLine line : lines) {
            if (line.getMatchStatus() != TreasuryLineMatchStatus.UNMATCHED) {
                throw new TreasuryWorkflowException(RECON, "line.already.matched",
                        "Statement line " + line.getId() + " is already " + line.getMatchStatus(), line.getId());
            }
        }
        final List<EntryCandidate> entries = this.readService.matchableEntries(account, request.journalEntryIds());
        if (entries.size() != request.journalEntryIds().size()) {
            final Set<Long> notMatchable = new LinkedHashSet<>(request.journalEntryIds());
            entries.forEach(entry -> notMatchable.remove(entry.id()));
            throw new TreasuryWorkflowException(RECON, "entry.not.matchable",
                    "Journal entries " + notMatchable + " are not unmatched, unreversed entries of treasury account " + account.getCode()
                            + " (GL account, office and currency)",
                    notMatchable.toString());
        }
        final BigDecimal lineTotal = lines.stream().map(TreasuryStatementLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal entryTotal = entries.stream().map(EntryCandidate::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (lineTotal.compareTo(entryTotal) != 0) {
            throw new TreasuryWorkflowException(RECON, "totals.differ",
                    "The statement lines total " + lineTotal.toPlainString() + " but the journal entries total "
                            + entryTotal.toPlainString() + " (debits positive); a difference can be explained on a line instead",
                    lineTotal, entryTotal);
        }
        final Set<Long> statementIds = new LinkedHashSet<>();
        lines.forEach(line -> statementIds.add(line.getStatementId()));
        requireCheckerDidNotImport(command, statementIds);

        final TreasuryReconMatch recon = newMatch(account, TreasuryReconMatchRule.MANUAL, this.actor.userId(command), request.note());
        for (final TreasuryStatementLine line : lines) {
            this.itemRepository.save(TreasuryReconMatchItem.line(recon.getId(), line.getId(), line.getAmount()));
            markLine(line, recon, TreasuryLineMatchStatus.MATCHED);
        }
        for (final EntryCandidate entry : entries) {
            this.itemRepository.save(TreasuryReconMatchItem.entry(recon.getId(), entry.id(), entry.amount()));
        }
        refreshStatus(statementIds);
        return result(command, recon.getId(), account,
                Map.of("lines", lines.size(), "journalEntries", entries.size(), "amount", lineTotal));
    }

    /**
     * Undoes a match or an explanation: its lines go back to UNMATCHED, its entries become matchable again, and an
     * explanation's adjusting entry is reversed on the business date. The match is kept, UNMATCHED, with the reason.
     */
    @Transactional
    public CommandProcessingResult unmatch(final Long matchId, final JsonCommand command) {
        final String reason = this.validator.validateUnmatch(command.json());
        final TreasuryReconMatch recon = this.matchRepository.findById(matchId).orElseThrow(() -> new TreasuryWorkflowException(RECON,
                "match.not.found", "Reconciliation match " + matchId + " does not exist", matchId));
        final TreasuryAccount account = account(recon.getTreasuryAccountId());
        lockAccount(account.getId());
        if (!recon.isActive()) {
            throw new TreasuryWorkflowException(RECON, "match.not.active", "Reconciliation match " + matchId + " is already unmatched",
                    matchId);
        }
        final Map<String, Object> changes = new LinkedHashMap<>();
        if (recon.getAdjustmentTransactionId() != null) {
            changes.put("adjustmentReversalTransactionId",
                    this.journalPoster.reverse(TreasuryEventType.STATEMENT_ADJUSTMENT_REVERSAL, matchId, recon.getAdjustmentTransactionId(),
                            DateUtils.getBusinessLocalDate(), "Treasury reconciliation " + matchId + " undone: " + reason));
        }
        final List<TreasuryStatementLine> lines = this.lineRepository.findByMatchId(matchId);
        final Set<Long> statementIds = new LinkedHashSet<>();
        for (final TreasuryStatementLine line : lines) {
            line.setMatchStatus(TreasuryLineMatchStatus.UNMATCHED);
            line.setMatchId(null);
            statementIds.add(line.getStatementId());
        }
        this.lineRepository.saveAll(lines);
        recon.setStatus(TreasuryReconMatchStatus.UNMATCHED);
        recon.setUnmatchedBy(this.actor.userId(command));
        recon.setUnmatchedOnDate(DateUtils.getBusinessLocalDate());
        recon.setUnmatchReason(reason);
        this.matchRepository.saveAndFlush(recon);
        refreshStatus(statementIds);
        changes.put("status", recon.getStatus().name());
        changes.put("lines", lines.size());
        return result(command, matchId, account, changes);
    }

    // ---------- explanation ----------

    /**
     * Settles a line with a reason instead of a journal entry. With {@code glAccountId} it books the line as an
     * adjusting entry against the treasury account's GL account (money in: Dr treasury, Cr the given account; money
     * out: the reverse), dated the line's date unless another is given, and ties that entry to the line.
     */
    @Transactional
    public CommandProcessingResult explainLine(final Long lineId, final JsonCommand command) {
        final ExplainRequest request = this.validator.validateExplain(command.json());
        final TreasuryStatementLine line = this.lineRepository.findById(lineId).orElseThrow(
                () -> new TreasuryWorkflowException(RECON, "line.not.found", "Statement line " + lineId + " does not exist", lineId));
        final TreasuryAccount account = account(line.getTreasuryAccountId());
        lockAccount(account.getId());
        if (line.getMatchStatus() != TreasuryLineMatchStatus.UNMATCHED) {
            throw new TreasuryWorkflowException(RECON, "line.already.matched",
                    "Statement line " + lineId + " is already " + line.getMatchStatus(), lineId);
        }
        final Long reasonId = reasonCodeValueId(request.reasonCode());
        if (request.glAccountId() != null) {
            requirePostableAgainst(request.glAccountId(), account);
            requireNotImporter(command, line.getStatementId());
        }
        final TreasuryReconMatch recon = newMatch(account, TreasuryReconMatchRule.EXPLANATION, this.actor.userId(command), request.note());
        recon.setReasonCodeValueId(reasonId);
        this.itemRepository.save(TreasuryReconMatchItem.line(recon.getId(), line.getId(), line.getAmount()));
        final Map<String, Object> changes = new LinkedHashMap<>();
        if (request.glAccountId() != null) {
            final LocalDate date = request.transactionDate() == null ? line.getTransactionDate() : request.transactionDate();
            final String transactionId = this.journalPoster.post(TreasuryEventType.STATEMENT_ADJUSTMENT, recon.getId(),
                    account.getCurrencyCode(), date,
                    StringUtils.abbreviate("Statement line " + lineId + " explained as " + request.reasonCode()
                            + (line.getDescription() == null ? "" : ": " + line.getDescription()), 500),
                    StringUtils.abbreviate(line.getReference(), 100), adjustmentLegs(account, request.glAccountId(), line.getAmount()));
            recon.setAdjustmentTransactionId(transactionId);
            this.matchRepository.save(recon);
            for (final JournalEntry entry : this.treasuryJournalEntryRepository.findUnreversedByTransactionId(transactionId)) {
                if (Objects.equals(entry.getGlAccount().getId(), account.getGlAccountId())) {
                    this.itemRepository.save(TreasuryReconMatchItem.entry(recon.getId(), entry.getId(), line.getAmount()));
                }
            }
            changes.put("transactionId", transactionId);
        }
        markLine(line, recon, TreasuryLineMatchStatus.EXPLAINED);
        refreshStatus(Set.of(line.getStatementId()));
        changes.put("matchId", recon.getId());
        changes.put("status", TreasuryLineMatchStatus.EXPLAINED.name());
        return result(command, lineId, account, changes);
    }

    /** Money into the account debits its GL account; money out credits it. */
    static List<Leg> adjustmentLegs(final TreasuryAccount account, final Long otherGlAccountId, final BigDecimal lineAmount) {
        final BigDecimal amount = lineAmount.abs();
        final boolean moneyIn = lineAmount.signum() > 0;
        return List.of(
                new Leg(account.getOfficeId(), account.getGlAccountId(), moneyIn ? JournalEntryType.DEBIT : JournalEntryType.CREDIT,
                        amount),
                new Leg(account.getOfficeId(), otherGlAccountId, moneyIn ? JournalEntryType.CREDIT : JournalEntryType.DEBIT, amount));
    }

    // ---------- formats ----------

    @Transactional
    public CommandProcessingResult createFormat(final JsonCommand command) {
        final FormatRequest request = this.validator.validateFormat(command.json(), true);
        if (this.formatRepository.findByCode(request.code()).isPresent()) {
            throw new TreasuryWorkflowException(FORMAT, "duplicate.code",
                    "A statement format with code " + request.code() + " already exists", request.code());
        }
        final TreasuryStatementFormat format = new TreasuryStatementFormat();
        format.setCode(request.code());
        format.setFormatType(request.formatType());
        format.setActive(true);
        apply(format, request);
        requireReadable(format);
        this.formatRepository.saveAndFlush(format);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(format.getId()).build();
    }

    @Transactional
    public CommandProcessingResult updateFormat(final Long formatId, final JsonCommand command) {
        final FormatRequest request = this.validator.validateFormat(command.json(), false);
        final TreasuryStatementFormat format = this.formatRepository.findById(formatId).orElseThrow(
                () -> new TreasuryWorkflowException(FORMAT, "not.found", "Statement format " + formatId + " does not exist", formatId));
        final Map<String, Object> changes = apply(format, request);
        requireReadable(format);
        if (!changes.isEmpty()) {
            this.formatRepository.saveAndFlush(format);
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(formatId).with(changes).build();
    }

    private static Map<String, Object> apply(final TreasuryStatementFormat format, final FormatRequest request) {
        final Map<String, Object> changes = new LinkedHashMap<>();
        set(request, NAME_PARAM, format.getName(), request.name(), format::setName, changes);
        set(request, DELIMITER_PARAM, format.getDelimiter(), request.delimiter(), format::setDelimiter, changes);
        set(request, DATE_FORMAT_PARAM, format.getDateFormat(), request.dateFormat(), format::setDateFormat, changes);
        set(request, DECIMAL_SEPARATOR_PARAM, format.getDecimalSeparator(), request.decimalSeparator(), format::setDecimalSeparator,
                changes);
        set(request, DATE_COLUMN_PARAM, format.getDateColumn(), request.dateColumn(), format::setDateColumn, changes);
        set(request, VALUE_DATE_COLUMN_PARAM, format.getValueDateColumn(), request.valueDateColumn(), format::setValueDateColumn, changes);
        set(request, DESCRIPTION_COLUMN_PARAM, format.getDescriptionColumn(), request.descriptionColumn(), format::setDescriptionColumn,
                changes);
        set(request, REFERENCE_COLUMN_PARAM, format.getReferenceColumn(), request.referenceColumn(), format::setReferenceColumn, changes);
        set(request, DEBIT_COLUMN_PARAM, format.getDebitColumn(), request.debitColumn(), format::setDebitColumn, changes);
        set(request, CREDIT_COLUMN_PARAM, format.getCreditColumn(), request.creditColumn(), format::setCreditColumn, changes);
        set(request, AMOUNT_COLUMN_PARAM, format.getAmountColumn(), request.amountColumn(), format::setAmountColumn, changes);
        set(request, BALANCE_COLUMN_PARAM, format.getBalanceColumn(), request.balanceColumn(), format::setBalanceColumn, changes);
        if (request.has(HEADER_ROWS_PARAM) && format.getHeaderRows() != request.headerRows()) {
            format.setHeaderRows(request.headerRows());
            changes.put(HEADER_ROWS_PARAM, request.headerRows());
        }
        if (request.has(INVERT_AMOUNT_PARAM) && request.invertAmount() != null && format.isInvertAmount() != request.invertAmount()) {
            format.setInvertAmount(request.invertAmount());
            changes.put(INVERT_AMOUNT_PARAM, request.invertAmount());
        }
        if (request.has(ACTIVE_PARAM) && request.active() != null && format.isActive() != request.active()) {
            format.setActive(request.active());
            changes.put(ACTIVE_PARAM, request.active());
        }
        return changes;
    }

    private static void set(final FormatRequest request, final String parameter, final String current, final String requested,
            final java.util.function.Consumer<String> setter, final Map<String, Object> changes) {
        if (request.has(parameter) && !Objects.equals(current, requested)) {
            setter.accept(requested);
            changes.put(parameter, requested);
        }
    }

    /** A CSV format must name a date column and either an amount column or both debit and credit columns. */
    private static void requireReadable(final TreasuryStatementFormat format) {
        if (format.getFormatType() != TreasuryStatementFormatType.CSV) {
            return;
        }
        if (StringUtils.isBlank(format.getDateColumn())) {
            throw new TreasuryWorkflowException(FORMAT, "no.date.column", "A CSV format needs a dateColumn");
        }
        if (StringUtils.isBlank(format.getAmountColumn())
                && (StringUtils.isBlank(format.getDebitColumn()) || StringUtils.isBlank(format.getCreditColumn()))) {
            throw new TreasuryWorkflowException(FORMAT, "no.amount.column",
                    "A CSV format needs an amountColumn, or a debitColumn and a creditColumn");
        }
    }

    // ---------- helpers ----------

    private TreasuryReconMatch newMatch(final TreasuryAccount account, final TreasuryReconMatchRule rule, final Long userId,
            final String note) {
        final TreasuryReconMatch recon = new TreasuryReconMatch();
        recon.setTreasuryAccountId(account.getId());
        recon.setRule(rule);
        recon.setStatus(TreasuryReconMatchStatus.ACTIVE);
        recon.setMatchedBy(userId);
        recon.setMatchedOnDate(DateUtils.getBusinessLocalDate());
        recon.setNote(note);
        return this.matchRepository.saveAndFlush(recon);
    }

    private void markLine(final TreasuryStatementLine line, final TreasuryReconMatch recon, final TreasuryLineMatchStatus status) {
        line.setMatchStatus(status);
        line.setMatchId(recon.getId());
        this.lineRepository.save(line);
    }

    /** A statement is RECONCILED when no line is unmatched, else IMPORTED. */
    private void refreshStatus(final Set<Long> statementIds) {
        this.lineRepository.flush();
        for (final Long statementId : statementIds) {
            final TreasuryStatement statement = statement(statementId);
            final TreasuryStatementStatus status = this.lineRepository.countByStatementIdAndMatchStatus(statementId,
                    TreasuryLineMatchStatus.UNMATCHED) == 0 ? TreasuryStatementStatus.RECONCILED : TreasuryStatementStatus.IMPORTED;
            if (statement.getStatus() != status) {
                statement.setStatus(status);
                this.statementRepository.saveAndFlush(statement);
                log.info("Treasury statement {} of account {} is now {}", statementId, statement.getTreasuryAccountId(), status);
            }
        }
    }

    /**
     * When a manual match was held for approval, the checker approving it now runs it; the user who imported a
     * statement may not approve matches of its lines.
     */
    private void requireCheckerDidNotImport(final JsonCommand command, final Set<Long> statementIds) {
        if (command.commandId() == null) {
            return;
        }
        final Long checkerId = this.context.authenticatedUser().getId();
        if (Objects.equals(checkerId, this.actor.userId(command))) {
            return;
        }
        for (final Long statementId : statementIds) {
            if (Objects.equals(statement(statementId).getImportedBy(), checkerId)) {
                throw new TreasuryWorkflowException(RECON, "importer.cannot.approve",
                        "The user who imported statement " + statementId + " cannot approve a match of its lines", statementId);
            }
        }
    }

    /**
     * The user who imported a statement may not book an adjusting entry from it, neither directly nor by approving a
     * held explanation.
     */
    private void requireNotImporter(final JsonCommand command, final Long statementId) {
        final Long importer = statement(statementId).getImportedBy();
        final Long maker = this.actor.userId(command);
        final Long checker = command.commandId() == null ? null : this.context.authenticatedUser().getId();
        if (Objects.equals(importer, maker) || Objects.equals(importer, checker)) {
            throw new TreasuryWorkflowException(RECON, "importer.cannot.post.adjustment",
                    "The user who imported statement " + statementId + " cannot post an adjusting entry from its lines", statementId);
        }
    }

    private Long reasonCodeValueId(final String reasonCode) {
        final boolean byId = StringUtils.isNumeric(reasonCode);
        final List<Long> ids = this.jdbcTemplate.queryForList(
                "SELECT cv.id FROM m_code_value cv JOIN m_code c ON c.id = cv.code_id" + " WHERE c.code_name = ? AND cv.is_active = ? AND "
                        + (byId ? "cv.id = ?" : "UPPER(cv.code_value) = UPPER(?)"),
                Long.class, RECON_EXPLANATION_CODE, Boolean.TRUE, byId ? Long.valueOf(reasonCode) : reasonCode);
        if (ids.isEmpty()) {
            throw new TreasuryWorkflowException(RECON, "reason.unknown",
                    "Explanation reason " + reasonCode + " is not an active value of code " + RECON_EXPLANATION_CODE, reasonCode);
        }
        return ids.getFirst();
    }

    private void requirePostableAgainst(final Long glAccountId, final TreasuryAccount account) {
        if (Objects.equals(glAccountId, account.getGlAccountId())) {
            throw new TreasuryWorkflowException(RECON, "adjustment.same.gl.account",
                    "The adjusting entry needs a GL account other than the treasury account's own", glAccountId);
        }
        final GlAccountInfo gl = this.referenceDataLookup.findGlAccount(glAccountId).orElseThrow(() -> new TreasuryWorkflowException(RECON,
                "gl.account.not.found", "GL account " + glAccountId + " does not exist", glAccountId));
        if (!gl.isDetail() || gl.disabled()) {
            throw new TreasuryWorkflowException(RECON, "gl.account.not.postable",
                    "GL account " + glAccountId + " must be an enabled detail account", glAccountId);
        }
    }

    private void lockAccount(final Long accountId) {
        this.jdbcTemplate.queryForList("SELECT id FROM m_treasury_account WHERE id = ? FOR UPDATE", Long.class, accountId);
    }

    private TreasuryStatement statement(final Long statementId) {
        return this.statementRepository.findById(statementId).orElseThrow(
                () -> new TreasuryWorkflowException(STATEMENT, "not.found", "Statement " + statementId + " does not exist", statementId));
    }

    private TreasuryAccount account(final Long accountId) {
        return this.accountRepository.findById(accountId).orElseThrow(() -> new TreasuryAccountNotFoundException(accountId));
    }

    private TreasuryAccount activeAccount(final Long accountId) {
        final TreasuryAccount account = account(accountId);
        if (!account.isActive()) {
            throw new TreasuryWorkflowException(STATEMENT, "account.closed", "Treasury account " + account.getCode() + " is closed",
                    accountId);
        }
        return account;
    }

    private static CommandProcessingResult result(final JsonCommand command, final Long entityId, final TreasuryAccount account,
            final Map<String, Object> changes) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(entityId)
                .withOfficeId(account.getOfficeId()).withSubEntityId(account.getId()).with(new LinkedHashMap<>(changes)).build();
    }
}
