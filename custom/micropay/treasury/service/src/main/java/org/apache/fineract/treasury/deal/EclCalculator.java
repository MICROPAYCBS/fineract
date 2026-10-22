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
 * IFRS 9 expected credit loss: exposure at default x probability of default x loss given default. PD and LGD are
 * percentages; stage 1 takes the 12-month PD, stages 2 and 3 the lifetime PD.
 */
public final class EclCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private EclCalculator() {}

    public static BigDecimal ecl(final BigDecimal exposure, final BigDecimal pdPercent, final BigDecimal lgdPercent, final int scale) {
        if (exposure.signum() <= 0) {
            return BigDecimal.ZERO.setScale(scale);
        }
        return exposure.multiply(pdPercent).multiply(lgdPercent).divide(HUNDRED.multiply(HUNDRED), scale, RoundingMode.HALF_UP);
    }

    /** The PD of the stage: 12-month for stage 1, lifetime for stages 2 and 3. */
    public static BigDecimal pd(final int stage, final BigDecimal pd12Month, final BigDecimal pdLifetime) {
        return stage == 1 ? pd12Month : pdLifetime;
    }

    public static boolean validStage(final int stage) {
        return stage >= 1 && stage <= 3;
    }
}
