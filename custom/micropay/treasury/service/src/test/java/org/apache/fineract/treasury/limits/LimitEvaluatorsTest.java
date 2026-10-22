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
package org.apache.fineract.treasury.limits;

import static org.apache.fineract.treasury.limits.LimitFixtures.AS_OF;
import static org.apache.fineract.treasury.limits.LimitFixtures.context;
import static org.apache.fineract.treasury.limits.LimitFixtures.limit;
import static org.apache.fineract.treasury.limits.LimitFixtures.own;
import static org.apache.fineract.treasury.limits.LimitFixtures.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.fineract.treasury.data.TreasuryExposureData;
import org.apache.fineract.treasury.deal.LimitCheck;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.domain.TreasuryCounterparty;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyRepository;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyStatus;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.domain.TreasuryRunLine;
import org.apache.fineract.treasury.domain.TreasuryRunLineRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRatio;
import org.apache.fineract.treasury.domain.TreasuryRunRatioRepository;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.liquidity.RatioDirection;
import org.apache.fineract.treasury.liquidity.RatioStatus;
import org.apache.fineract.treasury.liquidity.RunType;
import org.apache.fineract.treasury.service.TreasuryBalanceReader;
import org.apache.fineract.treasury.service.TreasuryCounterpartyService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** One test group per metric's evaluator. */
class LimitEvaluatorsTest {

    // ---------- RATIO ----------

    @Test
    void aRatioFromTheRatiosRunWithItsOwnOrTheLimitsLevels() {
        final TreasuryRunRatioRepository ratios = mock(TreasuryRunRatioRepository.class);
        when(ratios.findByRunIdOrderBySequenceNumberAsc(13L))
                .thenReturn(List.of(ratio("LIQUID_ASSETS_TO_DEPOSITS", "16.2", "15", "17", RatioDirection.MIN),
                        ratio("ADVANCES_TO_DEPOSITS", "692.0682", "80", "75", RatioDirection.MAX)));
        final RatioLimitEvaluator evaluator = new RatioLimitEvaluator(ratios);
        final EvaluationContext context = context(null, run(13L, RunType.RATIOS, null));

        final TreasuryLimit ownLevels = own(1L, LimitMetric.RATIO);
        ownLevels.setMetricKey("LIQUID_ASSETS_TO_DEPOSITS");
        final Observation own = evaluator.observe(ownLevels, context).getFirst();
        assertEquals(LimitLevel.WARNING, own.level(), "16.2 is below the ratio's warning of 17");
        assertEquals(LimitComparison.MIN, own.comparison());
        assertEquals(0, BigDecimal.valueOf(15).compareTo(own.hard()));
        assertEquals("RUN", own.sourceType());
        assertEquals(13L, own.sourceId());

        final TreasuryLimit advances = limit(2L, LimitMetric.RATIO, LimitComparison.MAX, "75", "80", LimitUnit.PERCENT);
        advances.setMetricKey("ADVANCES_TO_DEPOSITS");
        final Observation hard = evaluator.observe(advances, context).getFirst();
        assertEquals(LimitLevel.HARD, hard.level());
        assertEquals(0, new BigDecimal("692.0682").compareTo(hard.value()));
        assertEquals(LimitScopeType.INSTITUTION, hard.subjectType());
    }

    @Test
    void aRatioWithoutADenominatorIsAHardBreachAndMissingDataIsNoLevel() {
        final TreasuryRunRatioRepository ratios = mock(TreasuryRunRatioRepository.class);
        when(ratios.findByRunIdOrderBySequenceNumberAsc(13L)).thenReturn(List.of(ratio("LCR", null, "100", "110", RatioDirection.MIN)));
        final RatioLimitEvaluator evaluator = new RatioLimitEvaluator(ratios);
        final TreasuryLimit lcr = own(1L, LimitMetric.RATIO);
        lcr.setMetricKey("LCR");
        final Observation none = evaluator.observe(lcr, context(null, run(13L, RunType.RATIOS, null))).getFirst();
        assertEquals(LimitLevel.HARD, none.level());
        assertNull(none.value());
        final TreasuryLimit missing = own(2L, LimitMetric.RATIO);
        missing.setMetricKey("NSFR");
        final Observation absent = evaluator.observe(missing, context(null, run(13L, RunType.RATIOS, null))).getFirst();
        assertNull(absent.level());
        assertTrue(absent.detail().contains("not in run 13"));
        assertNull(evaluator.observe(lcr, context(null, null)).getFirst().level(), "no ratios run");
    }

