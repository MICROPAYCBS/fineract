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
import java.math.RoundingMode;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryConsolidatedLadderData;
import org.apache.fineract.treasury.data.TreasuryCoreCapitalData;
import org.apache.fineract.treasury.data.TreasuryFxPositionData;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.domain.TreasuryCoreCapital;
import org.apache.fineract.treasury.domain.TreasuryCoreCapitalRepository;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunLine;
import org.apache.fineract.treasury.domain.TreasuryRunLineRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
import org.apache.fineract.treasury.fx.FxRates;
import org.apache.fineract.treasury.fx.NetOpenPosition;
import org.apache.fineract.treasury.fx.NopCapitalBasis;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.liquidity.RunType;
import org.apache.fineract.treasury.serialization.TreasuryFxDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryFxDataValidator.CoreCapitalRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Currency positions and the net open position (NOP), core capital, and the ladder consolidated in the base currency.
 *
 * <p>
 * A currency's position is its GL balances on the date: assets (debit less credit of asset accounts) less liabilities
 * (credit less debit of liability accounts), in the currency, and at the MID rate in effect in base (flagged when the
 * rate is older than the maximum age). Transfers pending between treasury accounts and approved or confirmed deals not
 * yet settled are shown beside it, not added: neither changes the currency's net position once it is in the GL. The NOP
 * of a currency is its net base position, long or short, as a percentage of core capital; the aggregate is the greater
 * of the sum of the longs and the sum of the shorts (the shorthand method).
 * </p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryFxPositionService {

    static final String ENTITY = "fx.position";

    private static final String GL_SQL = """
            SELECT je.currency_code, g.classification_enum, SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END) AS dc
            FROM acc_gl_journal_entry je JOIN acc_gl_account g ON g.id = je.account_id
            WHERE je.entry_date <= :asOf AND g.classification_enum IN (1, 2) AND (:officeId IS NULL OR je.office_id = :officeId)
            GROUP BY je.currency_code, g.classification_enum
            """;
    private static final String DEALS_SQL = """
            SELECT d.currency_code, SUM(CASE WHEN t.direction = 'ASSET' THEN d.amount ELSE -d.amount END) AS amount
            FROM m_treasury_deal d JOIN m_treasury_deal_type t ON t.id = d.deal_type_id
            WHERE d.status IN ('APPROVED', 'CONFIRMED') AND (:officeId IS NULL OR d.office_id = :officeId)
            GROUP BY d.currency_code
            """;

    private final TreasuryFxDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryFxRateService rateService;
    private final TreasuryFxConfig fxConfig;
    private final TreasuryCoreCapitalRepository capitalRepository;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final TreasuryAccountRepository accountRepository;
    private final TreasuryBalanceReader balanceReader;
    private final TreasuryRunRepository runRepository;
    private final TreasuryRunLineRepository runLineRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public TreasuryFxPositionData positions(final LocalDate asOfDate, final Long officeId) {
        final LocalDate asOf = asOfDate == null ? DateUtils.getBusinessLocalDate() : asOfDate;
        final String base = this.fxConfig.baseCurrency();
        final MapSqlParameterSource params = new MapSqlParameterSource("asOf", asOf).addValue("officeId", officeId, Types.BIGINT);
        final Map<String, BigDecimal[]> gl = new HashMap<>();
        this.jdbcTemplate.query(GL_SQL, params, rs -> {
            final BigDecimal[] row = gl.computeIfAbsent(rs.getString("currency_code"),
                    c -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
            if (rs.getInt("classification_enum") == 1) {
                row[0] = row[0].add(rs.getBigDecimal("dc"));
            } else {
                row[1] = row[1].add(rs.getBigDecimal("dc").negate());
            }
        });
        final Map<String, BigDecimal> deals = new HashMap<>();
        this.jdbcTemplate.query(DEALS_SQL, params, rs -> {
            deals.put(rs.getString("currency_code"), rs.getBigDecimal("amount"));
        });
        final List<TreasuryAccount> accounts = this.accountRepository.findByStatusOrderByIdAsc(TreasuryAccountStatus.ACTIVE).stream()
                .filter(a -> officeId == null || officeId.equals(a.getOfficeId())).toList();
        final Map<Long, TreasuryBalanceReader.Pending> pending = this.balanceReader
                .pending(accounts.stream().map(TreasuryAccount::getId).toList(), asOf);
        final TreeSet<String> currencies = new TreeSet<>(this.referenceDataLookup.organisationCurrencies());
        currencies.addAll(gl.keySet());
        // FI (Foreign Exchange Business) Rules 2010 r.6(1): core capital as at the preceding quarter, unless the tenant
        // chose LATEST
        final NopCapitalBasis basis = this.fxConfig.nopCapitalBasis();
        final LocalDate capitalAsOf = basis.capitalDate(asOf);
        final Optional<TreasuryCoreCapital> capital = nopCoreCapital(asOf);
        final BigDecimal coreCapital = capital.map(TreasuryCoreCapital::getAmount).orElse(null);
        final int scale = this.referenceDataLookup.decimalPlaces(base);
        final List<TreasuryFxPositionData.Currency> rows = new ArrayList<>();
        final List<BigDecimal> foreign = new ArrayList<>();
        for (final String currency : currencies) {
            final BigDecimal[] balances = gl.getOrDefault(currency, new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
            final BigDecimal net = balances[0].subtract(balances[1]);
            BigDecimal pendingIn = BigDecimal.ZERO;
            BigDecimal pendingOut = BigDecimal.ZERO;
            for (final TreasuryAccount account : accounts) {
                if (account.getCurrencyCode().equals(currency)) {
                    final TreasuryBalanceReader.Pending p = pending.getOrDefault(account.getId(), TreasuryBalanceReader.Pending.NONE);
                    pendingIn = pendingIn.add(p.in());
                    pendingOut = pendingOut.add(p.out());
                }
            }
            final boolean isBase = currency.equals(base);
            final Optional<FxRates.Rate> rate = isBase ? Optional.empty() : this.rateService.mid(currency, asOf);
            final BigDecimal mid = isBase ? BigDecimal.ONE : rate.map(FxRates.Rate::rate).orElse(null);
            final BigDecimal netBase = mid == null ? null : net.multiply(mid).setScale(scale, RoundingMode.HALF_UP);
            if (!isBase) {
                foreign.add(netBase);
            }
            rows.add(new TreasuryFxPositionData.Currency(currency, isBase, balances[0], balances[1], net, pendingIn, pendingOut,
                    deals.getOrDefault(currency, BigDecimal.ZERO), mid, rate.map(FxRates.Rate::rateDate).orElse(null),
                    rate.map(r -> FxRates.isStale(r, asOf, this.fxConfig.maxRateAgeDays())).orElse(false), netBase,
                    netBase == null ? null : netBase.signum() > 0 ? "LONG" : netBase.signum() < 0 ? "SHORT" : "FLAT",
                    isBase ? null : NetOpenPosition.single(netBase, coreCapital)));
        }
        final NetOpenPosition.Aggregate aggregate = NetOpenPosition.aggregate(foreign, coreCapital);
        return new TreasuryFxPositionData(asOf, base, officeId, coreCapital,
                capital.map(TreasuryCoreCapital::getEffectiveDate).orElse(null), rows, aggregate.longs(), aggregate.shorts(),
                aggregate.nop(), aggregate.percent(), basis.name(), capitalAsOf);
    }

    // ---------- core capital ----------

    @Transactional
    public CommandProcessingResult createCoreCapital(final JsonCommand command) {
        final CoreCapitalRequest request = this.validator.validateCoreCapital(command.json());
        final TreasuryCoreCapital capital = new TreasuryCoreCapital();
        capital.setEffectiveDate(request.effectiveDate());
        capital.setAmount(request.amount());
        capital.setCurrencyCode(this.fxConfig.baseCurrency());
        capital.setNote(request.note());
        capital.setCreatedBy(this.actor.userId(command));
        capital.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.capitalRepository.saveAndFlush(capital);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("effectiveDate", capital.getEffectiveDate());
        changes.put("amount", capital.getAmount());
        changes.put("currencyCode", capital.getCurrencyCode());
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(capital.getId()).with(changes).build();
    }

    /** The core capital the net open position of the date is measured against, by the tenant's NOP capital basis. */
    public Optional<TreasuryCoreCapital> nopCoreCapital(final LocalDate asOf) {
        return coreCapital(this.fxConfig.nopCapitalBasis().capitalDate(asOf));
    }

    /** The core capital in effect: the latest from an effective date on or before the date. */
    public Optional<TreasuryCoreCapital> coreCapital(final LocalDate asOf) {
        return this.capitalRepository.findAllByOrderByEffectiveDateAscIdAsc().stream().filter(c -> !c.getEffectiveDate().isAfter(asOf))
                .reduce((a, b) -> b);
    }

    @Transactional(readOnly = true)
    public List<TreasuryCoreCapitalData> retrieveCoreCapital() {
        return this.capitalRepository.findAllByOrderByEffectiveDateAscIdAsc().stream().map(c -> new TreasuryCoreCapitalData(c.getId(),
                c.getEffectiveDate(), c.getAmount(), c.getCurrencyCode(), c.getNote(), c.getCreatedBy(), c.getCreatedOnUtc())).toList();
    }

    // ---------- consolidated ladder ----------

    /**
     * Each currency's latest ladder run of the date and scope, its bucket totals at the MID rate in effect, summed by
     * bucket code.
     */
    @Transactional(readOnly = true)
    public TreasuryConsolidatedLadderData consolidatedLadder(final LocalDate asOfDate, final Long officeId) {
        final LocalDate asOf = asOfDate == null ? DateUtils.getBusinessLocalDate() : asOfDate;
        final String base = this.fxConfig.baseCurrency();
        final int scale = this.referenceDataLookup.decimalPlaces(base);
        final List<TreasuryConsolidatedLadderData.CurrencyRun> currencies = new ArrayList<>();
        final Map<String, BigDecimal[]> buckets = new LinkedHashMap<>();
        final Map<String, Integer> seqs = new HashMap<>();
        for (final String currency : this.referenceDataLookup.organisationCurrencies()) {
            final Optional<TreasuryRun> run = this.runRepository.findByRunTypeAndAsOfDateAndCurrencyCode(RunType.LADDER, asOf, currency)
                    .stream().filter(r -> Objects.equals(r.getOfficeId(), officeId)).max(Comparator.comparing(TreasuryRun::getId));
            final Optional<FxRates.Rate> rate = currency.equals(base) ? Optional.empty() : this.rateService.mid(currency, asOf);
            final BigDecimal mid = currency.equals(base) ? BigDecimal.ONE : rate.map(FxRates.Rate::rate).orElse(null);
            if (run.isEmpty() || mid == null) {
                currencies.add(new TreasuryConsolidatedLadderData.CurrencyRun(currency, run.map(TreasuryRun::getId).orElse(null), mid,
                        rate.map(FxRates.Rate::rateDate).orElse(null), run.isEmpty() ? "No ladder run of the date" : "No MID rate"));
                continue;
            }
            currencies.add(new TreasuryConsolidatedLadderData.CurrencyRun(currency, run.get().getId(), mid,
                    rate.map(FxRates.Rate::rateDate).orElse(null), null));
            for (final TreasuryRunLine line : this.runLineRepository.findByRunIdOrderByBucketSeqAscIdAsc(run.get().getId())) {
                if (line.getSource() != LadderSourceCode.TOTAL) {
                    continue;
                }
                seqs.merge(line.getBucketCode(), line.getBucketSeq(), Math::min);
                final BigDecimal[] totals = buckets.computeIfAbsent(line.getBucketCode(),
                        c -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
                totals[0] = totals[0].add(line.getInflow().multiply(mid).setScale(scale, RoundingMode.HALF_UP));
                totals[1] = totals[1].add(line.getOutflow().multiply(mid).setScale(scale, RoundingMode.HALF_UP));
            }
        }
        final List<TreasuryConsolidatedLadderData.Bucket> rows = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        for (final String code : buckets.keySet().stream().sorted(Comparator.comparing(seqs::get)).toList()) {
            final BigDecimal[] totals = buckets.get(code);
            final BigDecimal net = totals[0].subtract(totals[1]);
            cumulative = cumulative.add(net);
            rows.add(new TreasuryConsolidatedLadderData.Bucket(seqs.get(code), code, totals[0], totals[1], net, cumulative));
        }
        return new TreasuryConsolidatedLadderData(asOf, base, officeId, currencies, rows);
    }
}
