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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
import org.apache.fineract.treasury.domain.TreasuryRunStressBucket;
import org.apache.fineract.treasury.domain.TreasuryRunStressBucketRepository;
import org.apache.fineract.treasury.liquidity.RunType;
import org.junit.jupiter.api.Test;

/** The shortest survival of the scope's latest stress run per scenario, or the named scenario's. */
class SurvivalLimitEvaluatorTest {

    private final TreasuryRunRepository runs = mock(TreasuryRunRepository.class);
    private final TreasuryRunStressBucketRepository buckets = mock(TreasuryRunStressBucketRepository.class);
    private final SurvivalLimitEvaluator evaluator = new SurvivalLimitEvaluator(this.runs, this.buckets);

    @Test
    void theShortestOfTheLatestRuns() {
        when(this.runs.findByRunTypeAndAsOfDateAndCurrencyCode(RunType.STRESS, AS_OF, "UGX")).thenReturn(List.of(stress(20L, "RUN", "45.5", null),
                stress(21L, "MARKET", null, null), stress(22L, "RUN", "12.25", null), stress(23L, "RUN", "1", 1L)));
        final TreasuryRunStressBucket last = new TreasuryRunStressBucket();
        last.setFromDay(366);
        when(this.buckets.findByRunIdOrderByBucketSeqAscIdAsc(21L)).thenReturn(List.of(last));
        final TreasuryLimit limit = limit(1L, LimitMetric.SURVIVAL_DAYS, LimitComparison.MIN, "60", "30", LimitUnit.DAYS);
        Observation o = this.evaluator.observe(limit, context(null, null)).getFirst();
        assertEquals(0, new BigDecimal("12.25").compareTo(o.value()), "run 22 replaced 20; the office run is left out");
        assertEquals(LimitLevel.HARD, o.level());
        assertEquals(22L, o.sourceId());
        assertTrue(o.detail().contains("shortest of 2 scenarios"), o.detail());
        limit.setMetricKey("MARKET");
        o = this.evaluator.observe(limit, context(null, null)).getFirst();
        assertEquals(0, BigDecimal.valueOf(366).compareTo(o.value()), "beyond the horizon: the last bucket's first day");
        assertNull(o.level());
        limit.setMetricKey("OTHER");
        assertNull(this.evaluator.observe(limit, context(null, null)).getFirst().value(), "no run of the scenario");
    }

    private static TreasuryRun stress(final long id, final String scenario, final String days, final Long officeId) {
        final TreasuryRun run = run(id, RunType.STRESS, officeId);
        run.setScenarioCode(scenario);
        run.setScenarioVersion(1);
        run.setSurvivalDays(days == null ? null : new BigDecimal(days));
        run.setSurvivalBeyondHorizon(days == null);
        return run;
    }
}
