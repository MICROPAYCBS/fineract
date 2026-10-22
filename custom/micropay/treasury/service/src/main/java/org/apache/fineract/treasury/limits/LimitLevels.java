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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Values against a limit's levels, and the shares concentration limits observe. */
public final class LimitLevels {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private LimitLevels() {}

    /**
     * MIN: below the hard level is HARD, below the warning level WARNING; MAX: above the hard level HARD, above the
     * warning level WARNING. A value on a level has not crossed it. No value, or no levels, is no level.
     */
    public static LimitLevel level(final LimitComparison comparison, final BigDecimal warning, final BigDecimal hard,
            final BigDecimal value) {
        if (value == null || comparison == null) {
            return null;
        }
        if (crossed(comparison, hard, value)) {
            return LimitLevel.HARD;
        }
        return crossed(comparison, warning, value) ? LimitLevel.WARNING : null;
    }

    private static boolean crossed(final LimitComparison comparison, final BigDecimal level, final BigDecimal value) {
        if (level == null) {
            return false;
        }
        return comparison == LimitComparison.MIN ? value.compareTo(level) < 0 : value.compareTo(level) > 0;
    }

    /** {@code part} as a percentage of {@code whole}, 4 decimals; none when the whole is zero. */
    public static BigDecimal percent(final BigDecimal part, final BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return null;
        }
        return part.multiply(HUNDRED).divide(whole, 4, RoundingMode.HALF_UP);
    }

    /** The share of the largest {@code topN} amounts in their total, in percent; positive amounts only. */
    public static Concentration concentration(final List<BigDecimal> amounts, final int topN) {
        final List<BigDecimal> positive = amounts.stream().filter(a -> a != null && a.signum() > 0).sorted((a, b) -> b.compareTo(a))
                .toList();
        final BigDecimal total = positive.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal top = positive.stream().limit(topN).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Concentration(top, total, positive.size(), percent(top, total));
    }

    /**
     * @param share
     *            the top amounts' share of the total, percent; null when the total is zero
     */
    public record Concentration(BigDecimal top, BigDecimal total, int holders, BigDecimal share) {
    }
}