    // ---------- BUCKET_GAP ----------

    @Test
    void aBucketsCumulativeOrNetGapAsAnAmountOrAPercentage() {
        final List<TreasuryRunLine> lines = List.of(line("ON", LadderSourceCode.SAVINGS, "0", "400", null, null),
                line("ON", LadderSourceCode.TOTAL, "300", "1000", "-700", "-200"),
                line("D2_8", LadderSourceCode.TERM_DEPOSITS, "0", "100", null, null),
                line("D2_8", LadderSourceCode.TOTAL, "900", "1000", "-100", "-300"));
        final TreasuryLimit cumulative = limit(1L, LimitMetric.BUCKET_GAP, LimitComparison.MIN, null, "0", LimitUnit.AMOUNT);
        cumulative.setMetricKey("D2_8");
        cumulative.setGapType(GapType.CUMULATIVE);
        final Observation gap = BucketGapLimitEvaluator.observe(cumulative, context(run(9L, RunType.LADDER, null), null), 9L, lines);
        assertEquals(0, new BigDecimal("-300").compareTo(gap.value()));
        assertEquals(LimitLevel.HARD, gap.level());

        final TreasuryLimit net = limit(2L, LimitMetric.BUCKET_GAP, LimitComparison.MIN, "-50", "-500", LimitUnit.AMOUNT);
        net.setMetricKey("D2_8");
        net.setGapType(GapType.NET);
        assertEquals(LimitLevel.WARNING, BucketGapLimitEvaluator.observe(net, context(null, null), 9L, lines).level());

        final TreasuryLimit ofOutflows = limit(3L, LimitMetric.BUCKET_GAP, LimitComparison.MIN, "-5", "-10", LimitUnit.PERCENT);
        ofOutflows.setMetricKey("ON");
        ofOutflows.setGapBasis(GapBasis.TOTAL_OUTFLOWS);
        final Observation percent = BucketGapLimitEvaluator.observe(ofOutflows, context(null, null), 9L, lines);
        assertEquals(new BigDecimal("-10.0000"), percent.value(), "-200 of 2,000 outflows");
        assertEquals(LimitLevel.WARNING, percent.level(), "on the hard level is not past it");
        ofOutflows.setGapBasis(GapBasis.DEPOSIT_OUTFLOWS);
        assertEquals(new BigDecimal("-40.0000"), BucketGapLimitEvaluator.observe(ofOutflows, context(null, null), 9L, lines).value(),
                "-200 of 500 deposit outflows");
        ofOutflows.setMetricKey("M1_3");
        assertNull(BucketGapLimitEvaluator.observe(ofOutflows, context(null, null), 9L, lines).level(), "no such bucket");
    }

    @Test
    void theGapEvaluatorReadsTheLadderRun() {
        final TreasuryRunLineRepository repository = mock(TreasuryRunLineRepository.class);
        when(repository.findByRunIdOrderByBucketSeqAscIdAsc(9L))
                .thenReturn(List.of(line("ON", LadderSourceCode.TOTAL, "0", "5", "-5", "-5")));
        final TreasuryLimit limit = limit(1L, LimitMetric.BUCKET_GAP, LimitComparison.MIN, null, "0", LimitUnit.AMOUNT);
        limit.setMetricKey("ON");
        final BucketGapLimitEvaluator evaluator = new BucketGapLimitEvaluator(repository);
        assertEquals(LimitLevel.HARD, evaluator.observe(limit, context(run(9L, RunType.LADDER, null), null)).getFirst().level());
        assertNull(evaluator.observe(limit, context(null, null)).getFirst().level(), "no ladder run");
    }

    // ---------- concentrations ----------

