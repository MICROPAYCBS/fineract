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
package org.apache.fineract.portfolio.charge.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTier;
import org.apache.fineract.portfolio.charge.exception.ChargeTierNotFoundException;

/**
 * Lookup-style tier resolution: find the single band containing the base amount.
 */
public final class ChargeTierCalculator {

    private ChargeTierCalculator() {}

    /**
     * Returns the tier's configured amount value (flat fee or percentage rate, depending on charge calculation type).
     */
    public static BigDecimal resolveTierAmountOrPercentage(final Charge charge, final BigDecimal baseAmount) {
        return findMatchingTier(charge, baseAmount).getAmount();
    }

    /**
     * Returns the final charge monetary amount for the given base (applies % when calculation type is percentage).
     */
    public static BigDecimal resolveChargeAmount(final Charge charge, final BigDecimal baseAmount) {
        final ChargeTier tier = findMatchingTier(charge, baseAmount);
        final ChargeCalculationType calculationType = ChargeCalculationType.fromInt(charge.getChargeCalculation());
        if (calculationType.isFlat()) {
            return tier.getAmount();
        }
        final BigDecimal base = baseAmount == null ? BigDecimal.ZERO : baseAmount;
        return percentageOf(base, tier.getAmount());
    }

    public static ChargeTier findMatchingTier(final Charge charge, final BigDecimal baseAmount) {
        final List<ChargeTier> tiers = charge.getChargeTiers().stream()
                .sorted(Comparator.comparing(ChargeTier::getAmountRangeFrom, Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
        final BigDecimal base = baseAmount == null ? BigDecimal.ZERO : baseAmount;
        for (final ChargeTier tier : tiers) {
            if (tier.matches(base)) {
                return tier;
            }
        }
        throw new ChargeTierNotFoundException(charge.getId(), base);
    }

    private static BigDecimal percentageOf(final BigDecimal value, final BigDecimal percentage) {
        if (value == null || percentage == null) {
            return BigDecimal.ZERO;
        }
        return value.multiply(percentage).divide(BigDecimal.valueOf(100L), RoundingMode.HALF_EVEN);
    }
}
