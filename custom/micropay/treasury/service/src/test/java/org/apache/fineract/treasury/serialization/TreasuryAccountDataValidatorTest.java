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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.data.TreasuryAccountRequest;
import org.junit.jupiter.api.Test;

class TreasuryAccountDataValidatorTest {

    private static final String VALID = """
            {"code": "stanbic-ugx", "name": "Stanbic main", "accountType": "bank", "glAccountId": 12, "officeId": 1,
             "currencyCode": "ugx", "counterpartyName": "Stanbic Bank", "externalAccountNumber": "9030001234",
             "minBalance": 1000000, "maxBalance": 50000000}
            """;

    private final TreasuryAccountDataValidator validator = new TreasuryAccountDataValidator(new FromJsonHelper());

    @Test
    void validCreateIsNormalised() {
        final TreasuryAccountRequest request = this.validator.validateForCreate(VALID);

        assertEquals("STANBIC-UGX", request.getCode());
        assertEquals("BANK", request.getAccountType());
        assertEquals("UGX", request.getCurrencyCode());
        assertEquals(0, new BigDecimal("1000000").compareTo(request.getMinBalance()));
        assertTrue(request.has("maxBalance"));
    }

    @Test
    void createNeedsTheMandatoryFields() {
        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateForCreate("{\"name\": \"x\"}"));

        assertTrue(parameters(exception).containsAll(List.of("code", "accountType", "glAccountId", "officeId", "currencyCode")));
    }

    @Test
    void unknownAccountTypeIsRejected() {
        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateForCreate(VALID.replace("\"bank\"", "\"SAFE\"")));

        assertEquals(List.of("accountType"), parameters(exception));
    }

    @Test
    void currencyMustBeThreeLetters() {
        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateForCreate(VALID.replace("\"ugx\"", "\"UGXX\"")));

        assertEquals(List.of("currencyCode"), parameters(exception));
    }

    @Test
    void minimumAboveMaximumIsRejected() {
        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateForCreate(VALID.replace("50000000", "999999")));

        assertEquals(List.of("minBalance"), parameters(exception));
    }

    @Test
    void equalMinimumAndMaximumAreAllowed() {
        assertDoesNotThrow(() -> this.validator.validateForCreate(VALID.replace("50000000", "1000000")));
    }

    @Test
    void negativeLevelsAreRejected() {
        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateForCreate(VALID.replace("1000000,", "-1,")));

        assertEquals(List.of("minBalance"), parameters(exception));
    }

    @Test
    void unsupportedParameterIsRejected() {
        assertThrows(UnsupportedParameterException.class, () -> this.validator.validateForCreate(VALID.replace("{", "{\"balance\": 1,")));
    }

    @Test
    void emptyBodyIsRejected() {
        assertThrows(InvalidJsonException.class, () -> this.validator.validateForCreate(" "));
    }

    @Test
    void updateKnowsWhichFieldsWereSent() {
        final TreasuryAccountRequest request = this.validator.validateForUpdate("{\"maxBalance\": null, \"name\": \"Renamed\"}");

        assertTrue(request.has("maxBalance"));
        assertNull(request.getMaxBalance());
        assertTrue(request.has("name"));
        assertFalse(request.has("minBalance"));
    }

    @Test
    void codeCannotBeUpdated() {
        assertThrows(UnsupportedParameterException.class, () -> this.validator.validateForUpdate("{\"code\": \"OTHER\"}"));
    }

    @Test
    void updateNeedsAtLeastOneField() {
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateForUpdate("{}"));
    }

    @Test
    void mergedRangeIsChecked() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateBalanceRange(new BigDecimal("10"), new BigDecimal("9.99")));
        assertDoesNotThrow(() -> this.validator.validateBalanceRange(new BigDecimal("10"), null));
    }

    private static List<String> parameters(final PlatformApiDataValidationException exception) {
        return exception.getErrors().stream().map(ApiParameterError::getParameterName).distinct().toList();
    }
}
