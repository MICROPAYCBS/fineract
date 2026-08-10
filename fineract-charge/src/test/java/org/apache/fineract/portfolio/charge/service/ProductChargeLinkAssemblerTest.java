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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.charge.data.ProductChargeLink;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductChargeLinkAssemblerTest {

    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();
    private ChargeRepositoryWrapper chargeRepository;
    private ProductChargeLinkAssembler assembler;

    @BeforeEach
    void setUp() {
        chargeRepository = mock(ChargeRepositoryWrapper.class);
        assembler = new ProductChargeLinkAssembler(chargeRepository, fromJsonHelper);
    }

    @Test
    void assemblesOptionalAmountOverride() {
        final Charge charge = mockLoanCharge(10L, "USD", false);
        when(chargeRepository.findOneWithNotFoundDetection(10L)).thenReturn(charge);

        final List<ProductChargeLink> links = assembler.assembleLoanProductCharges(command("""
                {
                  "locale": "en",
                  "currencyCode": "USD",
                  "charges": [ { "id": 10, "amount": 1.5 } ]
                }
                """), "USD");

        assertEquals(1, links.size());
        assertEquals(0, new BigDecimal("1.5").compareTo(links.get(0).amount()));
        assertEquals(0, new BigDecimal("1.5").compareTo(links.get(0).effectiveAmount()));
    }

    @Test
    void omitsAmountUsesChargeDefaultAsEffective() {
        final Charge charge = mockLoanCharge(11L, "USD", false);
        when(charge.getAmount()).thenReturn(new BigDecimal("25"));
        when(chargeRepository.findOneWithNotFoundDetection(11L)).thenReturn(charge);

        final List<ProductChargeLink> links = assembler.assembleLoanProductCharges(command("""
                {
                  "locale": "en",
                  "currencyCode": "USD",
                  "charges": [ { "id": 11 } ]
                }
                """), "USD");

        assertEquals(1, links.size());
        assertNull(links.get(0).amount());
        assertEquals(0, new BigDecimal("25").compareTo(links.get(0).effectiveAmount()));
    }

    @Test
    void rejectsAmountWhenChargeIsTiered() {
        final Charge charge = mockLoanCharge(12L, "USD", true);
        when(chargeRepository.findOneWithNotFoundDetection(12L)).thenReturn(charge);

        assertThrows(PlatformApiDataValidationException.class, () -> assembler.assembleLoanProductCharges(command("""
                {
                  "locale": "en",
                  "currencyCode": "USD",
                  "charges": [ { "id": 12, "amount": 9 } ]
                }
                """), "USD"));
    }

    @Test
    void rejectsNonPositiveAmount() {
        final Charge charge = mockLoanCharge(13L, "USD", false);
        when(chargeRepository.findOneWithNotFoundDetection(13L)).thenReturn(charge);

        assertThrows(PlatformApiDataValidationException.class, () -> assembler.assembleLoanProductCharges(command("""
                {
                  "locale": "en",
                  "currencyCode": "USD",
                  "charges": [ { "id": 13, "amount": 0 } ]
                }
                """), "USD"));
    }

    private Charge mockLoanCharge(final Long id, final String currency, final boolean tiered) {
        final Charge charge = mock(Charge.class);
        when(charge.getId()).thenReturn(id);
        when(charge.isLoanCharge()).thenReturn(true);
        when(charge.isSavingsCharge()).thenReturn(false);
        when(charge.getCurrencyCode()).thenReturn(currency);
        when(charge.isTiered()).thenReturn(tiered);
        when(charge.getAmount()).thenReturn(BigDecimal.TEN);
        return charge;
    }

    private JsonCommand command(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), fromJsonHelper, "LOANPRODUCT", null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }
}
