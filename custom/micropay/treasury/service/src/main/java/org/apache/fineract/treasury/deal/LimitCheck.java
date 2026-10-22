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
package org.apache.fineract.treasury.deal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A counterparty limit against its exposure with a new deal added.
 */
public final class LimitCheck {

    private LimitCheck() {}

    public enum Outcome {
        /** No limit set, or below the warning level. */
        OK,
        /** At or above the warning percentage of the limit, within the limit. */
        WARNING,
        /** Above the limit. */
        EXCEEDED
    }

    /**
     * @param utilisation
     *            exposure after the deal as a percentage of the limit, 2 decimals; {@code null} without a limit
     */
    public record Result(Outcome outcome, BigDecimal exposureAfter, BigDecimal utilisation, BigDecimal available) {
    }

    public static Result evaluate(final BigDecimal limit, final BigDecimal warningPercent, final BigDecimal currentExposure,
            final BigDecimal dealAmount) {
        final BigDecimal after = currentExposure.add(dealAmount);
        if (limit == null) {
            return new Result(Outcome.OK, after, null, null);
        }
        final BigDecimal utilisation = limit.signum() == 0 ? (after.signum() > 0 ? BigDecimal.valueOf(100_000) : BigDecimal.ZERO)
                : after.multiply(BigDecimal.valueOf(100)).divide(limit, 2, RoundingMode.HALF_UP);
        final Outcome outcome = after.compareTo(limit) > 0 ? Outcome.EXCEEDED
                : utilisation.compareTo(warningPercent) >= 0 ? Outcome.WARNING : Outcome.OK;
        return new Result(outcome, after, utilisation, limit.subtract(after));
    }
}