    @Test
    @SuppressWarnings("unchecked")
    void depositorConcentrationOfTheTopN() {
        final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(eq(DepositorConcentrationLimitEvaluator.SQL), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(
                List.of(holder("client 7", "600"), holder("client 3", "250"), holder("group 2", "100"), holder("client 9", "50")));
        final DepositorConcentrationLimitEvaluator evaluator = new DepositorConcentrationLimitEvaluator(jdbc);
        final TreasuryLimit top2 = limit(1L, LimitMetric.DEPOSITOR_CONCENTRATION, LimitComparison.MAX, "75", "80", LimitUnit.PERCENT);
        top2.setTopN(2);
        final Observation observation = evaluator.observe(top2, context(null, null)).getFirst();
        assertEquals(new BigDecimal("85.0000"), observation.value());
        assertEquals(LimitLevel.HARD, observation.level());
        assertTrue(observation.detail().startsWith("Top 2 of 4 depositors: 850 of 1000 UGX (client 7, client 3"), observation.detail());
        final TreasuryLimit single = limit(2L, LimitMetric.DEPOSITOR_CONCENTRATION, LimitComparison.MAX, null, "5", LimitUnit.PERCENT);
        assertEquals(new BigDecimal("60.0000"), evaluator.observe(single, context(null, null)).getFirst().value(), "topN defaults to 1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fundingConcentrationAndNoBorrowings() {
        final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(eq(FundingConcentrationLimitEvaluator.SQL), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(holder("DFCU", "300000000"), holder("BOU", "200000000")));
        final FundingConcentrationLimitEvaluator evaluator = new FundingConcentrationLimitEvaluator(jdbc);
        final TreasuryLimit limit = limit(1L, LimitMetric.FUNDING_CONCENTRATION, LimitComparison.MAX, null, "40", LimitUnit.PERCENT);
        final Observation observation = evaluator.observe(limit, context(null, null)).getFirst();
        assertEquals(new BigDecimal("60.0000"), observation.value());
        assertEquals(LimitLevel.HARD, observation.level());
        assertEquals("DEALS", observation.sourceType());
        final Observation none = DepositorConcentrationLimitEvaluator.observe(limit, context(null, null), List.of(), "DEALS",
                "funding counterparties");
        assertNull(none.value());
        assertNull(none.level());
    }

    // ---------- COUNTERPARTY_EXPOSURE ----------

    @Test
    void counterpartyOwnLevelsFollowTheCaptureCheck() {
        final TreasuryCounterparty bank = counterparty(5L, "1000000", "80");
        final TreasuryLimit ownLevels = own(1L, LimitMetric.COUNTERPARTY_EXPOSURE);
        final LimitCheck.Result warning = LimitCheck.evaluate(bank.getExposureLimit(), bank.getWarningPercent(), new BigDecimal("500000"),
                new BigDecimal("350000"));
        Observation o = CounterpartyExposureLimitEvaluator.observe(ownLevels, bank, "UGX", warning, warning.exposureAfter(), "DEAL", 70L,
                "d");
        assertEquals(LimitLevel.WARNING, o.level());
        assertEquals(new BigDecimal("85.00"), o.value());
        assertEquals(0, BigDecimal.valueOf(80).compareTo(o.warning()));
        assertEquals(0, BigDecimal.valueOf(100).compareTo(o.hard()));
        assertEquals(LimitScopeType.COUNTERPARTY, o.subjectType());
        assertEquals(5L, o.subjectId());
        final LimitCheck.Result exceeded = LimitCheck.evaluate(bank.getExposureLimit(), bank.getWarningPercent(),
                new BigDecimal("999999.99"), new BigDecimal("0.02"));
        o = CounterpartyExposureLimitEvaluator.observe(ownLevels, bank, "UGX", exceeded, null, "DEAL_REQUEST", null, "d");
        assertEquals(LimitLevel.HARD, o.level(), "EXCEEDED although the utilisation rounds to 100.00");
        final LimitCheck.Result ok = LimitCheck.evaluate(bank.getExposureLimit(), bank.getWarningPercent(), BigDecimal.TEN, BigDecimal.ONE);
        assertNull(CounterpartyExposureLimitEvaluator.observe(ownLevels, bank, "UGX", ok, null, "DEAL", 1L, "d").level());
        assertNull(CounterpartyExposureLimitEvaluator.observe(ownLevels, bank, "USD", ok, null, "DEAL", 1L, "d"),
                "limit in another currency");
        assertNull(CounterpartyExposureLimitEvaluator.observe(ownLevels, counterparty(6L, null, "80"), "UGX", ok, null, "DEAL", 1L, "d"),
                "no exposure limit");
    }

    @Test
    void counterpartyPercentAndAmountLevelsOverTodaysExposure() {
        final TreasuryCounterpartyRepository repository = mock(TreasuryCounterpartyRepository.class);
        final TreasuryCounterpartyService service = mock(TreasuryCounterpartyService.class);
        final TreasuryCounterparty bank = counterparty(5L, "1000000", "80");
        final TreasuryCounterparty closed = counterparty(6L, "1000000", "80");
        closed.setStatus(TreasuryCounterpartyStatus.CLOSED);
        when(repository.findAllByOrderByNameAsc()).thenReturn(List.of(bank, closed));
        when(service.exposure(5L)).thenReturn(new TreasuryExposureData(5L, "B5", "Bank 5", "UGX", new BigDecimal("1000000"),
                BigDecimal.valueOf(80), new BigDecimal("700000"), null, null, "OK",
                List.of(new TreasuryExposureData.DealExposure(1L, "SETTLED", "UGX", new BigDecimal("700000"), LocalDate.of(2027, 1, 1)))));
        final CounterpartyExposureLimitEvaluator evaluator = new CounterpartyExposureLimitEvaluator(repository, service);
        final TreasuryLimit percent = limit(1L, LimitMetric.COUNTERPARTY_EXPOSURE, LimitComparison.MAX, "60", "90", LimitUnit.PERCENT);
        final List<Observation> observations = evaluator.observe(percent, context(null, null));
        assertEquals(1, observations.size(), "closed counterparties are left out");
        assertEquals(new BigDecimal("70.00"), observations.getFirst().value());
        assertEquals(LimitLevel.WARNING, observations.getFirst().level());
        final TreasuryLimit amount = limit(2L, LimitMetric.COUNTERPARTY_EXPOSURE, LimitComparison.MAX, null, "500000", LimitUnit.AMOUNT);
        amount.setCurrencyCode("UGX");
        amount.setScopeType(LimitScopeType.COUNTERPARTY);
        amount.setScopeId(5L);
        assertEquals(LimitLevel.HARD, evaluator.observe(amount, context(null, null)).getFirst().level());
        amount.setScopeId(7L);
        assertTrue(evaluator.observe(amount, context(null, null)).isEmpty(), "another counterparty");
    }

    // ---------- ACCOUNT_BALANCE ----------

    @Test
    void accountOwnMinimumAndMaximum() {
        final TreasuryLimit ownLevels = own(1L, LimitMetric.ACCOUNT_BALANCE);
        final TreasuryAccount vault = account(3L, 1L, "1000", "5000");
        Observation o = AccountBalanceLimitEvaluator.observe(ownLevels, vault, new BigDecimal("999.99"));
        assertEquals(LimitLevel.HARD, o.level());
        assertEquals(LimitComparison.MIN, o.comparison());
        assertEquals(0, new BigDecimal("1000").compareTo(o.hard()));
        o = AccountBalanceLimitEvaluator.observe(ownLevels, vault, new BigDecimal("5000.01"));
        assertEquals(LimitLevel.HARD, o.level());
        assertEquals(LimitComparison.MAX, o.comparison());
        assertNull(AccountBalanceLimitEvaluator.observe(ownLevels, vault, new BigDecimal("5000")).level(), "bounds are inclusive");
        assertNull(AccountBalanceLimitEvaluator.observe(ownLevels, account(4L, 1L, null, null), BigDecimal.ONE), "no levels set");
        final TreasuryLimit explicit = limit(2L, LimitMetric.ACCOUNT_BALANCE, LimitComparison.MIN, "2000", "1500", LimitUnit.AMOUNT);
        assertEquals(LimitLevel.WARNING, AccountBalanceLimitEvaluator.observe(explicit, vault, new BigDecimal("1800")).level());
    }

    @Test
    void accountsOfTheScopeAndCurrencyAtTheirGlBalance() {
        final TreasuryAccountRepository repository = mock(TreasuryAccountRepository.class);
        final TreasuryBalanceReader reader = mock(TreasuryBalanceReader.class);
        final TreasuryAccount office1 = account(3L, 1L, "1000", null);
        final TreasuryAccount office2 = account(4L, 2L, "1000", null);
        final TreasuryAccount dollars = account(5L, 1L, "1000", null);
        dollars.setCurrencyCode("USD");
        when(repository.findByStatusOrderByIdAsc(TreasuryAccountStatus.ACTIVE)).thenReturn(List.of(office1, office2, dollars));
        when(reader.glBalances(any(), eq(AS_OF))).thenReturn(Map.of(3L, new BigDecimal("10"), 4L, new BigDecimal("2000")));
        final AccountBalanceLimitEvaluator evaluator = new AccountBalanceLimitEvaluator(repository, reader);
        final TreasuryLimit ownLevels = own(1L, LimitMetric.ACCOUNT_BALANCE);
        final List<Observation> all = evaluator.observe(ownLevels, context(null, null));
        assertEquals(List.of(3L, 4L), all.stream().map(Observation::subjectId).toList());
        assertEquals(LimitLevel.HARD, all.getFirst().level());
        assertNull(all.get(1).level());
        ownLevels.setScopeType(LimitScopeType.OFFICE);
        ownLevels.setScopeId(2L);
        assertEquals(List.of(4L), evaluator.observe(ownLevels, context(null, null)).stream().map(Observation::subjectId).toList());
    }

    // ---------- helpers ----------

    private static TreasuryRunRatio ratio(final String code, final String result, final String limit, final String warning,
            final RatioDirection direction) {
        final TreasuryRunRatio ratio = new TreasuryRunRatio();
        ratio.setCode(code);
        ratio.setDirection(direction);
        ratio.setResult(result == null ? null : new BigDecimal(result));
        ratio.setNumerator(BigDecimal.ONE);
        ratio.setDenominator(result == null ? BigDecimal.ZERO : BigDecimal.TEN);
        ratio.setLimitValue(new BigDecimal(limit));
        ratio.setWarningValue(new BigDecimal(warning));
        ratio.setStatus(RatioStatus.OK);
        return ratio;
    }

    private static TreasuryRunLine line(final String bucket, final LadderSourceCode source, final String inflow, final String outflow,
            final String net, final String cumulative) {
        final TreasuryRunLine line = new TreasuryRunLine();
        line.setBucketCode(bucket);
        line.setSource(source);
        line.setInflow(new BigDecimal(inflow));
        line.setOutflow(new BigDecimal(outflow));
        line.setNet(net == null ? null : new BigDecimal(net));
        line.setCumulativeGap(cumulative == null ? null : new BigDecimal(cumulative));
        return line;
    }

    private static DepositorConcentrationLimitEvaluator.Holder holder(final String reference, final String amount) {
        return new DepositorConcentrationLimitEvaluator.Holder(reference, new BigDecimal(amount));
    }

    private static TreasuryCounterparty counterparty(final long id, final String limit, final String warning) {
        final TreasuryCounterparty counterparty = new TreasuryCounterparty();
        counterparty.setId(id);
        counterparty.setShortCode("B" + id);
        counterparty.setName("Bank " + id);
        counterparty.setExposureLimit(limit == null ? null : new BigDecimal(limit));
        counterparty.setLimitCurrencyCode(limit == null ? null : "UGX");
        counterparty.setWarningPercent(new BigDecimal(warning));
        counterparty.setStatus(TreasuryCounterpartyStatus.ACTIVE);
        return counterparty;
    }

    private static TreasuryAccount account(final long id, final long officeId, final String min, final String max) {
        final TreasuryAccount account = new TreasuryAccount();
        account.setId(id);
        account.setCode("ACC-" + id);
        account.setName("Account " + id);
        account.setOfficeId(officeId);
        account.setCurrencyCode("UGX");
        account.setMinBalance(min == null ? null : new BigDecimal(min));
        account.setMaxBalance(max == null ? null : new BigDecimal(max));
        account.setStatus(TreasuryAccountStatus.ACTIVE);
        return account;
    }
}
