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

import java.math.BigDecimal;
import org.apache.fineract.treasury.limits.LimitLevel;
import org.apache.fineract.treasury.limits.LimitMetric;

/** Whether a breach or a stress result meets a contingency funding plan trigger. */
public final class CfpMatcher {

    private CfpMatcher() {}

    /**
     * A LIMIT_BREACH trigger names a limit, or a metric for every limit of it, and the lowest level it reacts to: a
     * WARNING trigger also meets HARD breaches.
     */
    public static boolean meetsBreach(final TriggerCondition condition, final Long triggerLimitId, final LimitMetric triggerMetric,
            final LimitLevel triggerLevel, final Long limitId, final LimitMetric metric, final LimitLevel level) {
        if (condition != TriggerCondition.LIMIT_BREACH || level == null) {
            return false;
        }
        final boolean same = triggerLimitId != null ? triggerLimitId.equals(limitId) : triggerMetric == metric;
        return same && !(triggerLevel == null ? LimitLevel.WARNING : triggerLevel).isAbove(level);
    }

    /** A SURVIVAL_BELOW trigger: a survival of fewer days, of its scenario or any; never when beyond the horizon. */
    public static boolean meetsSurvival(final TriggerCondition condition, final BigDecimal days, final String triggerScenario,
            final BigDecimal survivalDays, final boolean beyondHorizon, final String scenarioCode) {
        return condition == TriggerCondition.SURVIVAL_BELOW && !beyondHorizon && survivalDays != null && days != null
                && survivalDays.compareTo(days) < 0 && (triggerScenario == null || triggerScenario.equals(scenarioCode));
    }
}
