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
package org.apache.fineract.portfolio.charge.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.charge.domain.ChargeAppliesTo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChargeDefinitionCommandFromApiJsonDeserializerUpdateTest {

    private ChargeDefinitionCommandFromApiJsonDeserializer deserializer;

    @BeforeEach
    void setUp() {
        final FromJsonHelper fromJsonHelper = new FromJsonHelper();
        this.deserializer = new ChargeDefinitionCommandFromApiJsonDeserializer(fromJsonHelper, new ChargeTierCommandParser(fromJsonHelper));
    }

    @Test
    void savingsWithdrawalFeeMayUseTiersWhenAppliesToIsOmitted() {
        assertDoesNotThrow(() -> this.deserializer.validateForUpdate("{\"useChargeTiers\":true,\"chargeTimeType\":5}",
                ChargeAppliesTo.SAVINGS.getValue()));
    }

    @Test
    void loanChargeRejectsASavingsWithdrawalTimeType() {
        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> this.deserializer.validateForUpdate("{\"useChargeTiers\":true,\"chargeTimeType\":5}", ChargeAppliesTo.LOAN.getValue()));
        assertTrue(ex.getErrors().stream().anyMatch(error -> error.getUserMessageGlobalisationCode().contains("not.supported.for.charge.time.type")));
    }
}
