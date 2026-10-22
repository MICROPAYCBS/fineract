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
package org.apache.fineract.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.service.DealValuationPlanner.Release;
import org.apache.fineract.treasury.service.DealValuationPlanner.ValuationGl;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;
import org.junit.jupiter.api.Test;

class DealValuationPlannerTest {

    private static final long OFFICE = 1L;
    /** adjustment 40, gain 41, loss 42, reserve 43, impairment 44, allowance 45, realised gain 29, realised loss 30 */
    private static final ValuationGl GL = new ValuationGl(40L, 41L, 42L, 43L, 44L, 45L, 29L, 30L);

    @Test
    void fvtplRiseAndFallGoToProfitOrLoss() {
        assertEquals(List.of(leg(40, true, "793621.57"), leg(41, false, "793621.57")),
                DealValuationPlanner.revaluation(DealClassification.FVTPL, new BigDecimal("793621.57"), OFFICE, GL));
        assertEquals(List.of(leg(42, true, "200000"), leg(40, false, "200000")),
                DealValuationPlanner.revaluation(DealClassification.FVTPL, new BigDecimal("-200000"), OFFICE, GL));
    }

    @Test
    void fvociRiseAndFallGoToTheReserve() {
        assertEquals(List.of(leg(40, true, "480000"), leg(43, false, "480000")),
                DealValuationPlanner.revaluation(DealClassification.FVOCI, new BigDecimal("480000"), OFFICE, GL));
        assertEquals(List.of(leg(43, true, "825000"), leg(40, false, "825000")),
                DealValuationPlanner.revaluation(DealClassification.FVOCI, new BigDecimal("-825000"), OFFICE, GL));
        assertThrows(IllegalArgumentException.class,
                () -> DealValuationPlanner.revaluation(DealClassification.AMORTISED_COST, BigDecimal.ONE, OFFICE, GL));
    }

    @Test
    void lossAllowanceIsAContraAssetAtAmortisedCostAndInOciAtFvoci() {
        assertEquals(List.of(leg(44, true, "1141643.84"), leg(45, false, "1141643.84")),
                DealValuationPlanner.impairment(DealClassification.AMORTISED_COST, new BigDecimal("1141643.84"), OFFICE, GL));
        assertEquals(List.of(leg(43, true, "100"), leg(44, false, "100")),
                DealValuationPlanner.impairment(DealClassification.FVOCI, new BigDecimal("-100"), OFFICE, GL));
        assertThrows(IllegalArgumentException.class,
                () -> DealValuationPlanner.impairment(DealClassification.FVTPL, BigDecimal.ONE, OFFICE, GL));
    }

    @Test
    void fvtplReleaseMovesTheAdjustmentIntoTheRealisedResult() {
        final Release release = DealValuationPlanner.release(DealClassification.FVTPL, new BigDecimal("793621.57"), OFFICE, GL);

        assertEquals(List.of(leg(30, true, "793621.57"), leg(40, false, "793621.57")), release.legs());
        assertEquals(new BigDecimal("-793621.57"), release.realised());
        assertEquals(0, release.ociRecycled().signum());
    }

    @Test
    void fvociReleaseEmptiesTheReserve() {
        final Release release = DealValuationPlanner.release(DealClassification.FVOCI, new BigDecimal("-345000"), OFFICE, GL);

        assertEquals(List.of(leg(40, true, "345000"), leg(43, false, "345000")), release.legs());
        assertEquals(0, release.realised().signum());
        assertEquals(new BigDecimal("-345000"), release.ociRecycled());
        assertEquals(List.of(), DealValuationPlanner.release(DealClassification.FVOCI, BigDecimal.ZERO, OFFICE, GL).legs());
    }

    private static Leg leg(final long gl, final boolean debit, final String amount) {
        return new Leg(OFFICE, gl, debit ? JournalEntryType.DEBIT : JournalEntryType.CREDIT, new BigDecimal(amount));
    }
}
