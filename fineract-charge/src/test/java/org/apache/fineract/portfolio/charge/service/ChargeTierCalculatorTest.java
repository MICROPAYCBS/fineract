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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTier;
import org.apache.fineract.portfolio.charge.exception.ChargeTierNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChargeTierCalculatorTest {

    private Charge charge;

    @BeforeEach
    void setUp() {
        charge = mock(Charge.class);
        when(charge.getId()).thenReturn(1L);
        when(charge.getChargeCalculation()).thenReturn(ChargeCalculationType.FLAT.getValue());

        final Set<ChargeTier> tiers = new HashSet<>();
        tiers.add(ChargeTier.create(charge, BigDecimal.ZERO, new BigDecimal("100000"), new BigDecimal("50")));
        tiers.add(ChargeTier.create(charge, new BigDecimal("100000"), null, new BigDecimal("100")));
        when(charge.getChargeTiers()).thenReturn(tiers);
        when(charge.isUseChargeTiers()).thenReturn(true);
        when(charge.isTiered()).thenReturn(true);
    }

    @Test
    void lookupSelectsMiddleBandForFlat() {
        final BigDecimal result = ChargeTierCalculator.resolveTierAmountOrPercentage(charge, new BigDecimal("50000"));
        assertEquals(0, new BigDecimal("50").compareTo(result));
    }

    @Test
    void lookupSelectsOpenEndedBand() {
        final BigDecimal result = ChargeTierCalculator.resolveTierAmountOrPercentage(charge, new BigDecimal("150000"));
        assertEquals(0, new BigDecimal("100").compareTo(result));
    }

    @Test
    void lookupBoundaryUsesUpperTierWhenExclusive() {
        final BigDecimal result = ChargeTierCalculator.resolveTierAmountOrPercentage(charge, new BigDecimal("100000"));
        assertEquals(0, new BigDecimal("100").compareTo(result));
    }

    @Test
    void percentageResolvesFinalAmount() {
        when(charge.getChargeCalculation()).thenReturn(ChargeCalculationType.PERCENT_OF_AMOUNT.getValue());
        final Set<ChargeTier> tiers = new HashSet<>();
        tiers.add(ChargeTier.create(charge, BigDecimal.ZERO, new BigDecimal("1000"), new BigDecimal("1")));
        tiers.add(ChargeTier.create(charge, new BigDecimal("1000"), null, new BigDecimal("2")));
        when(charge.getChargeTiers()).thenReturn(tiers);

        final BigDecimal result = ChargeTierCalculator.resolveChargeAmount(charge, new BigDecimal("2000"));
        assertEquals(0, new BigDecimal("40").compareTo(result));
    }

    @Test
    void noMatchingTierThrows() {
        final Set<ChargeTier> tiers = new HashSet<>();
        tiers.add(ChargeTier.create(charge, new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("5")));
        when(charge.getChargeTiers()).thenReturn(tiers);

        assertThrows(ChargeTierNotFoundException.class,
                () -> ChargeTierCalculator.resolveTierAmountOrPercentage(charge, BigDecimal.ONE));
    }

    @Test
    void flatLookupIsNotProgressive() {
        // base in open band must return only that band's amount (100), not 50+100
        final BigDecimal result = ChargeTierCalculator.resolveChargeAmount(charge, new BigDecimal("150000"));
        assertEquals(0, new BigDecimal("100").compareTo(result));
    }
}
