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
package org.apache.fineract.treasury.fx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * The net open position by the shorthand method: each foreign currency's net position in base, long or short; the
 * aggregate is the greater of the sum of the longs and the sum of the shorts. Percentages are of core capital, 4
 * decimals; none without it.
 */
public final class NetOpenPosition {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private NetOpenPosition() {}

    public record Aggregate(BigDecimal longs, BigDecimal shorts, BigDecimal nop, BigDecimal percent) {
    }

    public static Aggregate aggregate(final Collection<BigDecimal> netBase, final BigDecimal coreCapital) {
        BigDecimal longs = BigDecimal.ZERO;
        BigDecimal shorts = BigDecimal.ZERO;
        for (final BigDecimal net : netBase) {
            if (net == null) {
                continue;
            }
            if (net.signum() > 0) {
                longs = longs.add(net);
            } else {
                shorts = shorts.add(net.negate());
            }
        }
        final BigDecimal nop = longs.max(shorts);
        return new Aggregate(longs, shorts, nop, percent(nop, coreCapital));
    }

    /** A single currency's position, long or short, as a percentage of core capital. */
    public static BigDecimal single(final BigDecimal netBase, final BigDecimal coreCapital) {
        return netBase == null ? null : percent(netBase.abs(), coreCapital);
    }

    static BigDecimal percent(final BigDecimal amount, final BigDecimal coreCapital) {
        if (coreCapital == null || coreCapital.signum() <= 0) {
            return null;
        }
        return amount.multiply(HUNDRED).divide(coreCapital, 4, RoundingMode.HALF_UP);
    }
}
