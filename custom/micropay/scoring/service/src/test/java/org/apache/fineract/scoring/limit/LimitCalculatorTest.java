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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.scoring.domain.ScoringLimitBand;
import org.apache.fineract.scoring.domain.ScoringLimitPolicy;
import org.apache.fineract.scoring.domain.ScoringOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Agent float loans: grade A gets twice the average monthly deposits up to UGX 5m, grade B once up to 2m, grade C a
 * flat 500k, grades D and E nothing. Limits are rounded down to 10,000.
 */
class LimitCalculatorTest {

    private static final String BASE = "SAVINGS_MONTHLY_DEPOSITS_6M";

    private ScoringLimitPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ScoringLimitPolicy();
        policy.setBaseCharacteristic(BASE);
        policy.setRoundingUnit(new BigDecimal("10000"));
        policy.addBand(ScoringLimitBand.create("A", new BigDecimal("2"), new BigDecimal("5000000")));
        policy.addBand(ScoringLimitBand.create("B", BigDecimal.ONE, new BigDecimal("2000000")));
        policy.addBand(ScoringLimitBand.create("C", null, new BigDecimal("500000")));
    }

    @Test
    void multiplierOfBaseIsRoundedDown() {
        assertEquals(0, new BigDecimal("2460000").compareTo(calculate("A", ScoringOutcome.APPROVE, "1234567")));
    }

    @Test
    void bandMaximumCapsTheLimit() {
        assertEquals(0, new BigDecimal("5000000").compareTo(calculate("A", ScoringOutcome.APPROVE, "9000000")));
        assertEquals(0, new BigDecimal("2000000").compareTo(calculate("b", ScoringOutcome.APPROVE, "9000000")));
    }

    @Test
    void bandWithoutMultiplierGivesItsMaximum() {
        assertEquals(0, new BigDecimal("500000").compareTo(calculate("C", ScoringOutcome.REFER, "100")));
    }

    @Test
    void gradeWithoutBandGetsNothing() {
        assertEquals(0, BigDecimal.ZERO.compareTo(calculate("D", ScoringOutcome.APPROVE, "9000000")));
        assertEquals(0, BigDecimal.ZERO.compareTo(calculate(null, ScoringOutcome.REFER, "9000000")));
    }

    @Test
    void declineGetsNothingWhateverTheGrade() {
        assertEquals(0, BigDecimal.ZERO.compareTo(calculate("A", ScoringOutcome.DECLINE, "9000000")));
    }

    @Test
    void missingBaseGetsNothing() {
        assertEquals(0, BigDecimal.ZERO.compareTo(calculate("A", ScoringOutcome.APPROVE, null)));
    }

    private BigDecimal calculate(final String grade, final ScoringOutcome outcome, final String base) {
        final Map<String, Object> inputs = new HashMap<>();
        inputs.put(BASE, base == null ? null : new BigDecimal(base));
        return LimitCalculator.calculate(policy, grade, outcome, inputs);
    }
}
