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

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The arithmetic of ratios: eligible amounts after exclusions, haircut, weight and sign; the ratio in percent; its
 * status; the LCR-style net outflows; and the part of a holding that counts.
 */
public final class RatioCalculator {

    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Inflows count up to this share of outflows in NET_OUTFLOWS. */
    static final BigDecimal INFLOW_CAP = new BigDecimal("0.75");

    private RatioCalculator() {}

    /** {@code sign x (source - excluded) x (100 - haircut)% x weight%}, rounded to the currency. */
    public static BigDecimal eligible(final BigDecimal source, final BigDecimal excluded, final BigDecimal haircut, final BigDecimal weight,
            final int sign, final int scale) {
        return source.subtract(excluded).multiply(HUNDRED.subtract(haircut)).multiply(weight).multiply(BigDecimal.valueOf(sign))
                .divide(HUNDRED.multiply(HUNDRED), scale, RoundingMode.HALF_UP);
    }

    /** Numerator over denominator in percent, four decimals; null when the denominator is zero. */
    public static BigDecimal ratio(final BigDecimal numerator, final BigDecimal denominator) {
        return denominator.signum() == 0 ? null : numerator.multiply(HUNDRED).divide(denominator, 4, RoundingMode.HALF_UP);
    }

    /**
     * MIN: below the limit BREACH, below the warning WARNING. MAX: above the limit BREACH, above the warning WARNING.
     * No result (zero denominator) is a BREACH.
     */
    public static RatioStatus status(final RatioDirection direction, final BigDecimal result, final BigDecimal limit,
            final BigDecimal warning) {
        if (result == null) {
            return RatioStatus.BREACH;
        }
        if (direction == RatioDirection.MIN) {
            return result.compareTo(limit) < 0 ? RatioStatus.BREACH
                    : warning != null && result.compareTo(warning) < 0 ? RatioStatus.WARNING : RatioStatus.OK;
        }
        return result.compareTo(limit) > 0 ? RatioStatus.BREACH
                : warning != null && result.compareTo(warning) > 0 ? RatioStatus.WARNING : RatioStatus.OK;
    }

    /** Outflows less inflows, inflows counting up to 75% of outflows. */
    public static BigDecimal netOutflows(final BigDecimal inflows, final BigDecimal outflows) {
        return outflows.subtract(inflows.min(outflows.multiply(INFLOW_CAP)));
    }

    /** The value of a holding and what is excluded from it. */
    public record HoldingValue(BigDecimal value, BigDecimal excludedMaturity, BigDecimal excludedPledged) {
    }

    /**
     * A holding maturing after the cut-off is excluded whole; otherwise the pledged share of its nominal is excluded
     * from its value.
     *
     * @param remainingDays
     *            days from the as-of date to maturity
     * @param cutoffDays
     *            the asset class's maximum remaining maturity, or null for none
     */
    public static HoldingValue holding(final BigDecimal value, final BigDecimal nominal, final BigDecimal pledged, final long remainingDays,
            final Integer cutoffDays, final int scale) {
        if (cutoffDays != null && remainingDays > cutoffDays) {
            return new HoldingValue(value, value, BigDecimal.ZERO);
        }
        if (pledged.signum() <= 0 || nominal.signum() <= 0) {
            return new HoldingValue(value, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        final BigDecimal share = pledged.min(nominal);
        return new HoldingValue(value, BigDecimal.ZERO, value.multiply(share).divide(nominal, scale, RoundingMode.HALF_UP));
    }
}
