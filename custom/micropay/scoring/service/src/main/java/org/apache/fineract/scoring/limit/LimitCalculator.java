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
package org.apache.fineract.scoring.limit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.scoring.domain.ScoringLimitBand;
import org.apache.fineract.scoring.domain.ScoringLimitPolicy;
import org.apache.fineract.scoring.domain.ScoringOutcome;
import org.apache.fineract.scoring.input.ScoringCharacteristics;

/**
 * Turns a behavioural decision into a limit amount.
 *
 * <ul>
 * <li>A DECLINE, or a grade with no band, gives zero.</li>
 * <li>Otherwise the band's multiplier times the base characteristic, capped at the band's maximum. Without a
 * multiplier or a base characteristic the maximum is the limit. A missing or negative base gives zero.</li>
 * <li>The result is rounded down to the policy's rounding unit.</li>
 * </ul>
 */
public final class LimitCalculator {

    private LimitCalculator() {}

    public static BigDecimal calculate(final ScoringLimitPolicy policy, final String grade, final ScoringOutcome outcome,
            final Map<String, Object> inputs) {
        if (outcome == ScoringOutcome.DECLINE) {
            return BigDecimal.ZERO;
        }
        final Optional<ScoringLimitBand> band = policy.findBand(grade);
        if (band.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = band.get().getMaxAmount();
        if (band.get().getMultiplier() != null && policy.getBaseCharacteristic() != null) {
            final BigDecimal base = ScoringCharacteristics.toNumber(inputs.get(policy.getBaseCharacteristic()));
            if (base == null || base.signum() <= 0) {
                return BigDecimal.ZERO;
            }
            amount = amount.min(base.multiply(band.get().getMultiplier()));
        }
        return roundDown(amount.max(BigDecimal.ZERO), policy.getRoundingUnit());
    }

    private static BigDecimal roundDown(final BigDecimal amount, final BigDecimal unit) {
        if (unit == null || unit.signum() <= 0) {
            return amount.setScale(2, RoundingMode.DOWN);
        }
        return amount.divide(unit, 0, RoundingMode.DOWN).multiply(unit);
    }
}
