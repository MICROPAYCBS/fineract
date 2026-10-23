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
import static org.apache.fineract.treasury.limits.LimitFixtures.limit;
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
import org.apache.fineract.treasury.data.TreasuryFxPositionData;
import org.apache.fineract.treasury.service.TreasuryFxConfig;
import org.apache.fineract.treasury.service.TreasuryFxPositionService;
import org.junit.jupiter.api.Test;

/** A single currency's NOP in its own evaluation; the aggregate in the base currency's. */
class FxNopLimitEvaluatorTest {

    private final TreasuryFxPositionService positions = mock(TreasuryFxPositionService.class);
    private final TreasuryFxConfig config = new TreasuryFxConfig("UGX", 3);
    private final FxSingleNopLimitEvaluator single = new FxSingleNopLimitEvaluator(this.positions, this.config);
    private final FxAggregateNopLimitEvaluator aggregate = new FxAggregateNopLimitEvaluator(this.positions, this.config);

    @Test
    void singleAndAggregate() {
        when(this.positions.positions(eq(AS_OF), any())).thenReturn(data(new BigDecimal("1000000000")));
        final TreasuryLimitFixture usd = new TreasuryLimitFixture();
        final Observation o = this.single.observe(usd.single, context("USD")).getFirst();
        assertEquals(new BigDecimal("12.5000"), o.value());
        assertEquals(LimitLevel.HARD, o.level(), "above 10%");
        assertTrue(o.detail().startsWith("Net 33333.33 USD = 125000000 UGX"), o.detail());
        assertTrue(this.single.observe(usd.single, context("UGX")).isEmpty(), "not in the base currency");
        final Observation a = this.aggregate.observe(usd.aggregate, context("UGX")).getFirst();
        assertEquals(new BigDecimal("15.0000"), a.value());
        assertNull(a.level(), "within 20%");
        assertTrue(this.aggregate.observe(usd.aggregate, context("USD")).isEmpty(), "only in the base currency");
    }

    @Test
    void withoutCoreCapitalNothingIsObserved() {
        when(this.positions.positions(eq(AS_OF), any())).thenReturn(data(null));
        final Observation o = this.single.observe(new TreasuryLimitFixture().single, context("USD")).getFirst();
        assertNull(o.value());
        assertNull(o.level());
    }

    private static EvaluationContext context(final String currency) {
        return new EvaluationContext(AS_OF, currency, null, null, null, 2);
    }

    private static TreasuryFxPositionData data(final BigDecimal capital) {
        final BigDecimal single = capital == null ? null : new BigDecimal("12.5000");
        return new TreasuryFxPositionData(AS_OF, "UGX", null, capital, null,
                List.of(new TreasuryFxPositionData.Currency("USD", false, new BigDecimal("40000"), new BigDecimal("6666.67"),
                        new BigDecimal("33333.33"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("3750"), AS_OF, false,
                        new BigDecimal("125000000"), "LONG", single),
                        new TreasuryFxPositionData.Currency("EUR", false, BigDecimal.ZERO, new BigDecimal("6000"), new BigDecimal("-6000"),
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("4000"), AS_OF, false,
                                new BigDecimal("-24000000"), "SHORT", capital == null ? null : new BigDecimal("2.4000"))),
                new BigDecimal("125000000"), new BigDecimal("24000000"), new BigDecimal("125000000"),
                capital == null ? null : new BigDecimal("15.0000"), "PRECEDING_QUARTER", LocalDate.of(2026, 9, 30));
    }

    private static final class TreasuryLimitFixture {

        private final org.apache.fineract.treasury.domain.TreasuryLimit single = limit(1L, LimitMetric.FX_SINGLE_NOP, LimitComparison.MAX,
                null, "10", LimitUnit.PERCENT);
        private final org.apache.fineract.treasury.domain.TreasuryLimit aggregate = limit(2L, LimitMetric.FX_AGGREGATE_NOP,
                LimitComparison.MAX, null, "20", LimitUnit.PERCENT);
    }
}
