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
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * Cumulative-target revaluation of a foreign currency GL balance (debit less credit) in an office. The target is the
 * balance at the closing MID rate; the carried base is each day's movement at its own day's MID rate plus the
 * revaluations already posted. The adjustment is their difference, so a rerun posts only what changed.
 */
public final class FxRevaluationCalculator {

    private FxRevaluationCalculator() {}

    /** The net movement (debit less credit) of a day. */
    public record Movement(LocalDate date, BigDecimal amount) {
    }

    public record Result(BigDecimal balance, BigDecimal target, BigDecimal movementsBase, BigDecimal priorAdjustments, BigDecimal carried,
            BigDecimal adjustment) {

        /**
         * Up in base: Dr the adjustment account, Cr the gain account; down: Dr the loss account, Cr the adjustment
         * account.
         */
        public boolean isGain() {
            return this.adjustment.signum() > 0;
        }
    }

    /**
     * @param rateOn
     *            the MID rate used for a movement on a date
     */
    public static Result revalue(final List<Movement> movements, final Function<LocalDate, BigDecimal> rateOn, final BigDecimal closingRate,
            final BigDecimal priorAdjustments, final int scale) {
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal movementsBase = BigDecimal.ZERO;
        for (final Movement movement : movements) {
            balance = balance.add(movement.amount());
            movementsBase = movementsBase.add(base(movement.amount(), rateOn.apply(movement.date()), scale));
        }
        final BigDecimal target = base(balance, closingRate, scale);
        final BigDecimal carried = movementsBase.add(priorAdjustments);
        return new Result(balance, target, movementsBase, priorAdjustments, carried, target.subtract(carried));
    }

    public static BigDecimal base(final BigDecimal amount, final BigDecimal rate, final int scale) {
        return amount.multiply(rate).setScale(scale, RoundingMode.HALF_UP);
    }
}
