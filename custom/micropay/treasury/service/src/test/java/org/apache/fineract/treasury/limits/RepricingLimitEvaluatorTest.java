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
import static org.apache.fineract.treasury.limits.LimitFixtures.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRepricingBucket;
import org.apache.fineract.treasury.domain.TreasuryRunRepricingBucketRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRepricingShock;
import org.apache.fineract.treasury.domain.TreasuryRunRepricingShockRepository;
import org.apache.fineract.treasury.liquidity.RunType;
import org.junit.jupiter.api.Test;

/** The repricing gap and earnings at risk of the scope's latest repricing run. */
class RepricingLimitEvaluatorTest {

    private final TreasuryRunRepository runs = mock(TreasuryRunRepository.class);
    private final TreasuryRunRepricingBucketRepository buckets = mock(TreasuryRunRepricingBucketRepository.class);
    private final TreasuryRunRepricingShockRepository shocks = mock(TreasuryRunRepricingShockRepository.class);

    @Test
    void theOneYearCumulativeGapOrABucket() {
        when(this.runs.findByRunTypeAndAsOfDateAndCurrencyCode(RunType.REPRICING, AS_OF, "UGX")).thenReturn(List.of(run(40L, RunType.REPRICING, null),
                run(41L, RunType.REPRICING, null), run(42L, RunType.REPRICING, 1L)));
        when(this.buckets.findByRunIdOrderByBucketSeqAsc(41L)).thenReturn(List.of(bucket("ON", 0, 1, "5", "5"), bucket("M6_12", 181, 365, "-12", "-18"),
                bucket("Y1_PLUS", 366, null, "40", "22")));
        final RepricingGapLimitEvaluator evaluator = new RepricingGapLimitEvaluator(this.runs, this.buckets, this.shocks);
        final TreasuryLimit year = limit(1L, LimitMetric.REPRICING_GAP_PCT, LimitComparison.MIN, "-10", "-15", LimitUnit.PERCENT);
        final Observation o = evaluator.observe(year, context(null, null)).getFirst();
        assertEquals(0, new BigDecimal("-18").compareTo(o.value()), "the latest institution run, cumulative to 6 to 12 months");
        assertEquals(LimitLevel.HARD, o.level());
        assertEquals(41L, o.sourceId());
        year.setMetricKey("ON");
        assertNull(evaluator.observe(year, context(null, null)).getFirst().level(), "the overnight bucket's own 5%");
    }

    @Test
    void earningsAtRiskOfTheWorstOrANamedShock() {
        when(this.runs.findByRunTypeAndAsOfDateAndCurrencyCode(RunType.REPRICING, AS_OF, "UGX")).thenReturn(List.of(run(41L, RunType.REPRICING, null)));
        when(this.shocks.findByRunIdOrderByShockBpDesc(41L)).thenReturn(List.of(shock(200, "500", null), shock(-200, "-500", "6.5")));
        final EarningsAtRiskLimitEvaluator evaluator = new EarningsAtRiskLimitEvaluator(this.runs, this.buckets, this.shocks);
        final TreasuryLimit ear = limit(2L, LimitMetric.EAR_PCT, LimitComparison.MAX, null, "5", LimitUnit.PERCENT);
        final Observation worst = evaluator.observe(ear, context(null, null)).getFirst();
        assertEquals(0, new BigDecimal("6.5").compareTo(worst.value()));
        assertEquals(LimitLevel.HARD, worst.level());
        ear.setMetricKey("+200");
        assertNull(evaluator.observe(ear, context(null, null)).getFirst().value(), "a gain: nothing at risk");
        when(this.runs.findByRunTypeAndAsOfDateAndCurrencyCode(RunType.REPRICING, AS_OF, "UGX")).thenReturn(List.of());
        assertNull(evaluator.observe(ear, context(null, null)).getFirst().value(), "no run");
    }

    private static TreasuryRunRepricingBucket bucket(final String code, final int from, final Integer to, final String gap,
            final String cumulative) {
        final TreasuryRunRepricingBucket b = new TreasuryRunRepricingBucket();
        b.setBucketCode(code);
        b.setFromDay(from);
        b.setToDay(to);
        b.setGap(new BigDecimal(gap));
        b.setGapPercent(new BigDecimal(gap));
        b.setCumulativeGap(new BigDecimal(cumulative));
        b.setCumulativePercent(new BigDecimal(cumulative));
        return b;
    }

    private static TreasuryRunRepricingShock shock(final int bp, final String delta, final String ear) {
        final TreasuryRunRepricingShock s = new TreasuryRunRepricingShock();
        s.setShockBp(bp);
        s.setDeltaNii(new BigDecimal(delta));
        s.setEarPercent(ear == null ? null : new BigDecimal(ear));
        return s;
    }
}
