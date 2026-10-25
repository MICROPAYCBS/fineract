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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryTrustData;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.domain.TreasuryAccountType;
import org.apache.fineract.treasury.domain.TreasuryCounterparty;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyRepository;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.domain.TreasuryEMoneyBalance;
import org.apache.fineract.treasury.domain.TreasuryEMoneyBalanceRepository;
import org.apache.fineract.treasury.domain.TreasuryRuleSet;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
import org.apache.fineract.treasury.domain.TreasuryRunTrust;
import org.apache.fineract.treasury.domain.TreasuryRunTrustLine;
import org.apache.fineract.treasury.domain.TreasuryRunTrustLineRepository;
import org.apache.fineract.treasury.domain.TreasuryRunTrustRepository;
import org.apache.fineract.treasury.domain.TreasurySegregation;
import org.apache.fineract.treasury.domain.TreasuryTenantSetting;
import org.apache.fineract.treasury.domain.TreasuryTrustAccount;
import org.apache.fineract.treasury.domain.TreasuryTrustAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryTrustHolding;
import org.apache.fineract.treasury.domain.TreasuryTrustHoldingRepository;
import org.apache.fineract.treasury.domain.TreasuryTrustInterest;
import org.apache.fineract.treasury.domain.TreasuryTrustInterestRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.liquidity.RunType;
import org.apache.fineract.treasury.trust.EMoneyCategory;
import org.apache.fineract.treasury.trust.EMoneySource;
import org.apache.fineract.treasury.trust.EMoneySourceType;
import org.apache.fineract.treasury.trust.TrustAssetType;
import org.apache.fineract.treasury.trust.TrustCalculator;
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
 * E-money trust safeguarding for a payment service provider (NPS Act 2020, NPS Regulations 2021): the TRUST accounts
 * and the deals held in trust, the e-money outstanding (from Fineract savings or imported), the daily TRUST_RECON run
 * with its statement (reg 22(5)) and its limits (reg 24: a breach is notified at once), the weekly asset breakdown (reg
 * 22(6)), and the interest earned on the trust (reg 14). Allocating the interest to customers and paying it out is left
 * to the wallet platform.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryTrustService {

    static final String ENTITY = "trust";
    static final String COMPLETED = "COMPLETED";
    static final String SUBMITTED = "SUBMITTED";
    static final String SOURCE_DEAL = "DEAL_ACCRUAL";
    static final String SOURCE_BANK = "BANK_INTEREST";

    private final TreasuryAccountRepository accountRepository;
    private final TreasuryTrustAccountRepository trustAccountRepository;
    private final TreasuryTrustHoldingRepository holdingRepository;
    private final TreasuryEMoneyBalanceRepository emoneyRepository;
    private final TreasuryRunRepository runRepository;
    private final TreasuryRunTrustRepository runTrustRepository;
    private final TreasuryRunTrustLineRepository runLineRepository;
    private final TreasuryTrustInterestRepository interestRepository;
    private final TreasuryCounterpartyRepository counterpartyRepository;
    private final TreasuryDealRepository dealRepository;
    private final TreasuryDealTypeService dealTypeService;
    private final TreasuryPledgeService pledgeService;
    private final TreasuryBalanceReader balanceReader;
    private final TreasuryTenantSettingService tenantSettingService;
    private final TreasuryRuleSetService ruleSetService;
    private final TreasuryLimitEngine limitEngine;
    private final TreasuryFxConfig fxConfig;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final List<EMoneySource> emoneySources;
    private final TreasuryActor actor;
    private final FromJsonHelper fromJsonHelper;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final AppUserRepositoryWrapper appUserRepository;

    // ---------- trust accounts and holdings ----------

    /** Names the institution (a counterparty) holding a TRUST account and its trustee. */
    @Transactional
    public CommandProcessingResult updateTrustAccount(final Long accountId, final JsonCommand command) {
        final JsonObject body = parse(command.json(), Set.of("bankCounterpartyId", "trusteeName", "trustDeedReference"));
        final TreasuryAccount account = trustAccount(accountId);
        final TreasuryTrustAccount trust = this.trustAccountRepository.findByTreasuryAccountId(accountId).orElseGet(() -> {
            final TreasuryTrustAccount t = new TreasuryTrustAccount();
            t.setTreasuryAccountId(account.getId());
            return t;
        });
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource("TREASURYTRUSTACCOUNT");
        final Map<String, Object> changes = new LinkedHashMap<>();
        if (body.has("bankCounterpartyId")) {
            final Long bank = this.fromJsonHelper.extractLongNamed("bankCounterpartyId", body);
            v.reset().parameter("bankCounterpartyId").value(bank).ignoreIfNull().longGreaterThanZero();
            if (bank != null && this.counterpartyRepository.findById(bank).isEmpty()) {
                v.reset().parameter("bankCounterpartyId").value(bank).failWithCode("not.found");
            }
            trust.setBankCounterpartyId(bank);
            changes.put("bankCounterpartyId", bank);
        }
        if (body.has("trusteeName")) {
            final String trustee = StringUtils.trimToNull(this.fromJsonHelper.extractStringNamed("trusteeName", body));
            v.reset().parameter("trusteeName").value(trustee).ignoreIfNull().notExceedingLengthOf(150);
            trust.setTrusteeName(trustee);
            changes.put("trusteeName", trustee);
        }
        if (body.has("trustDeedReference")) {
            final String deed = StringUtils.trimToNull(this.fromJsonHelper.extractStringNamed("trustDeedReference", body));
            v.reset().parameter("trustDeedReference").value(deed).ignoreIfNull().notExceedingLengthOf(100);
            trust.setTrustDeedReference(deed);
            changes.put("trustDeedReference", deed);
        }
        if (body.keySet().isEmpty()) {
            v.reset().parameter("bankCounterpartyId").failWithCode("one.of.the.fields.required");
        }
        throwIfAny(errors);
        trust.setLastModifiedBy(this.actor.userId(command));
        trust.setLastModifiedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.trustAccountRepository.saveAndFlush(trust);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(accountId).with(changes).build();
    }

    /** Puts a settled-or-to-settle asset deal (bill, bond, placement) under a TRUST account of its currency. */
    @Transactional
    public CommandProcessingResult createHolding(final JsonCommand command) {
        final JsonObject body = parse(command.json(), Set.of("dealId", "trustAccountId"));
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource("TREASURYTRUSTHOLDING");
        final Long dealId = this.fromJsonHelper.extractLongNamed("dealId", body);
        final Long accountId = this.fromJsonHelper.extractLongNamed("trustAccountId", body);
        v.reset().parameter("dealId").value(dealId).notNull().longGreaterThanZero();
        v.reset().parameter("trustAccountId").value(accountId).notNull().longGreaterThanZero();
        throwIfAny(errors);
        final TreasuryAccount account = trustAccount(accountId);
        final TreasuryDeal deal = this.dealRepository.findById(dealId)
                .orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "deal.not.found", "Deal " + dealId + " does not exist", dealId));
        final InstrumentKind kind = this.dealTypeService.find(deal.getDealTypeId()).getInstrumentKind();
        if (!kind.isAsset() || TrustCalculator.assetType(kind.name()) == null) {
            throw new TreasuryWorkflowException(ENTITY, "not.a.trust.asset",
                    "Deal " + dealId + " is a " + kind + "; e-money trust money is held as deposits, treasury bills and bonds", dealId,
                    kind.name());
        }
        if (!deal.getCurrencyCode().equals(account.getCurrencyCode())) {
            throw new TreasuryWorkflowException(ENTITY, "currency.mismatch", "Deal " + dealId + " is in " + deal.getCurrencyCode()
                    + ", trust account " + accountId + " in " + account.getCurrencyCode(), dealId, accountId);
        }
        if (this.holdingRepository.findByDealId(dealId).isPresent()) {
            throw new TreasuryWorkflowException(ENTITY, "already.held", "Deal " + dealId + " is already held in trust", dealId);
        }
        final TreasuryTrustHolding holding = new TreasuryTrustHolding();
        holding.setDealId(dealId);
        holding.setTrustAccountId(accountId);
        holding.setCreatedBy(this.actor.userId(command));
        holding.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.holdingRepository.saveAndFlush(holding);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(dealId)
                .with(Map.of("dealId", dealId, "trustAccountId", accountId, "assetType", TrustCalculator.assetType(kind.name()).name()))
                .build();
    }

    @Transactional
    public CommandProcessingResult deleteHolding(final Long dealId, final JsonCommand command) {
        final TreasuryTrustHolding holding = this.holdingRepository.findByDealId(dealId).orElseThrow(
                () -> new TreasuryWorkflowException(ENTITY, "holding.not.found", "Deal " + dealId + " is not held in trust", dealId));
        this.holdingRepository.delete(holding);
        this.holdingRepository.flush();
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(dealId).build();
    }

    /**
     * The e-money balances of a date (the IMPORTED source): {@code balanceDate}, {@code currencyCode} (the base by
     * default), and any of {@code customers}, {@code agents}, {@code other}; a category sent again replaces the date's
     * figure.
     */
    @Transactional
    public CommandProcessingResult importEMoney(final JsonCommand command) {
        final JsonObject body = parse(command.json(),
                Set.of("balanceDate", "currencyCode", "customers", "agents", "other", "note", "dateFormat", "locale"));
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource("TREASURYEMONEYBALANCE");
        final LocalDate date = date(body, "balanceDate", v);
        v.reset().parameter("balanceDate").value(date).notNull();
        final String currency = body.has("currencyCode")
                ? StringUtils.upperCase(this.fromJsonHelper.extractStringNamed("currencyCode", body))
                : this.fxConfig.baseCurrency();
        v.reset().parameter("currencyCode").value(currency).isOneOfTheseStringValues(this.referenceDataLookup.organisationCurrencies());
        final String note = StringUtils.trimToNull(this.fromJsonHelper.extractStringNamed("note", body));
        v.reset().parameter("note").value(note).ignoreIfNull().notExceedingLengthOf(500);
        final Map<EMoneyCategory, BigDecimal> amounts = new EnumMap<>(EMoneyCategory.class);
        for (final Map.Entry<String, EMoneyCategory> e : Map
                .of("customers", EMoneyCategory.CUSTOMER, "agents", EMoneyCategory.AGENT, "other", EMoneyCategory.OTHER).entrySet()) {
            if (body.has(e.getKey()) && !body.get(e.getKey()).isJsonNull()) {
                final BigDecimal amount = body.get(e.getKey()).getAsBigDecimal();
                v.reset().parameter(e.getKey()).value(amount).zeroOrPositiveAmount();
                amounts.put(e.getValue(), amount);
            }
        }
        if (amounts.isEmpty()) {
            v.reset().parameter("customers").failWithCode("one.of.customers.agents.other.required");
        }
        throwIfAny(errors);
        final Long userId = this.actor.userId(command);
        for (final Map.Entry<EMoneyCategory, BigDecimal> e : amounts.entrySet()) {
            final TreasuryEMoneyBalance balance = this.emoneyRepository
                    .findByBalanceDateAndCurrencyCodeAndCategory(date, currency, e.getKey()).orElseGet(TreasuryEMoneyBalance::new);
            balance.setBalanceDate(date);
            balance.setCurrencyCode(currency);
            balance.setCategory(e.getKey());
            balance.setAmount(e.getValue());
            balance.setNote(note);
            balance.setCreatedBy(userId);
            balance.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
            this.emoneyRepository.save(balance);
        }
        this.emoneyRepository.flush();
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("balanceDate", date);
        changes.put("currencyCode", currency);
        amounts.forEach((k, a) -> changes.put(k.name(), a));
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).with(changes).build();
    }

    // ---------- the reconciliation ----------

    /**
     * {@code asOfDate} (default the closed day, the business date less one) and {@code currencyCode} (default the
     * base).
     */
    @Transactional
    public CommandProcessingResult reconCommand(final JsonCommand command) {
        final JsonObject body = parse(command.json(), Set.of("asOfDate", "currencyCode", "dateFormat", "locale"));
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource("TREASURYTRUSTRECON");
        final LocalDate date = date(body, "asOfDate", v);
        final String currency = body.has("currencyCode")
                ? StringUtils.upperCase(this.fromJsonHelper.extractStringNamed("currencyCode", body))
                : this.fxConfig.baseCurrency();
        v.reset().parameter("currencyCode").value(currency).isOneOfTheseStringValues(this.referenceDataLookup.organisationCurrencies());
        throwIfAny(errors);
        final LocalDate asOf = date == null ? DateUtils.getBusinessLocalDate().minusDays(1) : date;
        if (DateUtils.isAfterBusinessDate(asOf)) {
            throw new TreasuryWorkflowException(ENTITY, "date.in.future", "The reconciliation date " + asOf + " is after the business date",
                    asOf);
        }
        final TreasuryRun run = reconcile(asOf, currency, this.actor.userId(command));
        final TreasuryRunTrust t = this.runTrustRepository.findByRunId(run.getId()).orElseThrow();
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("asOfDate", asOf);
        changes.put("currencyCode", currency);
        changes.put("emoneyTotal", t.getEmoneyTotal());
        changes.put("eligibleTotal", t.getEligibleTotal());
        changes.put("coveragePercent", t.getCoveragePercent());
        changes.put("shortfall", t.getShortfall());
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(run.getId()).with(changes).build();
    }

    /**
     * Reconciles the date: replaces the date's earlier reconciliation unless it has been submitted, stores the e-money,
     * the assets and the figures, then evaluates the trust limits (a breach notifies at once).
     */
    TreasuryRun reconcile(final LocalDate asOf, final String currency, final Long userId) {
        final List<TreasuryRun> earlier = this.runRepository.findByRunTypeAndAsOfDateAndCurrencyCode(RunType.TRUST_RECON, asOf, currency)
                .stream().filter(r -> r.getOfficeId() == null).toList();
        final Optional<TreasuryRun> submitted = earlier.stream().filter(r -> SUBMITTED.equals(r.getStatus())).findFirst();
        if (submitted.isPresent()) {
            throw new TreasuryWorkflowException(ENTITY, "locked",
                    "The trust reconciliation " + submitted.get().getId() + " of " + asOf + " has been submitted and cannot be replaced",
                    submitted.get().getId());
        }
        for (final TreasuryRun run : earlier) {
            this.runLineRepository.deleteByRunId(run.getId());
            this.runTrustRepository.deleteByRunId(run.getId());
            this.runRepository.delete(run);
        }
        this.runRepository.flush();

        final TreasuryTenantSetting setting = this.tenantSettingService.setting();
        final EMoneySourceType sourceType = TreasuryTenantSettingService.emoneySource(setting);
        final EMoneySource source = this.emoneySources.stream().filter(s -> s.type() == sourceType).findFirst().orElseThrow();
        final Map<EMoneyCategory, BigDecimal> emoney = source.outstanding(asOf, currency);
        if (sourceType == EMoneySourceType.IMPORTED && emoney.isEmpty()) {
            throw new TreasuryWorkflowException(ENTITY, "no.emoney.balance",
                    "No e-money balance has been imported for " + asOf + " in " + currency, asOf, currency);
        }
        final int scale = this.referenceDataLookup.decimalPlaces(currency);
        final List<TrustCalculator.Asset> assets = assets(asOf, currency, scale);
        final TrustCalculator.Result result = TrustCalculator.reconcile(emoney, assets, scale);

        final TreasuryRuleSet ruleSet = this.ruleSetService.defaultRuleSet();
        final TreasuryRun run = new TreasuryRun();
        run.setRunType(RunType.TRUST_RECON);
        run.setAsOfDate(asOf);
        run.setCurrencyCode(currency);
        run.setRuleSetId(ruleSet.getId());
        run.setRuleSetVersion(ruleSet.getVersion());
        run.setStatus(COMPLETED);
        run.setBeyondHorizon(BigDecimal.ZERO);
        run.setNotes("E-money from " + sourceType
                + (assets.stream().anyMatch(a -> a.type().atAnInstitution() && a.institutionId() == null)
                        ? "; a TRUST account has no institution set"
                        : ""));
        run.setCreatedBy(userId);
        run.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.runRepository.saveAndFlush(run);

        final TreasuryRunTrust t = new TreasuryRunTrust();
        t.setRunId(run.getId());
        t.setEmoneySource(sourceType.name());
        t.setEmoneyCustomers(emoney.get(EMoneyCategory.CUSTOMER));
        t.setEmoneyAgents(emoney.get(EMoneyCategory.AGENT));
        t.setEmoneyOther(emoney.get(EMoneyCategory.OTHER));
        t.setEmoneyTotal(result.emoney());
        t.setTrustTotal(result.trustTotal());
        t.setEligibleTotal(result.eligible());
        t.setEncumberedTotal(result.encumbered());
        t.setIneligibleTotal(result.ineligible());
        t.setDifference(result.difference());
        t.setShortfall(result.shortfall());
        t.setCoveragePercent(result.coveragePercent());
        t.setMaxConcentrationPercent(result.maxConcentrationPercent());
        t.setConcentrationThreshold(TrustCalculator.CONCENTRATION_THRESHOLD);
        t.setConcentrationApplies(result.concentrationApplies());
        this.runTrustRepository.save(t);
        for (final Map.Entry<EMoneyCategory, BigDecimal> e : emoney.entrySet()) {
            final TreasuryRunTrustLine line = new TreasuryRunTrustLine();
            line.setRunId(run.getId());
            line.setLineType("EMONEY");
            line.setCategory(e.getKey());
            line.setLabel("E-money of " + e.getKey().name().toLowerCase() + "s");
            line.setAmount(e.getValue());
            this.runLineRepository.save(line);
        }
        for (final TrustCalculator.Asset a : assets) {
            final TreasuryRunTrustLine line = new TreasuryRunTrustLine();
            line.setRunId(run.getId());
            line.setLineType("ASSET");
            line.setAssetType(a.type());
            line.setInstitutionId(a.institutionId());
            line.setInstitutionName(a.institutionName());
            line.setTrustAccountId(a.trustAccountId());
            line.setDealId(a.dealId());
            line.setLabel(StringUtils.abbreviate(a.label(), 200));
            line.setAmount(a.amount());
            line.setEligible(a.eligible());
            line.setEncumbered(a.encumbered());
            this.runLineRepository.save(line);
        }
        this.runLineRepository.flush();
        this.limitEngine.afterRun(run, null, userId);
        return run;
    }

    /**
     * The trust assets of the date: TRUST accounts at their GL balance, and the deals held in trust at carrying value.
     */
    List<TrustCalculator.Asset> assets(final LocalDate asOf, final String currency, final int scale) {
        final List<TrustCalculator.Asset> assets = new ArrayList<>();
        final List<TreasuryAccount> accounts = this.accountRepository.findByStatusOrderByIdAsc(TreasuryAccountStatus.ACTIVE).stream()
                .filter(a -> a.getAccountType() == TreasuryAccountType.TRUST && a.getCurrencyCode().equals(currency)).toList();
        final Map<Long, BigDecimal> balances = this.balanceReader.glBalances(accounts.stream().map(TreasuryAccount::getId).toList(), asOf);
        final Map<Long, TreasuryTrustAccount> details = this.trustAccountRepository.findAll().stream()
                .collect(Collectors.toMap(TreasuryTrustAccount::getTreasuryAccountId, t -> t));
        for (final TreasuryAccount account : accounts) {
            final Long bank = Optional.ofNullable(details.get(account.getId())).map(TreasuryTrustAccount::getBankCounterpartyId)
                    .orElse(null);
            assets.add(new TrustCalculator.Asset(TrustAssetType.DEMAND_DEPOSIT, bank, counterpartyName(bank), account.getId(), null,
                    account.getCode() + " " + account.getName(), balances.getOrDefault(account.getId(), BigDecimal.ZERO), BigDecimal.ZERO));
        }
        final Map<Long, Long> trustAccountOf = this.holdingRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(TreasuryTrustHolding::getDealId, TreasuryTrustHolding::getTrustAccountId));
        final List<TreasuryDeal> deals = trustAccountOf.isEmpty() ? List.of()
                : this.dealRepository.findAllById(trustAccountOf.keySet()).stream()
                        .filter(d -> d.getStatus() == DealStatus.SETTLED && d.getCurrencyCode().equals(currency)
                                && !d.getValueDate().isAfter(asOf) && d.getMaturityDate().isAfter(asOf))
                        .sorted(java.util.Comparator.comparing(TreasuryDeal::getId)).toList();
        final Map<Long, BigDecimal> pledged = this.pledgeService.encumberedAmounts(deals.stream().map(TreasuryDeal::getId).toList(), asOf);
        for (final TreasuryDeal deal : deals) {
            final InstrumentKind kind = this.dealTypeService.find(deal.getDealTypeId()).getInstrumentKind();
            final TrustAssetType type = TrustCalculator.assetType(kind.name());
            if (type == null) {
                continue;
            }
            final BigDecimal value = DealLifecyclePlanner.carrying(deal, kind).assetTotal().add(deal.getFairValueAdjustment())
                    .subtract(deal.getClassification() == DealClassification.AMORTISED_COST ? deal.getEclAllowance() : BigDecimal.ZERO)
                    .setScale(scale, RoundingMode.HALF_UP);
            final BigDecimal outstanding = TreasuryPledgeService.outstanding(deal);
            final BigDecimal encumberedNominal = pledged.getOrDefault(deal.getId(), BigDecimal.ZERO);
            final BigDecimal encumbered = outstanding.signum() == 0 ? BigDecimal.ZERO
                    : value.multiply(encumberedNominal.min(outstanding)).divide(outstanding, scale, RoundingMode.HALF_UP);
            final Long institution = type.atAnInstitution() ? deal.getCounterpartyId() : null;
            assets.add(new TrustCalculator.Asset(type, institution,
                    type.atAnInstitution() ? counterpartyName(institution) : "Government of Uganda", trustAccountOf.get(deal.getId()),
                    deal.getId(),
                    "Deal " + deal.getId() + " " + kind + (deal.getExternalReference() == null ? "" : " " + deal.getExternalReference()),
                    value, encumbered));
        }
        return assets;
    }

    @Transactional
    public CommandProcessingResult submit(final Long runId, final JsonCommand command) {
        final TreasuryRun run = reconRun(runId);
        if (!COMPLETED.equals(run.getStatus())) {
            throw new TreasuryWorkflowException(ENTITY, "not.submittable", "Trust reconciliation " + runId + " is " + run.getStatus(),
                    runId);
        }
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(ENTITY, runId, "made", run.getCreatedBy(), "submit", userId);
        run.setStatus(SUBMITTED);
        run.setSubmittedBy(userId);
        run.setSubmittedOnDate(DateUtils.getBusinessLocalDate());
        this.runRepository.saveAndFlush(run);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(runId).with(Map.of("status", SUBMITTED))
                .build();
    }

    /**
     * The job: the closed day (the business date less one) in every currency with a TRUST account: interest, then the
     * reconciliation.
     */
    public int runClosedDay() {
        final LocalDate asOf = DateUtils.getBusinessLocalDate().minusDays(1);
        final TransactionTemplate template = new TransactionTemplate(this.transactionManager);
        // each currency in its own transaction, so that one that fails is rolled back alone and not the job's
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        final Long systemUserId = template.execute(status -> this.appUserRepository.fetchSystemUser().getId());
        final List<String> currencies = template.execute(status -> this.accountRepository
                .findByStatusOrderByIdAsc(TreasuryAccountStatus.ACTIVE).stream()
                .filter(a -> a.getAccountType() == TreasuryAccountType.TRUST).map(TreasuryAccount::getCurrencyCode).distinct().toList());
        int runs = 0;
        for (final String currency : currencies == null ? List.<String>of() : currencies) {
            try {
                template.execute(status -> {
                    accrueInterest(asOf, currency);
                    return reconcile(asOf, currency, systemUserId);
                });
                runs++;
            } catch (RuntimeException e) {
                log.error("Treasury trust reconciliation of {} on {} failed: {}", currency, asOf, e.getMessage());
            }
        }
        log.info("Treasury trust reconciliation for {}: {} currencies", asOf, runs);
        return runs;
    }

    // ---------- interest ----------

    /**
     * The interest earned on the trust on the date: the accruals posted that day for deals held in trust, and the
     * credits to the trust interest GL account. Replaces the date's earlier record.
     */
    @Transactional
    public int accrueInterest(final LocalDate date, final String currency) {
        this.interestRepository.deleteByInterestDateAndCurrencyCode(date, currency);
        this.interestRepository.flush();
        int rows = 0;
        final List<Long> deals = this.holdingRepository.findAllByOrderByIdAsc().stream().map(TreasuryTrustHolding::getDealId).toList();
        if (!deals.isEmpty()) {
            final List<Map<String, Object>> accruals = this.jdbcTemplate.queryForList("""
                    SELECT a.deal_id, SUM(a.interest) AS interest FROM m_treasury_deal_accrual a JOIN m_treasury_deal d ON d.id = a.deal_id
                    WHERE a.deal_id IN (:deals) AND a.to_date = :date AND d.currency_code = :currency GROUP BY a.deal_id
                    """, new MapSqlParameterSource("deals", deals).addValue("date", date).addValue("currency", currency));
            for (final Map<String, Object> a : accruals) {
                rows += record(date, currency, SOURCE_DEAL, ((Number) a.get("deal_id")).longValue(), null, (BigDecimal) a.get("interest"));
            }
        }
        final Long gl = this.tenantSettingService.setting().getTrustInterestGlAccountId();
        if (gl != null) {
            final BigDecimal credited = this.jdbcTemplate.queryForObject("""
                    SELECT COALESCE(SUM(CASE WHEN type_enum = 1 THEN amount ELSE -amount END), 0) FROM acc_gl_journal_entry
                    WHERE account_id = :gl AND currency_code = :currency AND entry_date = :date
                    """, new MapSqlParameterSource("gl", gl).addValue("currency", currency).addValue("date", date), BigDecimal.class);
            rows += record(date, currency, SOURCE_BANK, null, gl, credited);
        }
        return rows;
    }

    private int record(final LocalDate date, final String currency, final String source, final Long dealId, final Long gl,
            final BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            return 0;
        }
        final TreasuryTrustInterest row = new TreasuryTrustInterest();
        row.setInterestDate(date);
        row.setCurrencyCode(currency);
        row.setSource(source);
        row.setDealId(dealId);
        row.setGlAccountId(gl);
        row.setAmount(amount);
        row.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.interestRepository.save(row);
        return 1;
    }

    /**
     * The interest earned on the trust over the period (default the calendar quarter of the business date), the average
     * trust balance of the period's reconciliations and the annual rate it makes.
     */
    @Transactional(readOnly = true)
    public TreasuryTrustData.Interest interest(final LocalDate from, final LocalDate to, final String currencyCode) {
        final String currency = currencyCode == null ? this.fxConfig.baseCurrency() : currencyCode.toUpperCase();
        final LocalDate today = DateUtils.getBusinessLocalDate();
        final LocalDate start = from != null ? from : today.withDayOfMonth(1).withMonth((today.get(IsoFields.QUARTER_OF_YEAR) - 1) * 3 + 1);
        final LocalDate end = to != null ? to : start.plusMonths(3).minusDays(1);
        final List<TreasuryTrustData.InterestRow> rows = this.interestRepository
                .findByCurrencyCodeAndInterestDateBetweenOrderByInterestDateAscIdAsc(currency, start, end).stream()
                .map(r -> new TreasuryTrustData.InterestRow(r.getInterestDate(), r.getSource(), r.getDealId(), r.getGlAccountId(),
                        r.getAmount()))
                .toList();
        final BigDecimal accrued = rows.stream().map(TreasuryTrustData.InterestRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        final Map<String, BigDecimal> bySource = new LinkedHashMap<>();
        rows.forEach(r -> bySource.merge(r.source(), r.amount(), BigDecimal::add));
        final BigDecimal average = this.jdbcTemplate.queryForObject(
                """
                        SELECT AVG(t.trust_total) FROM m_treasury_run_trust t JOIN m_treasury_run r ON r.id = t.run_id
                        WHERE r.run_type = 'TRUST_RECON' AND r.office_id IS NULL AND r.currency_code = :currency AND r.as_of_date BETWEEN :from AND :to
                        """,
                new MapSqlParameterSource("currency", currency).addValue("from", start).addValue("to", end), BigDecimal.class);
        final int days = (int) ChronoUnit.DAYS.between(start, end) + 1;
        final BigDecimal rate = average == null || average.signum() == 0 ? null
                : accrued.multiply(new BigDecimal("36500")).divide(average.multiply(BigDecimal.valueOf(days)), 4, RoundingMode.HALF_UP);
        return new TreasuryTrustData.Interest(start, end, currency, accrued, bySource,
                average == null ? null : average.setScale(2, RoundingMode.HALF_UP), rate, days, rows,
                "Interest accrued on the trust is due to the e-money holders at the end of each calendar quarter (NPS Regulations 2021 reg 14(2)); "
                        + "allocating it to customers and paying it out is done on the wallet platform");
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public List<TreasuryTrustData.Account> accounts() {
        final List<TreasuryAccount> accounts = this.accountRepository.findAll().stream()
                .filter(a -> a.getAccountType() == TreasuryAccountType.TRUST).sorted(java.util.Comparator.comparing(TreasuryAccount::getId))
                .toList();
        final Map<Long, BigDecimal> balances = this.balanceReader.glBalances(accounts.stream().map(TreasuryAccount::getId).toList(),
                DateUtils.getBusinessLocalDate());
        final List<TreasuryTrustData.Account> data = new ArrayList<>();
        for (final TreasuryAccount a : accounts) {
            final Optional<TreasuryTrustAccount> t = this.trustAccountRepository.findByTreasuryAccountId(a.getId());
            final Long bank = t.map(TreasuryTrustAccount::getBankCounterpartyId).orElse(null);
            data.add(new TreasuryTrustData.Account(a.getId(), a.getCode(), a.getName(), a.getCurrencyCode(), a.getOfficeId(),
                    a.getGlAccountId(), bank, counterpartyName(bank), t.map(TreasuryTrustAccount::getTrusteeName).orElse(null),
                    t.map(TreasuryTrustAccount::getTrustDeedReference).orElse(null), balances.get(a.getId())));
        }
        return data;
    }

    @Transactional(readOnly = true)
    public List<TreasuryTrustData.Holding> holdings() {
        final List<TreasuryTrustData.Holding> data = new ArrayList<>();
        for (final TreasuryTrustHolding h : this.holdingRepository.findAllByOrderByIdAsc()) {
            this.dealRepository.findById(h.getDealId()).ifPresent(d -> {
                final InstrumentKind kind = this.dealTypeService.find(d.getDealTypeId()).getInstrumentKind();
                data.add(new TreasuryTrustData.Holding(d.getId(), h.getTrustAccountId(), kind.name(),
                        Objects.toString(TrustCalculator.assetType(kind.name()), null), d.getCounterpartyId(),
                        counterpartyName(d.getCounterpartyId()), d.getCurrencyCode(), d.getAmount(), d.getStatus().name()));
            });
        }
        return data;
    }

    @Transactional(readOnly = true)
    public List<TreasuryTrustData.EMoneyBalance> emoneyBalances(final LocalDate from, final LocalDate to, final String currencyCode) {
        final String currency = currencyCode == null ? this.fxConfig.baseCurrency() : currencyCode.toUpperCase();
        final LocalDate end = to == null ? DateUtils.getBusinessLocalDate() : to;
        final LocalDate start = from == null ? end.minusDays(30) : from;
        return this.emoneyRepository.findByCurrencyCodeAndBalanceDateBetweenOrderByBalanceDateAscCategoryAsc(currency, start, end).stream()
                .map(b -> new TreasuryTrustData.EMoneyBalance(b.getId(), b.getBalanceDate(), b.getCurrencyCode(), b.getCategory().name(),
                        b.getAmount(), b.getNote()))
                .toList();
    }

    /**
     * The institution-wide reconciliations of the currency between the dates (default the last 30 days), newest first.
     */
    @Transactional(readOnly = true)
    public List<TreasuryTrustData.ReconciliationSummary> reconciliations(final LocalDate from, final LocalDate to,
            final String currencyCode) {
        final String currency = currencyCode == null ? this.fxConfig.baseCurrency() : currencyCode.toUpperCase();
        final LocalDate end = to == null ? DateUtils.getBusinessLocalDate() : to;
        final LocalDate start = from == null ? end.minusDays(30) : from;
        if (start.isAfter(end)) {
            throw new TreasuryWorkflowException(ENTITY, "invalid.period", "The period from " + start + " to " + end + " is empty", start,
                    end);
        }
        return this.runRepository
                .findByRunTypeAndCurrencyCodeAndAsOfDateBetweenOrderByAsOfDateDescIdDesc(RunType.TRUST_RECON, currency, start, end).stream()
                .filter(r -> r.getOfficeId() == null)
                .flatMap(r -> this.runTrustRepository.findByRunId(r.getId()).stream()
                        .map(t -> new TreasuryTrustData.ReconciliationSummary(r.getId(), r.getAsOfDate(), r.getCurrencyCode(),
                                r.getStatus(), t.getEmoneySource(), t.getEmoneyTotal(), t.getTrustTotal(), t.getEligibleTotal(),
                                t.getDifference(), t.getShortfall(), t.getCoveragePercent(), r.getCreatedBy(), r.getSubmittedBy(),
                                r.getSubmittedOnDate())))
                .toList();
    }

    /** The stored statement of a reconciliation, as it was computed. */
    @Transactional(readOnly = true)
    public TreasuryTrustData.Statement statement(final Long runId) {
        final TreasuryRun run = reconRun(runId);
        final TreasuryRunTrust t = this.runTrustRepository.findByRunId(runId).orElseThrow();
        final List<TreasuryRunTrustLine> lines = this.runLineRepository.findByRunIdOrderByIdAsc(runId);
        final Map<String, BigDecimal> emoney = new LinkedHashMap<>();
        final List<TreasuryTrustData.Asset> assets = new ArrayList<>();
        final Map<Long, BigDecimal> byInstitution = new LinkedHashMap<>();
        final Map<Long, String> names = new LinkedHashMap<>();
        for (final TreasuryRunTrustLine l : lines) {
            if ("EMONEY".equals(l.getLineType())) {
                emoney.put(l.getCategory().name(), l.getAmount());
            } else {
                assets.add(new TreasuryTrustData.Asset(l.getAssetType().name(), l.getInstitutionId(), l.getInstitutionName(),
                        l.getTrustAccountId(), l.getDealId(), l.getLabel(), l.getAmount(), l.getEligible(), l.getEncumbered()));
                if (l.getAssetType().atAnInstitution()) {
                    final Long id = l.getInstitutionId() == null ? 0L : l.getInstitutionId();
                    byInstitution.merge(id, l.getAmount(), BigDecimal::add);
                    names.putIfAbsent(id, l.getInstitutionName());
                }
            }
        }
        final List<TreasuryTrustData.Institution> institutions = byInstitution.entrySet().stream()
                .map(e -> new TreasuryTrustData.Institution(e.getKey() == 0L ? null : e.getKey(), names.get(e.getKey()), e.getValue(),
                        TrustCalculator.percent(e.getValue(), t.getEmoneyTotal())))
                .toList();
        return new TreasuryTrustData.Statement(runId, run.getAsOfDate(), run.getCurrencyCode(), run.getStatus(), t.getEmoneySource(),
                emoney, t.getEmoneyTotal(), t.getTrustTotal(), t.getEligibleTotal(), t.getEncumberedTotal(), t.getIneligibleTotal(),
                t.getDifference(), t.getShortfall(), t.getCoveragePercent(), t.isConcentrationApplies(), t.getConcentrationThreshold(),
                t.getMaxConcentrationPercent(), institutions, assets, run.getCreatedBy(), run.getCreatedOnUtc(), run.getSubmittedBy(),
                run.getSubmittedOnDate());
    }

    /** The statement as CSV: the figures, then the e-money, the institutions and the assets. */
    public static String csv(final TreasuryTrustData.Statement s) {
        final StringBuilder out = new StringBuilder("section,item,institution,asset_type,amount,eligible,encumbered,percent_of_emoney\n");
        row(out, "summary", "as_of_date " + s.asOfDate() + " currency " + s.currencyCode() + " status " + s.status() + " run " + s.runId(),
                null, null, null, null, null, null);
        row(out, "summary", "emoney_total (" + s.emoneySource() + ")", null, null, s.emoneyTotal(), null, null, null);
        row(out, "summary", "trust_total", null, null, s.trustTotal(), s.eligibleTotal(), s.encumberedTotal(), s.coveragePercent());
        row(out, "summary", "difference", null, null, s.difference(), null, null, null);
        row(out, "summary", "shortfall", null, null, s.shortfall(), null, null, null);
        row(out, "summary",
                "max_institution_concentration (applies above " + plain(s.concentrationThreshold()) + ": " + s.concentrationApplies() + ")",
                null, null, null, null, null, s.maxConcentrationPercent());
        s.emoney().forEach((k, v) -> row(out, "emoney", k, null, null, v, null, null, null));
        s.institutions().forEach(
                i -> row(out, "institution", i.institutionName(), i.institutionName(), null, i.amount(), null, null, i.percentOfEmoney()));
        s.assets().forEach(
                a -> row(out, "asset", a.label(), a.institutionName(), a.assetType(), a.amount(), a.eligible(), a.encumbered(), null));
        return out.toString();
    }

    /** The trust balances by asset type and institution, from the date's reconciliation (reg 22(6)). */
    @Transactional(readOnly = true)
    public TreasuryTrustData.Breakdown breakdown(final LocalDate asOfDate, final String currencyCode) {
        final String currency = currencyCode == null ? this.fxConfig.baseCurrency() : currencyCode.toUpperCase();
        final LocalDate asOf = asOfDate == null ? DateUtils.getBusinessLocalDate().minusDays(1) : asOfDate;
        final Optional<TreasuryRun> run = this.runRepository.findByRunTypeAndAsOfDateAndCurrencyCode(RunType.TRUST_RECON, asOf, currency)
                .stream().filter(r -> r.getOfficeId() == null).max(java.util.Comparator.comparing(TreasuryRun::getId));
        if (run.isEmpty()) {
            throw new TreasuryWorkflowException(ENTITY, "no.reconciliation", "No trust reconciliation of " + asOf + " in " + currency,
                    asOf);
        }
        final Map<String, BigDecimal> byType = new LinkedHashMap<>();
        final Map<String, TreasuryTrustData.BreakdownRow> rows = new LinkedHashMap<>();
        for (final TreasuryRunTrustLine l : this.runLineRepository.findByRunIdOrderByIdAsc(run.get().getId())) {
            if (!"ASSET".equals(l.getLineType())) {
                continue;
            }
            byType.merge(l.getAssetType().name(), l.getAmount(), BigDecimal::add);
            final String key = l.getInstitutionId() + "|" + l.getInstitutionName() + "|" + l.getAssetType();
            final TreasuryTrustData.BreakdownRow before = rows.get(key);
            rows.put(key,
                    new TreasuryTrustData.BreakdownRow(l.getInstitutionId(), l.getInstitutionName(), l.getAssetType().name(),
                            (before == null ? BigDecimal.ZERO : before.amount()).add(l.getAmount()),
                            (before == null ? BigDecimal.ZERO : before.eligible())
                                    .add(l.getEligible() == null ? BigDecimal.ZERO : l.getEligible())));
        }
        return new TreasuryTrustData.Breakdown(asOf, currency, run.get().getId(), byType, List.copyOf(rows.values()));
    }

    // ---------- helpers ----------

    private TreasuryRun reconRun(final Long runId) {
        final TreasuryRun run = this.runRepository.findById(runId)
                .orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "run.not.found", "Run " + runId + " does not exist", runId));
        if (run.getRunType() != RunType.TRUST_RECON) {
            throw new TreasuryWorkflowException(ENTITY, "not.a.trust.reconciliation", "Run " + runId + " is a " + run.getRunType() + " run",
                    runId);
        }
        return run;
    }

    private TreasuryAccount trustAccount(final Long accountId) {
        final TreasuryAccount account = this.accountRepository.findById(accountId).orElseThrow(() -> new TreasuryWorkflowException(ENTITY,
                "account.not.found", "Treasury account " + accountId + " does not exist", accountId));
        if (account.getAccountType() != TreasuryAccountType.TRUST) {
            throw new TreasuryWorkflowException(ENTITY, "not.a.trust.account",
                    "Treasury account " + accountId + " is a " + account.getAccountType() + " account, not TRUST", accountId);
        }
        return account;
    }

    private String counterpartyName(final Long id) {
        return id == null ? null : this.counterpartyRepository.findById(id).map(TreasuryCounterparty::getName).orElse(null);
    }

    private JsonObject parse(final String json, final Set<String> supported) {
        final JsonElement element = this.fromJsonHelper.parse(StringUtils.isBlank(json) ? "{}" : json);
        this.fromJsonHelper.checkForUnsupportedParameters(element.getAsJsonObject(), supported);
        return element.getAsJsonObject();
    }

    private LocalDate date(final JsonObject body, final String name, final DataValidatorBuilder v) {
        if (!body.has(name) || body.get(name).isJsonNull()) {
            return null;
        }
        try {
            return LocalDate.parse(body.get(name).getAsString());
        } catch (RuntimeException e) {
            v.reset().parameter(name).value(body.get(name).toString()).failWithCode("invalid.date", "Dates are yyyy-MM-dd");
            return null;
        }
    }

    private static void throwIfAny(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }

    private static void row(final StringBuilder out, final String section, final String item, final String institution,
            final String assetType, final BigDecimal amount, final BigDecimal eligible, final BigDecimal encumbered,
            final BigDecimal percent) {
        out.append(String.join(",", section, quote(item), quote(institution), quote(assetType), plain(amount), plain(eligible),
                plain(encumbered), plain(percent))).append('\n');
    }

    private static String quote(final String value) {
        return value == null ? "" : "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String plain(final BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}
