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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.domain.TreasuryAssetClassRule;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.domain.TreasuryRatioComponent;
import org.apache.fineract.treasury.domain.TreasuryRunLine;
import org.apache.fineract.treasury.domain.TreasuryRunLineRepository;
import org.apache.fineract.treasury.liquidity.Bucket;
import org.apache.fineract.treasury.liquidity.LadderMetric;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.liquidity.RatioCalculator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * What ratio components read: GL balances, asset class holdings and ladder metrics, all as of a date, in a currency,
 * for an office or the whole institution.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryRatioInputs {

    /** Natural balance: debit balance for assets and expenses, credit balance for liabilities, equity and income. */
    private static final String GL_SQL = """
            SELECT COALESCE(SUM(CASE WHEN g.classification_enum IN (1, 5)
                                     THEN (CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END)
                                     ELSE (CASE WHEN je.type_enum = 1 THEN je.amount ELSE -je.amount END) END), 0)
            FROM acc_gl_journal_entry je
            JOIN acc_gl_account g ON g.id = je.account_id
            WHERE je.currency_code = :currency AND je.entry_date <= :asOf AND (:officeId IS NULL OR je.office_id = :officeId)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TreasuryAccountRepository accountRepository;
    private final TreasuryBalanceReader balanceReader;
    private final TreasuryDealRepository dealRepository;
    private final TreasuryDealTypeService dealTypeService;
    private final TreasuryPledgeService pledgeService;
    private final TreasuryRunLineRepository runLineRepository;

    /** A holding total and what is excluded from it. */
    public record AssetValue(BigDecimal source, BigDecimal excludedMaturity, BigDecimal excludedPledged) {
    }

    public BigDecimal gl(final TreasuryRatioComponent component, final LocalDate asOf, final String currency, final Long officeId) {
        final MapSqlParameterSource params = new MapSqlParameterSource().addValue("currency", currency).addValue("asOf", asOf)
                .addValue("officeId", officeId, java.sql.Types.BIGINT);
        final String condition;
        if (component.getGlAccountId() != null) {
            condition = " AND g.id = :glAccountId";
            params.addValue("glAccountId", component.getGlAccountId());
        } else if (component.getGlCodePrefix() != null) {
            condition = " AND g.gl_code LIKE :prefix";
            params.addValue("prefix", component.getGlCodePrefix() + "%");
        } else {
            condition = " AND g.gl_code >= :from AND g.gl_code <= :to";
            params.addValue("from", component.getGlCodeFrom()).addValue("to", component.getGlCodeTo());
        }
        final BigDecimal balance = this.jdbcTemplate.queryForObject(GL_SQL + condition, params, BigDecimal.class);
        return balance == null ? BigDecimal.ZERO : balance;
    }

    public static String reference(final TreasuryRatioComponent component) {
        return switch (component.getComponentType()) {
            case GL_ACCOUNT -> component.getGlAccountId() != null ? "GL account " + component.getGlAccountId()
                    : component.getGlCodePrefix() != null ? "GL codes " + component.getGlCodePrefix() + "*"
                            : "GL codes " + component.getGlCodeFrom() + " to " + component.getGlCodeTo();
            case ASSET_CLASS -> "Asset class " + component.getAssetClass();
            case LADDER_METRIC -> component.getLadderMetric() + " over " + component.getMetricDays() + " days";
        };
    }

    /**
     * Treasury accounts of the class's types at their GL balance; settled asset deals of its instrument kinds or deal
     * types, held on the date, at their carrying amount (amortised cost with accrued interest, plus the fair value
     * adjustment, less the loss allowance at amortised cost). A deal maturing after the class's cut-off is excluded
     * whole; of the others the share of the nominal pledged on the date.
     */
    public AssetValue asset(final TreasuryAssetClassRule rule, final LocalDate asOf, final String currency, final Long officeId,
            final int scale) {
        final Set<String> accountTypes = Set.copyOf(TreasuryRuleSetService.split(rule.getAccountTypes()));
        final Set<String> kinds = Set.copyOf(TreasuryRuleSetService.split(rule.getInstrumentKinds()));
        final Set<Long> dealTypes = TreasuryRuleSetService.split(rule.getDealTypeIds()).stream().map(Long::valueOf)
                .collect(Collectors.toSet());
        BigDecimal source = BigDecimal.ZERO;
        BigDecimal excludedMaturity = BigDecimal.ZERO;
        BigDecimal excludedPledged = BigDecimal.ZERO;
        if (!accountTypes.isEmpty()) {
            final List<TreasuryAccount> accounts = this.accountRepository.findByStatusOrderByIdAsc(TreasuryAccountStatus.ACTIVE).stream()
                    .filter(a -> accountTypes.contains(a.getAccountType().name()) && a.getCurrencyCode().equals(currency)
                            && (officeId == null || officeId.equals(a.getOfficeId())))
                    .toList();
            final Map<Long, BigDecimal> balances = this.balanceReader.glBalances(accounts.stream().map(TreasuryAccount::getId).toList(),
                    asOf);
            source = source.add(balances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        if (!kinds.isEmpty() || !dealTypes.isEmpty()) {
            for (final TreasuryDeal deal : this.dealRepository.findByStatusOrderByIdAsc(DealStatus.SETTLED)) {
                if (!deal.getCurrencyCode().equals(currency) || officeId != null && !officeId.equals(deal.getOfficeId())
                        || deal.getValueDate().isAfter(asOf) || !deal.getMaturityDate().isAfter(asOf)) {
                    continue;
                }
                final TreasuryDealType type = this.dealTypeService.find(deal.getDealTypeId());
                final InstrumentKind kind = type.getInstrumentKind();
                if (!kind.isAsset() || !kinds.contains(kind.name()) && !dealTypes.contains(type.getId())) {
                    continue;
                }
                final BigDecimal value = DealLifecyclePlanner.carrying(deal, kind).assetTotal().add(deal.getFairValueAdjustment())
                        .subtract(deal.getClassification() == DealClassification.AMORTISED_COST ? deal.getEclAllowance() : BigDecimal.ZERO);
                final RatioCalculator.HoldingValue holding = RatioCalculator.holding(value, TreasuryPledgeService.outstanding(deal),
                        this.pledgeService.encumberedAmount(deal.getId(), asOf), ChronoUnit.DAYS.between(asOf, deal.getMaturityDate()),
                        rule.getMaxMaturityDays(), scale);
                source = source.add(holding.value());
                excludedMaturity = excludedMaturity.add(holding.excludedMaturity());
                excludedPledged = excludedPledged.add(holding.excludedPledged());
            }
        }
        return new AssetValue(source, excludedMaturity, excludedPledged);
    }

    /** Ladder metric over the buckets ending within the component's days, opening stock and totals left out. */
    public BigDecimal metric(final TreasuryRatioComponent component, final Long ladderRunId, final List<Bucket> buckets) {
        final int days = component.getMetricDays() == null ? 30 : component.getMetricDays();
        final Set<String> within = buckets.stream().filter(b -> b.toDay() != null && b.toDay() <= days).map(Bucket::code)
                .collect(Collectors.toSet());
        BigDecimal inflows = BigDecimal.ZERO;
        BigDecimal outflows = BigDecimal.ZERO;
        for (final TreasuryRunLine line : this.runLineRepository.findByRunIdOrderByBucketSeqAscIdAsc(ladderRunId)) {
            if (within.contains(line.getBucketCode()) && line.getSource() != LadderSourceCode.OPENING
                    && line.getSource() != LadderSourceCode.TOTAL) {
                inflows = inflows.add(line.getInflow());
                outflows = outflows.add(line.getOutflow());
            }
        }
        final LadderMetric metric = component.getLadderMetric();
        return metric == LadderMetric.INFLOWS ? inflows
                : metric == LadderMetric.OUTFLOWS ? outflows : RatioCalculator.netOutflows(inflows, outflows);
    }
}
