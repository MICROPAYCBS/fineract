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
package org.apache.fineract.treasury.liquidity;

import static org.apache.fineract.treasury.liquidity.LadderFixtures.AS_OF;
import static org.apache.fineract.treasury.liquidity.LadderFixtures.BUCKETS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LadderBuilderTest {

    private final LadderContext context = LadderFixtures.context(Assumptions.NONE);

    @Test
    void daysAfterTheAsOfDateDecideTheBucket() {
        assertEquals("ON", bucket(0));
        assertEquals("ON", bucket(1));
        assertEquals("D2_8", bucket(2));
        assertEquals("D2_8", bucket(8));
        assertEquals("D9_1M", bucket(9));
        assertEquals("D9_1M", bucket(30));
        assertEquals("M1_3", bucket(31));
        assertEquals("M6_12", bucket(365));
        assertEquals("Y1_PLUS", bucket(366));
        assertEquals("Y1_PLUS", bucket(4000));
        assertEquals("ON", bucket(-40), "overdue flows fall in the first bucket");
        assertEquals("ON", LadderBuilder.bucketOf(this.context, flow(null, "1", false)).code(), "undated flows are on demand");
    }

    @Test
    void netAndCumulativeGapPerBucket() {
        final LadderBuilder.Ladder ladder = LadderBuilder.build(this.context, List.of(
                LadderFlow.contractual(LadderSourceCode.OPENING, "BANK", 1L, "BANK-1", null, new BigDecimal("1000")),
                LadderFlow.contractual(LadderSourceCode.SAVINGS, "DEMAND_SAVINGS", 2L, "Savings", null, new BigDecimal("-1500")),
                LadderFlow.behavioural(LadderSourceCode.SAVINGS, "DEMAND_SAVINGS", 2L, "Savings", null, new BigDecimal("1400")),
                LadderFlow.contractual(LadderSourceCode.LOANS, "LOAN_PRINCIPAL", 3L, "Loan", AS_OF.plusDays(5), new BigDecimal("300")),
                LadderFlow.contractual(LadderSourceCode.FIXED, "OUTFLOW", 4L, "Salaries", AS_OF.plusDays(6), new BigDecimal("-700"))));

        final LadderBuilder.BucketTotal on = ladder.totals().get(0);
        assertEquals(new BigDecimal("1000"), on.inflow());
        assertEquals(new BigDecimal("100"), on.outflow(), "the savings line nets to -100 after its behavioural adjustment");
        assertEquals(new BigDecimal("900"), on.net());
        assertEquals(new BigDecimal("900"), on.cumulativeGap());
        final LadderBuilder.BucketTotal week = ladder.totals().get(1);
        assertEquals(new BigDecimal("-400"), week.net());
        assertEquals(new BigDecimal("500"), week.cumulativeGap());
        assertEquals(new BigDecimal("500"), ladder.totals().getLast().cumulativeGap());
        assertEquals(7, ladder.totals().size());
        assertEquals(0, ladder.beyondHorizon().signum());
    }

    @Test
    void aBoundedLastBucketLeavesLaterFlowsOut() {
        final LadderContext bounded = new LadderContext(AS_OF, "UGX", null,
                List.of(new Bucket(1, "W1", "Week", 0, 7), new Bucket(2, "M1", "Month", 8, 30)), Assumptions.NONE, 2);
        final LadderBuilder.Ladder ladder = LadderBuilder.build(bounded, List.of(flow(AS_OF.plusDays(30), "10", false),
                flow(AS_OF.plusDays(31), "25", false), flow(AS_OF.plusDays(400), "-5", false)));

        assertEquals(new BigDecimal("10"), ladder.totals().getLast().cumulativeGap());
        assertEquals(new BigDecimal("20"), ladder.beyondHorizon());
        assertEquals(AS_OF.plusDays(30), bounded.horizonEnd());
        assertEquals(AS_OF.plusDays(366 + 365), LadderFixtures.context(Assumptions.NONE).horizonEnd());
    }

    @Test
    void bucketsMustBeContiguousFromDayZero() {
        assertNull(LadderBuilder.checkBuckets(BUCKETS));
        assertTrue(LadderBuilder.checkBuckets(List.of()).contains("at least one"));
        assertTrue(LadderBuilder.checkBuckets(List.of(new Bucket(1, "A", "A", 1, 5))).contains("start on day 0"));
        assertTrue(LadderBuilder.checkBuckets(List.of(new Bucket(1, "A", "A", 0, 5), new Bucket(2, "B", "B", 7, 9)))
                .contains("start on day 6"));
        assertTrue(LadderBuilder.checkBuckets(List.of(new Bucket(1, "A", "A", 0, null), new Bucket(2, "B", "B", 1, 9)))
                .contains("only the last"));
        assertTrue(LadderBuilder.checkBuckets(List.of(new Bucket(1, "A", "A", 0, 5), new Bucket(2, "A", "B", 6, 9))).contains("twice"));
        assertTrue(
                LadderBuilder.checkBuckets(List.of(new Bucket(1, "A", "A", 0, 5), new Bucket(2, "B", "B", 6, 4))).contains("ends before"));
    }

    private String bucket(final int days) {
        return LadderBuilder.bucketOf(this.context, flow(AS_OF.plusDays(days), "1", false)).code();
    }

    private static LadderFlow flow(final java.time.LocalDate date, final String amount, final boolean behavioural) {
        return new LadderFlow(LadderSourceCode.FIXED, "X", null, null, date, new BigDecimal(amount), behavioural);
    }
}
