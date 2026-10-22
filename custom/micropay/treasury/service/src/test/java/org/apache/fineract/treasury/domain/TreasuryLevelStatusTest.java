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
package org.apache.fineract.treasury.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TreasuryLevelStatusTest {

    private static final BigDecimal MIN = new BigDecimal("1000");
    private static final BigDecimal MAX = new BigDecimal("5000");

    @Test
    void balanceWithinLevelsIsOk() {
        assertEquals(TreasuryLevelStatus.OK, TreasuryLevelStatus.of(new BigDecimal("2500"), MIN, MAX));
    }

    @Test
    void boundsAreInclusive() {
        assertEquals(TreasuryLevelStatus.OK, TreasuryLevelStatus.of(new BigDecimal("1000.000000"), MIN, MAX));
        assertEquals(TreasuryLevelStatus.OK, TreasuryLevelStatus.of(new BigDecimal("5000"), MIN, MAX));
    }

    @Test
    void balanceUnderMinimumIsBelowMin() {
        assertEquals(TreasuryLevelStatus.BELOW_MIN, TreasuryLevelStatus.of(new BigDecimal("999.999999"), MIN, MAX));
    }

    @Test
    void balanceOverMaximumIsAboveMax() {
        assertEquals(TreasuryLevelStatus.ABOVE_MAX, TreasuryLevelStatus.of(new BigDecimal("5000.000001"), MIN, MAX));
    }

    @Test
    void missingBoundsAreNotChecked() {
        assertEquals(TreasuryLevelStatus.OK, TreasuryLevelStatus.of(new BigDecimal("-10"), null, null));
        assertEquals(TreasuryLevelStatus.OK, TreasuryLevelStatus.of(new BigDecimal("1000000"), MIN, null));
        assertEquals(TreasuryLevelStatus.BELOW_MIN, TreasuryLevelStatus.of(new BigDecimal("-10"), BigDecimal.ZERO, null));
        assertEquals(TreasuryLevelStatus.ABOVE_MAX, TreasuryLevelStatus.of(new BigDecimal("6000"), null, MAX));
    }

    @Test
    void missingBalanceCountsAsZero() {
        assertEquals(TreasuryLevelStatus.BELOW_MIN, TreasuryLevelStatus.of(null, MIN, MAX));
        assertEquals(TreasuryLevelStatus.OK, TreasuryLevelStatus.of(null, null, MAX));
    }

    @Test
    void onlyOkIsNotABreach() {
        assertFalse(TreasuryLevelStatus.OK.isBreach());
        assertTrue(TreasuryLevelStatus.BELOW_MIN.isBreach());
        assertTrue(TreasuryLevelStatus.ABOVE_MAX.isBreach());
    }
}
