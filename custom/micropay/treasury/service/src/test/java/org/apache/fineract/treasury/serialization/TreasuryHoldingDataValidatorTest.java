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
package org.apache.fineract.treasury.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.deal.PledgePurpose;
import org.apache.fineract.treasury.deal.PriceSource;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator.EclParameterRequest;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator.EclStageRequest;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator.PledgeRequest;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator.PriceRequest;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator.ReleasePledgeRequest;
import org.junit.jupiter.api.Test;

class TreasuryHoldingDataValidatorTest {

    private final TreasuryHoldingDataValidator validator = new TreasuryHoldingDataValidator(new FromJsonHelper());

    @Test
    void pledgeReadsEveryField() {
        final PledgeRequest request = this.validator.validatePledge("""
                {"pledgeeCounterpartyId": 9, "purpose": "repo", "amount": 300000000, "linkedDealId": 50,
                 "startDate": "20 October 2026", "expectedEndDate": "31 December 2026", "reason": "Repo with BoU",
                 "dateFormat": "dd MMMM yyyy", "locale": "en"}
                """);

        assertEquals(9L, request.pledgeeCounterpartyId());
        assertEquals(PledgePurpose.REPO, request.purpose());
        assertEquals(0, new BigDecimal("300000000").compareTo(request.amount()));
        assertEquals(50L, request.linkedDealId());
        assertEquals(LocalDate.of(2026, 10, 20), request.startDate());
        assertEquals(LocalDate.of(2026, 12, 31), request.expectedEndDate());
        assertEquals("Repo with BoU", request.reason());
    }

    @Test
    void pledgeNeedsPledgeePurposeAndAPositiveAmountAndEndsAfterItStarts() {
        assertEquals(List.of("pledgeeCounterpartyId", "purpose", "amount"),
                parameters(() -> this.validator.validatePledge("{\"purpose\": \"LOAN\", \"amount\": 0}")));
        assertEquals(List.of("expectedEndDate"), parameters(() -> this.validator.validatePledge(
                "{\"pledgeeCounterpartyId\": 9, \"purpose\": \"OTHER\", \"amount\": 1, \"startDate\": \"2026-10-20\", \"expectedEndDate\": \"2026-10-19\"}")));
        assertThrows(UnsupportedParameterException.class, () -> this.validator
                .validatePledge("{\"pledgeeCounterpartyId\": 9, \"purpose\": \"OTHER\", \"amount\": 1, \"haircut\": 5}"));
    }

    @Test
    void releaseNeedsThePledgeAndAReason() {
        final ReleasePledgeRequest request = this.validator
                .validateReleasePledge("{\"pledgeId\": 3, \"releaseDate\": \"2026-11-30\", \"reason\": \"Repo closed\"}");
        assertEquals(3L, request.pledgeId());
        assertEquals(LocalDate.of(2026, 11, 30), request.releaseDate());
        assertEquals("Repo closed", request.reason());
        assertEquals(List.of("pledgeId", "reason"), parameters(() -> this.validator.validateReleasePledge("{}")));
    }

    @Test
    void eclStageReadsTheOverrides() {
        final EclStageRequest request = this.validator.validateEclStage("""
                {"stage": 3, "reason": "Default", "manualPd": 100, "manualLgd": 60, "manualEcl": 2500000,
                 "manualEclReason": "Liquidator", "clearOverrides": true, "locale": "en"}
                """);
        assertEquals(3, request.stage());
        assertEquals("Default", request.reason());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(request.manualPd()));
        assertEquals(0, BigDecimal.valueOf(60).compareTo(request.manualLgd()));
        assertEquals(0, BigDecimal.valueOf(2500000).compareTo(request.manualEcl()));
        assertEquals("Liquidator", request.manualEclReason());
        assertTrue(request.clearOverrides());

