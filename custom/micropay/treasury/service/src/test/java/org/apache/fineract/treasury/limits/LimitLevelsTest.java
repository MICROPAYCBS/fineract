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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MIN and MAX comparisons, warning and hard levels, shares and concentrations. */
class LimitLevelsTest {

    private static final BigDecimal WARNING = BigDecimal.valueOf(17);
    private static final BigDecimal HARD = BigDecimal.valueOf(15);

    @Test
    void aMinimumIsCrossedBelowItsLevels() {
        assertNull(LimitLevels.level(LimitComparison.MIN, WARNING, HARD, new BigDecimal("17.0001")));
        assertNull(LimitLevels.level(LimitComparison.MIN, WARNING, HARD, WARNING), "on the warning level");
        assertEquals(LimitLevel.WARNING, LimitLevels.level(LimitComparison.MIN, WARNING, HARD, new BigDecimal("16.9999")));
        assertEquals(LimitLevel.WARNING, LimitLevels.level(LimitComparison.MIN, WARNING, HARD, HARD), "on the hard level");
        assertEquals(LimitLevel.HARD, LimitLevels.level(LimitComparison.MIN, WARNING, HARD, new BigDecimal("14.9999")));
        assertEquals(LimitLevel.HARD, LimitLevels.level(LimitComparison.MIN, null, BigDecimal.ZERO, new BigDecimal("-0.01")),
                "a negative gap");
        assertNull(LimitLevels.level(LimitComparison.MIN, null, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void aMaximumIsCrossedAboveItsLevels() {
        final BigDecimal warning = BigDecimal.valueOf(20);
        final BigDecimal hard = BigDecimal.valueOf(25);
        assertNull(LimitLevels.level(LimitComparison.MAX, warning, hard, warning));
        assertEquals(LimitLevel.WARNING, LimitLevels.level(LimitComparison.MAX, warning, hard, new BigDecimal("20.01")));
        assertEquals(LimitLevel.WARNING, LimitLevels.level(LimitComparison.MAX, warning, hard, hard));
        assertEquals(LimitLevel.HARD, LimitLevels.level(LimitComparison.MAX, warning, hard, new BigDecimal("25.0001")));
        assertEquals(LimitLevel.HARD, LimitLevels.level(LimitComparison.MAX, null, hard, BigDecimal.valueOf(30)), "no warning level");
        assertNull(LimitLevels.level(LimitComparison.MAX, null, hard, BigDecimal.valueOf(24)));
    }

    @Test
    void noValueOrNoComparisonIsNoLevel() {
        assertNull(LimitLevels.level(LimitComparison.MAX, null, BigDecimal.TEN, null));
        assertNull(LimitLevels.level(null, null, BigDecimal.TEN, BigDecimal.valueOf(50)));
    }

    @Test
    void sharesAndConcentrations() {
        assertEquals(new BigDecimal("33.3333"), LimitLevels.percent(BigDecimal.ONE, BigDecimal.valueOf(3)));
        assertNull(LimitLevels.percent(BigDecimal.ONE, BigDecimal.ZERO));
        // five depositors of 1,000; 500; 300; 150; 50 (and an overdrawn one left out): the top 2 hold 75%
        final LimitLevels.Concentration top2 = LimitLevels.concentration(List.of(new BigDecimal("300"), new BigDecimal("1000"),
                new BigDecimal("50"), new BigDecimal("-20"), new BigDecimal("500"), new BigDecimal("150")), 2);
        assertEquals(0, new BigDecimal("1500").compareTo(top2.top()));
        assertEquals(0, new BigDecimal("2000").compareTo(top2.total()));
        assertEquals(5, top2.holders());
        assertEquals(new BigDecimal("75.0000"), top2.share());
        assertEquals(new BigDecimal("100.0000"), LimitLevels.concentration(List.of(BigDecimal.TEN), 20).share(), "fewer than N");
        assertNull(LimitLevels.concentration(List.of(), 1).share());
    }

    @Test
    void levelsRankAndMetricsDeclareWhatTheyAllow() {
        assertTrue(LimitLevel.HARD.isAbove(LimitLevel.WARNING));
        assertTrue(LimitLevel.WARNING.isAbove(null));
        assertFalse(LimitLevel.WARNING.isAbove(LimitLevel.HARD));
        assertFalse(LimitLevel.HARD.isAbove(LimitLevel.HARD));
        assertTrue(LimitMetric.RATIO.canUseOwnLevels());
        assertFalse(LimitMetric.BUCKET_GAP.canUseOwnLevels());
        assertTrue(LimitMetric.ACCOUNT_BALANCE.allows(LimitScopeType.ACCOUNT));
        assertFalse(LimitMetric.RATIO.allows(LimitScopeType.COUNTERPARTY));
        assertFalse(LimitMetric.RATIO.allows(LimitUnit.DAYS));
        assertEquals(LimitUnit.AMOUNT, LimitMetric.ACCOUNT_BALANCE.ownUnit());
        assertTrue(LimitMetric.BUCKET_GAP.isKeyed());
        assertTrue(LimitMetric.FUNDING_CONCENTRATION.isConcentration());
    }
}
