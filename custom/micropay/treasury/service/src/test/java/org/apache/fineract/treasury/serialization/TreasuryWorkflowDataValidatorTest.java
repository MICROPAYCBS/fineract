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
import org.apache.fineract.treasury.domain.TreasuryFloatRequestType;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator.ActionRequest;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator.SettingRequest;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator.TransferRequest;
import org.junit.jupiter.api.Test;

class TreasuryWorkflowDataValidatorTest {

    private final TreasuryWorkflowDataValidator validator = new TreasuryWorkflowDataValidator(new FromJsonHelper());

    @Test
    void transferIsParsed() {
        final TransferRequest request = this.validator.validateTransferForCreate(
                "{\"fromAccountId\": 1, \"toAccountId\": 2, \"amount\": 2500000.5, \"transferDate\": \"2026-10-16\", \"reference\": \" BOU-77 \"}");

        assertEquals(new BigDecimal("2500000.5"), request.amount());
        assertEquals(LocalDate.of(2026, 10, 16), request.transferDate());
        assertEquals("BOU-77", request.reference());
    }

    @Test
    void transferDateFollowsTheDateFormatWhenSent() {
        final TransferRequest request = this.validator.validateTransferForCreate(
                "{\"fromAccountId\": 1, \"toAccountId\": 2, \"amount\": 1, \"transferDate\": \"16 October 2026\", \"dateFormat\": \"dd MMMM yyyy\", \"locale\": \"en\"}");

        assertEquals(LocalDate.of(2026, 10, 16), request.transferDate());
    }

    @Test
    void transferToTheSameAccountIsRejected() {
        assertEquals(List.of("toAccountId"), parameters(assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateTransferForCreate("{\"fromAccountId\": 1, \"toAccountId\": 1, \"amount\": 1}"))));
    }

    @Test
    void transferAmountMustBePositive() {
        assertEquals(List.of("amount"), parameters(assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateTransferForCreate("{\"fromAccountId\": 1, \"toAccountId\": 2, \"amount\": 0}"))));
    }

    @Test
    void badDateIsRejected() {
        assertEquals(List.of("transferDate"),
                parameters(assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateTransferForCreate(
                        "{\"fromAccountId\": 1, \"toAccountId\": 2, \"amount\": 1, \"transferDate\": \"16/10/2026\"}"))));
    }

    @Test
    void emptyActionBodyIsAllowed() {
        assertEquals(ActionRequest.EMPTY, this.validator.validateAction("TREASURYTRANSFER", ""));
        final ActionRequest action = this.validator.validateAction("TREASURYTRANSFER", "{}");
        assertNull(action.transactionDate());
    }

    @Test
    void actionRejectsUnknownFields() {
        assertThrows(UnsupportedParameterException.class, () -> this.validator.validateAction("TREASURYTRANSFER", "{\"amount\": 1}"));
    }

    @Test
    void settingNeedsSomethingToChange() {
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateSetting("{\"currencyCode\": \"UGX\"}"));
    }

    @Test
    void matchingWindowNeedsNoLocale() {
        final SettingRequest request = this.validator
                .validateSetting("{\"currencyCode\": \"UGX\", \"reconWindowDays\": 5, \"reconSameDateRule\": false}");

        assertEquals(5, request.reconWindowDays());
        assertEquals(Boolean.FALSE, request.reconSameDateRule());
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateSetting("{\"currencyCode\": \"UGX\", \"reconWindowDays\": 40}"));
    }

    @Test
    void stalePriceDaysNeedsNoLocale() {
        final SettingRequest request = this.validator.validateSetting("{\"currencyCode\": \"UGX\", \"stalePriceDays\": 10}");

        assertEquals(10, request.stalePriceDays());
        assertTrue(request.has("stalePriceDays"));
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateSetting("{\"currencyCode\": \"UGX\", \"stalePriceDays\": -1}"));
    }

    @Test
    void settingKnowsWhichFieldsWereSent() {
        final SettingRequest request = this.validator.validateSetting("{\"currencyCode\": \"ugx\", \"cashInTransitGlAccountId\": null}");

        assertEquals("UGX", request.currencyCode());
        assertTrue(request.has("cashInTransitGlAccountId"));
        assertNull(request.cashInTransitGlAccountId());
    }

    @Test
    void floatRangeIsChecked() {
        assertEquals(List.of("minFloat"),
                parameters(assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateAgentFloatForCreate(
                        "{\"clientId\": 1, \"savingsAccountId\": 2, \"treasuryAccountId\": 3, \"minFloat\": 10, \"maxFloat\": 5}"))));
    }

    @Test
    void floatRequestTypeIsParsed() {
        assertEquals(TreasuryFloatRequestType.SWEEP,
                this.validator.validateFloatRequestForCreate("{\"requestType\": \"sweep\", \"amount\": 100}").requestType());
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateFloatRequestForCreate("{\"requestType\": \"LOAN\", \"amount\": 100}"));
    }

    private static List<String> parameters(final PlatformApiDataValidationException exception) {
        return exception.getErrors().stream().map(ApiParameterError::getParameterName).distinct().toList();
    }
}