        final EclStageRequest plain = this.validator.validateEclStage("{\"stage\": 2, \"reason\": \"Watch\"}");
        assertNull(plain.manualPd());
        assertFalse(plain.clearOverrides());
    }

    @Test
    void eclStageIsOneToThreeAndAManualEclNeedsAReason() {
        assertEquals(List.of("stage", "reason"), parameters(() -> this.validator.validateEclStage("{\"stage\": 4}")));
        assertEquals(List.of("manualPd", "manualLgd"), parameters(
                () -> this.validator.validateEclStage("{\"stage\": 1, \"reason\": \"x\", \"manualPd\": 101, \"manualLgd\": -1}")));
        assertEquals(List.of("manualEclReason"),
                parameters(() -> this.validator.validateEclStage("{\"stage\": 3, \"reason\": \"x\", \"manualEcl\": 10}")));
    }

    @Test
    void recalculateTakesAnOptionalNote() {
        assertNull(this.validator.validateRecalculateEcl(""));
        assertEquals("Month end", this.validator.validateRecalculateEcl("{\"note\": \"Month end\"}"));
        assertThrows(UnsupportedParameterException.class, () -> this.validator.validateRecalculateEcl("{\"stage\": 1}"));
    }

    @Test
    void eclParameterReadsEveryField() {
        final EclParameterRequest request = this.validator
                .validateEclParameter("{\"ratingCode\": \"aa-\", \"stage\": 2, \"pd12Month\": 0.5, \"pdLifetime\": 4, \"lgd\": 45}");
        assertEquals("AA-", request.ratingCode());
        assertEquals(2, request.stage());
        assertEquals(0, new BigDecimal("0.5").compareTo(request.pd12Month()));
        assertEquals(0, new BigDecimal("4").compareTo(request.pdLifetime()));
        assertEquals(0, new BigDecimal("45").compareTo(request.lgd()));
        assertEquals(List.of("ratingCode", "stage", "pd12Month", "pdLifetime", "lgd"),
                parameters(() -> this.validator.validateEclParameter("{\"stage\": 0}")));
        assertEquals(List.of("pdLifetime"), parameters(() -> this.validator
                .validateEclParameter("{\"ratingCode\": \"DEFAULT\", \"stage\": 1, \"pd12Month\": 2, \"pdLifetime\": 1, \"lgd\": 45}")));
    }

    @Test
    void onePriceOrABatch() {
        final List<PriceRequest> one = this.validator.validatePrices(
                "{\"instrumentId\": \"UG0000001234\", \"priceDate\": \"2026-10-30\", \"cleanPrice\": 99.25, \"source\": \"bou_secondary\"}");
        assertEquals(List
                .of(new PriceRequest("UG0000001234", LocalDate.of(2026, 10, 30), new BigDecimal("99.25"), null, PriceSource.BOU_SECONDARY)),
                one);

        final List<PriceRequest> batch = this.validator.validatePrices("""
                {"dateFormat": "dd/MM/yyyy", "prices": [
                  {"instrumentId": "UG0000001234", "priceDate": "30/10/2026", "cleanPrice": 99.25},
                  {"instrumentId": "TB-2027-01-15", "priceDate": "30/10/2026", "yield": 10.5, "source": "BOU_AUCTION"}]}
                """);
        assertEquals(2, batch.size());
        assertEquals(PriceSource.MANUAL, batch.getFirst().source());
        assertEquals(0, new BigDecimal("10.5").compareTo(batch.getLast().yield()));
        assertEquals(LocalDate.of(2026, 10, 30), batch.getLast().priceDate());
    }

    @Test
    void aPriceNeedsInstrumentDateAndAPriceOrYield() {
        assertEquals(List.of("instrumentId", "priceDate", "cleanPrice"), parameters(() -> this.validator.validatePrices("{}")));
        assertEquals(List.of("prices[1].yield", "prices[1].source"),
                parameters(() -> this.validator
                        .validatePrices("{\"prices\": [{\"instrumentId\": \"A\", \"priceDate\": \"2026-10-30\", \"cleanPrice\": 1}, "
                                + "{\"instrumentId\": \"B\", \"priceDate\": \"2026-10-30\", \"yield\": 150, \"source\": \"RUMOUR\"}]}")));
        assertEquals(List.of("prices"), parameters(() -> this.validator.validatePrices("{\"prices\": []}")));
        assertThrows(UnsupportedParameterException.class, () -> this.validator
                .validatePrices("{\"instrumentId\": \"A\", \"priceDate\": \"2026-10-30\", \"cleanPrice\": 1, \"currency\": \"UGX\"}"));
    }

    private static List<String> parameters(final org.junit.jupiter.api.function.Executable call) {
        return assertThrows(PlatformApiDataValidationException.class, call).getErrors().stream().map(ApiParameterError::getParameterName)
                .distinct().toList();
    }
}
