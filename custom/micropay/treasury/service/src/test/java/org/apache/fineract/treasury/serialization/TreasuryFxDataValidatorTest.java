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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.fx.FxRateSource;
import org.apache.fineract.treasury.fx.FxRateType;
import org.apache.fineract.treasury.serialization.TreasuryFxDataValidator.ConfigRequest;
import org.apache.fineract.treasury.serialization.TreasuryFxDataValidator.CoreCapitalRequest;
import org.apache.fineract.treasury.serialization.TreasuryFxDataValidator.RateRequest;
import org.apache.fineract.treasury.serialization.TreasuryFxDataValidator.RunRequest;
import org.junit.jupiter.api.Test;

/** Every field of rates (one or a batch), revaluation settings, runs and core capital. */
class TreasuryFxDataValidatorTest {

    private final TreasuryFxDataValidator validator = new TreasuryFxDataValidator(new FromJsonHelper());

    @Test
    void oneRate() {
        assertEquals(List.of(new RateRequest("UGX", "USD", LocalDate.of(2026, 10, 16), FxRateType.SELL, new BigDecimal("3755.25"),
                FxRateSource.BOU, "Noon fixing")), this.validator.validateRates("""
                        {"baseCurrency": "ugx", "quoteCurrency": "usd", "rateDate": "16 October 2026", "dateFormat": "dd MMMM yyyy",
                         "rateType": "sell", "rate": 3755.25, "source": "bou", "note": "Noon fixing", "locale": "en"}
                        """));
        final RateRequest defaults = this.validator
                .validateRates("{\"quoteCurrency\": \"USD\", \"rateDate\": \"2026-10-16\", \"rate\": 3750}").getFirst();
        assertNull(defaults.baseCurrency());
        assertEquals(FxRateType.MID, defaults.rateType());
        assertEquals(FxRateSource.MANUAL, defaults.source());
    }

    @Test
    void aBatchIsImportedByDefault() {
        final List<RateRequest> batch = this.validator.validateRates("""
                {"rates": [{"quoteCurrency": "USD", "rateDate": "2026-10-15", "rate": 3745},
                           {"quoteCurrency": "EUR", "rateDate": "2026-10-15", "rate": 4050.5, "source": "MANUAL"}]}
                """);
        assertEquals(2, batch.size());
        assertEquals(FxRateSource.IMPORT, batch.get(0).source());
        assertEquals(FxRateSource.MANUAL, batch.get(1).source());
        assertEquals(List.of("rates"), parameters(() -> this.validator.validateRates("{\"rates\": []}")));
        assertEquals(List.of("rates"), parameters(() -> this.validator.validateRates("{\"rates\": [{}], \"quoteCurrency\": \"USD\"}")));
        assertEquals(List.of("rates[0].quoteCurrency", "rates[0].rateDate", "rates[0].rate"),
                parameters(() -> this.validator.validateRates("{\"rates\": [{}]}")));
        assertThrows(UnsupportedParameterException.class, () -> this.validator.validateRates("{\"rates\": [{\"price\": 1}]}"));
    }

    @Test
    void rateRules() {
        assertEquals(List.of("quoteCurrency", "rateDate", "rate"), parameters(() -> this.validator.validateRates("{}")));
        assertEquals(List.of("rate"), parameters(() -> rate("0")));
        assertEquals(List.of("rate"), parameters(() -> rate("-1")));
        assertEquals(List.of("rate"), parameters(() -> rate("1.12345678901")));
        assertEquals(List.of("rateType"), parameters(() -> this.validator
                .validateRates("{\"quoteCurrency\": \"USD\", \"rateDate\": \"2026-10-16\", \"rate\": 1, \"rateType\": \"SPOT\"}")));
        assertEquals(List.of("quoteCurrency"), parameters(() -> this.validator
                .validateRates("{\"baseCurrency\": \"UGX\", \"quoteCurrency\": \"UGX\", \"rateDate\": \"2026-10-16\", \"rate\": 1}")));
        assertEquals(List.of("rateDate"),
                parameters(() -> this.validator.validateRates("{\"quoteCurrency\": \"USD\", \"rateDate\": \"16/10/2026\", \"rate\": 1}")));
    }

    @Test
    void revaluationSettingsRunsAndCoreCapital() {
        assertEquals(new ConfigRequest(11L, "USD", 12L, 13L, 14L, true, null), this.validator.validateConfig(
                "{\"glAccountId\": 11, \"currencyCode\": \"usd\", \"adjustmentGlAccountId\": 12, \"gainGlAccountId\": 13, \"lossGlAccountId\": 14}"));
        assertFalse(this.validator.validateConfig("{\"glAccountId\": 11, \"currencyCode\": \"USD\", \"adjustmentGlAccountId\": 12, "
                + "\"gainGlAccountId\": 13, \"lossGlAccountId\": 14, \"active\": false, \"note\": \"x\"}").active());
        assertEquals(List.of("glAccountId", "currencyCode", "adjustmentGlAccountId", "gainGlAccountId", "lossGlAccountId"),
                parameters(() -> this.validator.validateConfig("{}")));
        assertEquals(List.of("adjustmentGlAccountId"), parameters(() -> this.validator.validateConfig(
                "{\"glAccountId\": 11, \"currencyCode\": \"USD\", \"adjustmentGlAccountId\": 12, \"gainGlAccountId\": 13, \"lossGlAccountId\": 13}")));
        assertEquals(List.of("active"), parameters(() -> this.validator.validateConfig(
                "{\"glAccountId\": 11, \"currencyCode\": \"USD\", \"adjustmentGlAccountId\": 12, \"gainGlAccountId\": 13, \"lossGlAccountId\": 14, \"active\": \"yes\"}")));
        assertEquals(new RunRequest(LocalDate.of(2026, 10, 16), "USD"),
                this.validator.validateRun("{\"asOfDate\": \"2026-10-16\", \"currencyCode\": \"usd\"}"));
        assertEquals(new RunRequest(null, null), this.validator.validateRun(""));
        assertEquals(new CoreCapitalRequest(LocalDate.of(2026, 1, 1), new BigDecimal("12000000000"), "Audited 2025"), this.validator
                .validateCoreCapital("{\"effectiveDate\": \"2026-01-01\", \"amount\": 12000000000, \"note\": \"Audited 2025\"}"));
        assertEquals(List.of("effectiveDate", "amount"), parameters(() -> this.validator.validateCoreCapital("{}")));
        assertEquals(List.of("amount"),
                parameters(() -> this.validator.validateCoreCapital("{\"effectiveDate\": \"2026-01-01\", \"amount\": 0}")));
        assertEquals("n", this.validator.validateNote("{\"note\": \"n\"}"));
    }

    private List<RateRequest> rate(final String value) {
        return this.validator.validateRates("{\"quoteCurrency\": \"USD\", \"rateDate\": \"2026-10-16\", \"rate\": " + value + "}");
    }

    private static List<String> parameters(final org.junit.jupiter.api.function.Executable call) {
        return assertThrows(PlatformApiDataValidationException.class, call).getErrors().stream().map(ApiParameterError::getParameterName)
                .distinct().toList();
    }
}
