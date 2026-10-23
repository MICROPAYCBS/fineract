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

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryFxRevalConfigData;
import org.apache.fineract.treasury.data.TreasuryFxRevaluationData;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryEventType;
import org.apache.fineract.treasury.domain.TreasuryFxRevalConfig;
import org.apache.fineract.treasury.domain.TreasuryFxRevalConfigRepository;
import org.apache.fineract.treasury.domain.TreasuryFxRevaluation;
import org.apache.fineract.treasury.domain.TreasuryFxRevaluationRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.fx.FxRates;
import org.apache.fineract.treasury.fx.FxRevaluationCalculator;
import org.apache.fineract.treasury.fx.FxRevaluationCalculator.Movement;
import org.apache.fineract.treasury.fx.FxRevaluationStatus;
import org.apache.fineract.treasury.limits.LimitMetric;
import org.apache.fineract.treasury.liquidity.StreamingQueries;
import org.apache.fineract.treasury.serialization.TreasuryFxDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryFxDataValidator.ConfigRequest;
import org.apache.fineract.treasury.serialization.TreasuryFxDataValidator.RunRequest;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Day-end revaluation of foreign currency GL balances, per configured GL account and currency, per office.
 *
 * <p>
 * Fineract journal entries are in one currency with no base equivalent, so the base value of a foreign currency balance
 * is kept on a base currency adjustment account. The revaluation is cumulative: the target is the balance (debit less
 * credit) at the closing MID rate; the carried base is each day's movement at that day's MID rate plus the revaluations
 * already posted; their difference is posted in the base currency, up as Dr adjustment / Cr unrealised gain, down as Dr
 * unrealised loss / Cr adjustment, in the office of the balance on the revaluation date. A rerun posts only what
 * changed; a revaluation before a later one is refused, and only the latest can be reversed.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryFxRevaluationService {

    static final String ENTITY = "fx.revaluation";
    static final int GL_INCOME = 4;
    static final int GL_EXPENSE = 5;

    private static final String MOVEMENTS_SQL = """
            SELECT je.office_id, je.entry_date, SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END) AS net
            FROM acc_gl_journal_entry je
            WHERE je.account_id = :gl AND je.currency_code = :currency AND je.entry_date <= :asOf
            GROUP BY je.office_id, je.entry_date
            ORDER BY je.office_id, je.entry_date
            """;

    private final TreasuryFxDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryFxRevalConfigRepository configRepository;
    private final TreasuryFxRevaluationRepository revaluationRepository;
    private final TreasuryFxRateService rateService;
    private final TreasuryFxConfig fxConfig;
    private final TreasuryJournalPoster journalPoster;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final TreasuryAccountRepository accountRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final AppUserRepositoryWrapper appUserRepository;
    private final TreasuryLimitEngine limitEngine;

    // ---------- settings ----------

    /** Sets how a foreign currency GL account is revalued; one setting per GL account and currency. */
    @Transactional
    public CommandProcessingResult updateConfig(final JsonCommand command) {
        final ConfigRequest request = this.validator.validateConfig(command.json());
        if (request.currencyCode().equals(this.fxConfig.baseCurrency())
                || !this.referenceDataLookup.isOrganisationCurrency(request.currencyCode())) {
            throw new TreasuryWorkflowException(ENTITY, "currency.invalid",
                    request.currencyCode() + " is not a foreign organisation currency", request.currencyCode());
        }
        detail(request.glAccountId(), null);
        detail(request.adjustmentGlAccountId(), null);
        detail(request.gainGlAccountId(), GL_INCOME);
        detail(request.lossGlAccountId(), GL_EXPENSE);
        final Long userId = this.actor.userId(command);
        final Optional<TreasuryFxRevalConfig> existing = this.configRepository.findByGlAccountIdAndCurrencyCode(request.glAccountId(),
                request.currencyCode());
        final TreasuryFxRevalConfig config = existing.orElseGet(TreasuryFxRevalConfig::new);
        if (existing.isEmpty()) {
            config.setGlAccountId(request.glAccountId());
            config.setCurrencyCode(request.currencyCode());
            config.setCreatedBy(userId);
            config.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        } else {
            config.setLastModifiedBy(userId);
            config.setLastModifiedOnUtc(DateUtils.getAuditOffsetDateTime());
        }
        config.setAdjustmentGlAccountId(request.adjustmentGlAccountId());
        config.setGainGlAccountId(request.gainGlAccountId());
        config.setLossGlAccountId(request.lossGlAccountId());
        config.setActive(request.active());
        config.setNote(request.note());
        this.configRepository.saveAndFlush(config);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("created", existing.isEmpty());
        changes.put("active", config.isActive());
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(config.getId()).with(changes).build();
    }

    private void detail(final Long glAccountId, final Integer classification) {
        final TreasuryReferenceDataLookup.GlAccountInfo gl = this.referenceDataLookup.findGlAccount(glAccountId)
                .orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "gl.account.not.found",
                        "GL account " + glAccountId + " does not exist", glAccountId));
        if (!gl.isDetail() || gl.disabled()) {
            throw new TreasuryWorkflowException(ENTITY, "gl.account.not.postable", "GL account " + glAccountId + " is a header or disabled",
                    glAccountId);
        }
        if (classification != null && gl.classification() != classification) {
            throw new TreasuryWorkflowException(ENTITY, classification == GL_INCOME ? "gain.not.income" : "loss.not.expense",
                    "GL account " + glAccountId + " must be an " + (classification == GL_INCOME ? "income" : "expense") + " account",
                    glAccountId);
        }
    }

    // ---------- runs ----------

    @Transactional
    public CommandProcessingResult run(final JsonCommand command) {
        final RunRequest request = this.validator.validateRun(command.json());
        final LocalDate asOf = request.asOfDate() == null ? DateUtils.getBusinessLocalDate() : request.asOfDate();
        if (DateUtils.isAfterBusinessDate(asOf)) {
            throw new TreasuryWorkflowException(ENTITY, "future.date", "The revaluation date " + asOf + " is after the business date",
                    asOf);
        }
        final List<TreasuryFxRevaluation> rows = revalue(asOf, request.currencyCode(), this.actor.userId(command));
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("revaluationDate", asOf);
        changes.put("revaluations", rows.size());
        changes.put("posted", rows.stream().filter(r -> r.getStatus() == FxRevaluationStatus.POSTED).count());
        changes.put("adjustment", rows.stream().map(TreasuryFxRevaluation::getAdjustment).reduce(BigDecimal.ZERO, BigDecimal::add));
        changes.put("revaluationIds", rows.stream().map(TreasuryFxRevaluation::getId).toList());
        return new CommandProcessingResultBuilder().withCommandId(command.commandId())
                .withEntityId(rows.isEmpty() ? null : rows.getFirst().getId()).with(changes).build();
    }

    /** Every active setting of the currency (or of every currency), each office with entries in the GL account. */
    List<TreasuryFxRevaluation> revalue(final LocalDate asOf, final String currency, final Long userId) {
        final List<TreasuryFxRevaluation> rows = new ArrayList<>();
        final int scale = this.referenceDataLookup.decimalPlaces(this.fxConfig.baseCurrency());
        for (final TreasuryFxRevalConfig config : this.configRepository.findByActiveTrueOrderByCurrencyCodeAscGlAccountIdAsc()) {
            if (currency != null && !currency.equals(config.getCurrencyCode())) {
                continue;
            }
            final FxRates.Rate closing = this.rateService.closingMid(config.getCurrencyCode(), asOf);
            final Map<Long, List<Movement>> byOffice = new LinkedHashMap<>();
            StreamingQueries.query(this.jdbcTemplate, MOVEMENTS_SQL, new MapSqlParameterSource("gl", config.getGlAccountId())
                    .addValue("currency", config.getCurrencyCode()).addValue("asOf", asOf), rs -> {
                        byOffice.computeIfAbsent(rs.getLong("office_id"), o -> new ArrayList<>())
                                .add(new Movement(rs.getDate("entry_date").toLocalDate(), rs.getBigDecimal("net")));
                    });
            for (final Map.Entry<Long, List<Movement>> office : byOffice.entrySet()) {
                rows.add(revalue(config, office.getKey(), office.getValue(), closing, asOf, scale, userId));
            }
        }
        return rows;
    }

    private TreasuryFxRevaluation revalue(final TreasuryFxRevalConfig config, final Long officeId, final List<Movement> movements,
            final FxRates.Rate closing, final LocalDate asOf, final int scale, final Long userId) {
        final List<TreasuryFxRevaluation> posted = this.revaluationRepository
                .findByGlAccountIdAndOfficeIdAndCurrencyCodeAndStatusOrderByRevaluationDateAscIdAsc(config.getGlAccountId(), officeId,
                        config.getCurrencyCode(), FxRevaluationStatus.POSTED);
        if (posted.stream().anyMatch(r -> r.getRevaluationDate().isAfter(asOf))) {
            throw new TreasuryWorkflowException(
                    ENTITY, "later.revaluation.exists", "GL account " + config.getGlAccountId() + " in " + config.getCurrencyCode()
                            + " (office " + officeId + ") was revalued after " + asOf + "; reverse that first",
                    config.getGlAccountId(), asOf);
        }
        final BigDecimal prior = posted.stream().map(TreasuryFxRevaluation::getAdjustment).reduce(BigDecimal.ZERO, BigDecimal::add);
        final FxRevaluationCalculator.Result result = FxRevaluationCalculator.revalue(movements,
                date -> this.rateService.mid(config.getCurrencyCode(), date)
                        .orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "movement.rate.missing",
                                "No MID rate of " + config.getCurrencyCode() + " on or before " + date
                                        + ", the date of a movement of GL account " + config.getGlAccountId(),
                                config.getCurrencyCode(), date))
                        .rate(),
                closing.rate(), prior, scale);
        final TreasuryFxRevaluation row = new TreasuryFxRevaluation();
        row.setConfigId(config.getId());
        row.setGlAccountId(config.getGlAccountId());
        row.setOfficeId(officeId);
        row.setCurrencyCode(config.getCurrencyCode());
        row.setBaseCurrency(this.fxConfig.baseCurrency());
        row.setRevaluationDate(asOf);
        row.setFcBalance(result.balance());
        row.setRate(closing.rate());
        row.setRateId(closing.id());
        row.setRateDate(closing.rateDate());
        row.setBaseEquivalent(result.target());
        row.setMovementsBase(result.movementsBase());
        row.setPriorAdjustments(result.priorAdjustments());
        row.setCarriedBase(result.carried());
        row.setAdjustment(result.adjustment());
        row.setStatus(result.adjustment().signum() == 0 ? FxRevaluationStatus.ZERO : FxRevaluationStatus.POSTED);
        row.setCreatedBy(userId);
        row.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.revaluationRepository.saveAndFlush(row);
        if (row.getStatus() == FxRevaluationStatus.POSTED) {
            final BigDecimal amount = result.adjustment().abs();
            final List<Leg> legs = result.isGain()
                    ? List.of(new Leg(officeId, config.getAdjustmentGlAccountId(), JournalEntryType.DEBIT, amount),
                            new Leg(officeId, config.getGainGlAccountId(), JournalEntryType.CREDIT, amount))
                    : List.of(new Leg(officeId, config.getLossGlAccountId(), JournalEntryType.DEBIT, amount),
                            new Leg(officeId, config.getAdjustmentGlAccountId(), JournalEntryType.CREDIT, amount));
            row.setTransactionId(
                    this.journalPoster
                            .post(TreasuryEventType.FX_REVALUATION, row.getId(), row.getBaseCurrency(), asOf,
                                    "FX revaluation of " + result.balance().stripTrailingZeros().toPlainString() + " "
                                            + config.getCurrencyCode() + " at " + closing.rate().stripTrailingZeros().toPlainString(),
                                    null, legs));
            this.revaluationRepository.saveAndFlush(row);
        }
        return row;
    }

    /** Reverses a posted revaluation, the latest of its GL account, currency and office, dated the business date. */
    @Transactional
    public CommandProcessingResult reverse(final Long revaluationId, final JsonCommand command) {
        this.validator.validateNote(command.json());
        final TreasuryFxRevaluation row = find(revaluationId);
        if (row.getStatus() != FxRevaluationStatus.POSTED) {
            throw new TreasuryWorkflowException(ENTITY, "not.posted", "Revaluation " + revaluationId + " is " + row.getStatus(),
                    revaluationId);
        }
        final boolean later = this.revaluationRepository
                .findByGlAccountIdAndOfficeIdAndCurrencyCodeAndStatusOrderByRevaluationDateAscIdAsc(row.getGlAccountId(), row.getOfficeId(),
                        row.getCurrencyCode(), FxRevaluationStatus.POSTED)
                .stream().anyMatch(r -> !Objects.equals(r.getId(), row.getId()) && (r.getRevaluationDate().isAfter(row.getRevaluationDate())
                        || r.getRevaluationDate().equals(row.getRevaluationDate()) && r.getId() > row.getId()));
        if (later) {
            throw new TreasuryWorkflowException(ENTITY, "not.latest",
                    "Revaluation " + revaluationId + " is not the latest of its account; reverse" + " the later ones first", revaluationId);
        }
        final Long userId = this.actor.userId(command);
        row.setReversalTransactionId(this.journalPoster.reverse(TreasuryEventType.FX_REVALUATION_REVERSAL, row.getId(),
                row.getTransactionId(), DateUtils.getBusinessLocalDate(), "Reversal of FX revaluation " + row.getId()));
        row.setStatus(FxRevaluationStatus.REVERSED);
        row.setReversedBy(userId);
        row.setReversedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.revaluationRepository.saveAndFlush(row);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("status", FxRevaluationStatus.REVERSED.name());
        changes.put("reversalTransactionId", row.getReversalTransactionId());
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(revaluationId).with(changes).build();
    }

    /**
     * The job: the closed business day (the business date less one), currency by currency, a currency that fails being
     * logged; then the net open position limits of every currency.
     */
    public int runClosedDay() {
        final LocalDate asOf = DateUtils.getBusinessLocalDate().minusDays(1);
        final TransactionTemplate template = new TransactionTemplate(this.transactionManager);
        // Each scope in its own transaction, so that one that fails is rolled back alone and not the job's
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        final Long systemUserId = template.execute(status -> this.appUserRepository.fetchSystemUser().getId());
        int revaluations = 0;
        for (final String currency : this.referenceDataLookup.organisationCurrencies()) {
            if (currency.equals(this.fxConfig.baseCurrency())) {
                continue;
            }
            try {
                final List<TreasuryFxRevaluation> rows = template.execute(status -> revalue(asOf, currency, systemUserId));
                revaluations += rows == null ? 0 : rows.size();
            } catch (RuntimeException e) {
                log.error("Treasury FX revaluation of {} on {} failed: {}", currency, asOf, e.getMessage());
            }
        }
        for (final String currency : this.referenceDataLookup.organisationCurrencies()) {
            try {
                template.execute(status -> this.limitEngine.evaluate(this.limitEngine.contextFor(asOf, currency, null),
                        EnumSet.of(LimitMetric.FX_SINGLE_NOP, LimitMetric.FX_AGGREGATE_NOP), TreasuryLimitEngine.TRIGGER_JOB, null,
                        systemUserId, false));
            } catch (RuntimeException e) {
                log.error("Treasury net open position limits of {} on {} failed: {}", currency, asOf, e.getMessage());
            }
        }
        log.info("Treasury FX revaluation for {}: {} revaluations", asOf, revaluations);
        return revaluations;
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public List<TreasuryFxRevalConfigData> retrieveConfigs() {
        final List<TreasuryAccount> accounts = this.accountRepository.findAll();
        return this.configRepository.findAllByOrderByCurrencyCodeAscGlAccountIdAsc().stream().map(c -> new TreasuryFxRevalConfigData(
                c.getId(), c.getGlAccountId(), c.getCurrencyCode(), c.getAdjustmentGlAccountId(), c.getGainGlAccountId(),
                c.getLossGlAccountId(), c.isActive(), c.getNote(),
                accounts.stream()
                        .filter(a -> a.getGlAccountId().equals(c.getGlAccountId()) && a.getCurrencyCode().equals(c.getCurrencyCode()))
                        .map(TreasuryAccount::getCode).toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TreasuryFxRevaluationData> retrieveAll(final LocalDate date, final String currency, final Long glAccountId,
            final String status) {
        return this.revaluationRepository.findAll().stream().filter(r -> date == null || r.getRevaluationDate().equals(date))
                .filter(r -> currency == null || r.getCurrencyCode().equalsIgnoreCase(currency.trim()))
                .filter(r -> glAccountId == null || r.getGlAccountId().equals(glAccountId))
                .filter(r -> status == null || r.getStatus().name().equalsIgnoreCase(status.trim()))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId())).limit(500).map(TreasuryFxRevaluationService::data).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryFxRevaluationData retrieveOne(final Long revaluationId) {
        return data(find(revaluationId));
    }

    private TreasuryFxRevaluation find(final Long revaluationId) {
        return this.revaluationRepository.findById(revaluationId).orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "not.found",
                "FX revaluation " + revaluationId + " does not exist", revaluationId));
    }

    private static TreasuryFxRevaluationData data(final TreasuryFxRevaluation r) {
        return new TreasuryFxRevaluationData(r.getId(), r.getConfigId(), r.getGlAccountId(), r.getOfficeId(), r.getCurrencyCode(),
                r.getBaseCurrency(), r.getRevaluationDate(), r.getFcBalance(), r.getRate(), r.getRateDate(), r.getBaseEquivalent(),
                r.getMovementsBase(), r.getPriorAdjustments(), r.getCarriedBase(), r.getAdjustment(), r.getStatus().name(),
                r.getTransactionId(), r.getReversalTransactionId(), r.getCreatedBy(), r.getCreatedOnUtc());
    }
}
