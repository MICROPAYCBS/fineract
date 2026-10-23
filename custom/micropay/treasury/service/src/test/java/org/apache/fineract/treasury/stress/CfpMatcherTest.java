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
package org.apache.fineract.treasury.stress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.apache.fineract.treasury.limits.LimitLevel;
import org.apache.fineract.treasury.limits.LimitMetric;
import org.junit.jupiter.api.Test;

/** Which breaches and stress results meet a trigger. */
class CfpMatcherTest {

    @Test
    void aBreachOfTheLimitOrOfAnyLimitOfTheMetricAtOrAboveTheLevel() {
        final TriggerCondition breach = TriggerCondition.LIMIT_BREACH;
        assertTrue(CfpMatcher.meetsBreach(breach, 5L, null, LimitLevel.WARNING, 5L, LimitMetric.RATIO, LimitLevel.WARNING));
        assertTrue(CfpMatcher.meetsBreach(breach, 5L, null, LimitLevel.WARNING, 5L, LimitMetric.RATIO, LimitLevel.HARD), "above the level");
        assertFalse(CfpMatcher.meetsBreach(breach, 5L, null, LimitLevel.HARD, 5L, LimitMetric.RATIO, LimitLevel.WARNING));
        assertFalse(CfpMatcher.meetsBreach(breach, 5L, null, LimitLevel.WARNING, 6L, LimitMetric.RATIO, LimitLevel.HARD), "another limit");
        assertTrue(CfpMatcher.meetsBreach(breach, null, LimitMetric.SURVIVAL_DAYS, LimitLevel.HARD, 9L, LimitMetric.SURVIVAL_DAYS,
                LimitLevel.HARD));
        assertFalse(
                CfpMatcher.meetsBreach(breach, null, LimitMetric.SURVIVAL_DAYS, LimitLevel.HARD, 9L, LimitMetric.RATIO, LimitLevel.HARD));
        assertTrue(CfpMatcher.meetsBreach(breach, 5L, null, null, 5L, LimitMetric.RATIO, LimitLevel.WARNING), "WARNING by default");
        assertFalse(CfpMatcher.meetsBreach(TriggerCondition.MANUAL, 5L, null, LimitLevel.WARNING, 5L, LimitMetric.RATIO, LimitLevel.HARD));
        assertFalse(CfpMatcher.meetsBreach(breach, 5L, null, LimitLevel.WARNING, 5L, LimitMetric.RATIO, null));
    }

    @Test
    void aSurvivalBelowTheDaysOfTheScenarioOrAny() {
        final TriggerCondition survival = TriggerCondition.SURVIVAL_BELOW;
        final BigDecimal thirty = BigDecimal.valueOf(30);
        assertTrue(CfpMatcher.meetsSurvival(survival, thirty, null, new BigDecimal("29.99"), false, "MICROPAY_RUN"));
        assertFalse(CfpMatcher.meetsSurvival(survival, thirty, null, thirty, false, "MICROPAY_RUN"), "not below");
        assertFalse(CfpMatcher.meetsSurvival(survival, thirty, null, null, true, "MICROPAY_RUN"), "beyond the horizon");
        assertTrue(CfpMatcher.meetsSurvival(survival, thirty, "COMBINED", BigDecimal.ONE, false, "COMBINED"));
        assertFalse(CfpMatcher.meetsSurvival(survival, thirty, "COMBINED", BigDecimal.ONE, false, "MARKET_WIDE"), "another scenario");
        assertFalse(CfpMatcher.meetsSurvival(TriggerCondition.LIMIT_BREACH, thirty, null, BigDecimal.ONE, false, "X"));
    }
}
