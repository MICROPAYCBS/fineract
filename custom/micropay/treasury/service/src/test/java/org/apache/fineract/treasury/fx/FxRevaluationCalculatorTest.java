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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.fineract.treasury.fx.FxRevaluationCalculator.Movement;
import org.apache.fineract.treasury.fx.FxRevaluationCalculator.Result;
import org.junit.jupiter.api.Test;

/**
 * Worked example: USD 1,000 in on 1 October at 3,700 and USD 500 on 10 October at 3,720; closing 3,750 on 16 October.
 * The target is 5,625,000; the movements carried 5,560,000; the gain 65,000.
 */
class FxRevaluationCalculatorTest {

    private static final LocalDate D01 = LocalDate.of(2026, 10, 1);
    private static final LocalDate D10 = LocalDate.of(2026, 10, 10);
    private static final Function<LocalDate, BigDecimal> RATES = Map.of(D01, new BigDecimal("3700"), D10, new BigDecimal("3720"))::get;
    private static final List<Movement> MOVEMENTS = List.of(new Movement(D01, new BigDecimal("1000")),
            new Movement(D10, new BigDecimal("500")));

    @Test
    void aGainIsTheTargetLessTheMovementsAtTheirOwnRates() {
        final Result result = FxRevaluationCalculator.revalue(MOVEMENTS, RATES, new BigDecimal("3750"), BigDecimal.ZERO, 2);
        assertEquals(0, new BigDecimal("1500").compareTo(result.balance()));
        assertEquals(new BigDecimal("5625000.00"), result.target());
        assertEquals(new BigDecimal("5560000.00"), result.movementsBase());
        assertEquals(new BigDecimal("65000.00"), result.adjustment());
        assertTrue(result.isGain());
    }

    @Test
    void aRerunPostsNothingAndARateChangeOnlyTheDifference() {
        final BigDecimal prior = new BigDecimal("65000.00");
        assertEquals(0, FxRevaluationCalculator.revalue(MOVEMENTS, RATES, new BigDecimal("3750"), prior, 2).adjustment().signum(),
                "nothing changed");
        final Result lower = FxRevaluationCalculator.revalue(MOVEMENTS, RATES, new BigDecimal("3740"), prior, 2);
        assertEquals(new BigDecimal("-15000.00"), lower.adjustment(), "1,500 x 10 down");
        assertFalse(lower.isGain());
        assertEquals(new BigDecimal("5625000.00"), lower.carried());
    }

    @Test
    void aLiabilityLosesWhenTheRateRises() {
        final Result result = FxRevaluationCalculator.revalue(List.of(new Movement(D01, new BigDecimal("-1000"))), RATES,
                new BigDecimal("3750"), BigDecimal.ZERO, 2);
        assertEquals(new BigDecimal("-3750000.00"), result.target());
        assertEquals(new BigDecimal("-50000.00"), result.adjustment());
        assertFalse(result.isGain(), "Dr loss, Cr adjustment");
    }

    @Test
    void eachMovementIsRoundedAtItsRate() {
        final Result result = FxRevaluationCalculator.revalue(
                List.of(new Movement(D01, new BigDecimal("0.33")), new Movement(D10, new BigDecimal("0.33"))),
                d -> new BigDecimal("3700.5"), new BigDecimal("3700.5"), BigDecimal.ZERO, 2);
        assertEquals(new BigDecimal("2442.33"), result.target(), "0.66 x 3,700.5");
        assertEquals(new BigDecimal("2442.34"), result.movementsBase(), "1,221.17 twice");
        assertEquals(new BigDecimal("-0.01"), result.adjustment());
    }
}
