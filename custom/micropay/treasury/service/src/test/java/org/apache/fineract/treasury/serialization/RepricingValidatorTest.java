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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.liquidity.AssumptionType;
import org.apache.fineract.treasury.liquidity.Assumptions;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.junit.jupiter.api.Test;

/** Rate shocks of a repricing run and the repricing assumptions. */
class RepricingValidatorTest {

    private final TreasuryLiquidityDataValidator validator = new TreasuryLiquidityDataValidator(new FromJsonHelper());

    @Test
    void shocks() {
        assertEquals(List.of(200, -200), this.validator.validateRun("{\"type\": \"repricing\", \"currencyCode\": \"UGX\"}").shocksBp());
        assertEquals(List.of(100, -300, 50), this.validator
                .validateRun("{\"type\": \"REPRICING\", \"currencyCode\": \"UGX\", \"shocks\": \"100, -300,50,100\"}").shocksBp());
        assertEquals(List.of(400),
                this.validator.validateRun("{\"type\": \"REPRICING\", \"currencyCode\": \"UGX\", \"shocks\": [400]}").shocksBp());
        assertEquals(List.of("shocks"), parameters("{\"type\": \"REPRICING\", \"currencyCode\": \"UGX\", \"shocks\": \"0\"}"));
        assertEquals(List.of("shocks"), parameters("{\"type\": \"REPRICING\", \"currencyCode\": \"UGX\", \"shocks\": \"2001\"}"));
        assertEquals(List.of("shocks"), parameters("{\"type\": \"REPRICING\", \"currencyCode\": \"UGX\", \"shocks\": \"up\"}"));
        assertEquals(List.of("shocks"), parameters("{\"type\": \"LADDER\", \"currencyCode\": \"UGX\", \"shocks\": \"200\"}"));
        assertEquals(List.of(), this.validator.validateRun("{\"currencyCode\": \"UGX\"}").shocksBp());
    }

    @Test
    void repricingAssumptions() {
        final List<String> distNoBucket = assumptionErrors("{\"type\": \"REPRICING_DIST\", \"source\": \"SAVINGS\", \"rate\": 30}");
        assertEquals(List.of("assumptions[0].bucketCode"), distNoBucket);
        assertEquals(List.of("assumptions[0].source"),
                assumptionErrors("{\"type\": \"REPRICING_DIST\", \"source\": \"LOANS\", \"bucketCode\": \"ON\", \"rate\": 30}"));
        assertEquals(List.of("assumptions[0].minDays"),
                assumptionErrors("{\"type\": \"REPRICING_NON_SENSITIVE\", \"source\": \"LOANS\", \"rate\": 100}"));
        final Assumptions.Row on = new Assumptions.Row(AssumptionType.REPRICING_DIST, LadderSourceCode.SAVINGS, null, "ON", null, null,
                new java.math.BigDecimal("60"));
        final Assumptions.Row week = new Assumptions.Row(AssumptionType.REPRICING_DIST, LadderSourceCode.SAVINGS, null, "D2_8", null, null,
                new java.math.BigDecimal("50"));
        assertEquals("repricing distribution rates add up to 110% for SAVINGS, above 100%",
                TreasuryLiquidityDataValidator.checkAssumptions(List.of(on, week)));
        final Assumptions.Row band = new Assumptions.Row(AssumptionType.REPRICING_NON_SENSITIVE, LadderSourceCode.LOANS, null, null, 30,
                null, new java.math.BigDecimal("100"));
        final Assumptions.Row overlap = new Assumptions.Row(AssumptionType.REPRICING_NON_SENSITIVE, LadderSourceCode.LOANS, null, null, 60,
                90, new java.math.BigDecimal("50"));
        assertEquals("repricing non-sensitive bands overlap from 60 days",
                TreasuryLiquidityDataValidator.checkAssumptions(List.of(band, overlap)));
    }

    private List<String> assumptionErrors(final String row) {
        return assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateAssumptionSet(
                        "{\"code\": \"R\", \"name\": \"R\", \"effectiveDate\": \"2026-10-01\", \"assumptions\": [" + row + "]}", true))
                .getErrors().stream().map(ApiParameterError::getParameterName).distinct().toList();
    }

    private List<String> parameters(final String json) {
        return assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateRun(json)).getErrors().stream()
                .map(ApiParameterError::getParameterName).distinct().toList();
    }
}
