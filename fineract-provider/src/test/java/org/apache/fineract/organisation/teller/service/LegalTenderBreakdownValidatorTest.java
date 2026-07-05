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
package org.apache.fineract.organisation.teller.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.LegalTenderCaptureMode;
import org.apache.fineract.organisation.teller.domain.CashLegalTenderLine;
import org.apache.fineract.organisation.teller.domain.CashLegalTenderSourceType;
import org.apache.fineract.organisation.teller.domain.CurrencyLegalTender;
import org.apache.fineract.organisation.teller.domain.CurrencyLegalTenderRepository;
import org.apache.fineract.organisation.teller.exception.CashierLegalTenderValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegalTenderBreakdownValidatorTest {

    private static final String JSON_WITH_LINES = """
            {
              "transactionAmount": 5000000,
              "legalTenderLines": [
                { "legalTenderId": 6, "quantity": 100 }
              ]
            }
            """;

    private static final String JSON_AMOUNT_ONLY = """
            {
              "transactionAmount": 5000000
            }
            """;

    @Mock
    private CurrencyLegalTenderRepository legalTenderRepository;

    @Mock
    private ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository;

    private FromJsonHelper fromJsonHelper;
    private LegalTenderBreakdownValidator validator;

    @BeforeEach
    void setUp() {
        this.fromJsonHelper = new FromJsonHelper();
        this.validator = new LegalTenderBreakdownValidator(this.fromJsonHelper, this.legalTenderRepository,
                this.applicationCurrencyRepository);

        final ApplicationCurrency currency = new ApplicationCurrency("UGX", "Uganda Shilling", 0, 1, "currency.UGX", "USh");
        when(this.applicationCurrencyRepository.findOneWithNotFoundDetection("UGX")).thenReturn(currency);

        final CurrencyLegalTender tender = new CurrencyLegalTender();
        tender.setId(6L);
        tender.setCurrencyCode("UGX");
        tender.setValue(new BigDecimal("50000"));
        tender.setLabel("50,000 UGX note");
        tender.setIsActive(true);
        when(this.legalTenderRepository.findByIdAndCurrencyCode(eq(6L), eq("UGX"))).thenReturn(Optional.of(tender));
    }

    @Test
    void optionalModeAllowsAmountOnly() {
        final JsonCommand command = jsonCommand(JSON_AMOUNT_ONLY);

        final List<CashLegalTenderLine> lines = this.validator.validateAndBuildLines(command, "UGX", new BigDecimal("5000000"),
                LegalTenderCaptureMode.OPTIONAL, CashLegalTenderSourceType.SAVINGS_TXN, 42L);

        assertTrue(lines.isEmpty());
    }

    @Test
    void requiredModeRejectsMissingLines() {
        final JsonCommand command = jsonCommand(JSON_AMOUNT_ONLY);

        assertThrows(CashierLegalTenderValidationException.class,
                () -> this.validator.validateAndBuildLines(command, "UGX", new BigDecimal("5000000"), LegalTenderCaptureMode.REQUIRED,
                        CashLegalTenderSourceType.SAVINGS_TXN, 42L));
    }

    @Test
    void offModeRejectsProvidedLines() {
        final JsonCommand command = jsonCommand(JSON_WITH_LINES);

        assertThrows(CashierLegalTenderValidationException.class, () -> this.validator.validateAndBuildLines(command, "UGX",
                new BigDecimal("5000000"), LegalTenderCaptureMode.OFF, CashLegalTenderSourceType.SAVINGS_TXN, 42L));
    }

    @Test
    void validatesMatchingLinesAndBuildsEntities() {
        final JsonCommand command = jsonCommand(JSON_WITH_LINES);

        final List<CashLegalTenderLine> lines = this.validator.validateAndBuildLines(command, "UGX", new BigDecimal("5000000"),
                LegalTenderCaptureMode.OPTIONAL, CashLegalTenderSourceType.SAVINGS_TXN, 42L);

        assertEquals(1, lines.size());
        assertEquals(42L, lines.get(0).getSourceId());
        assertEquals(CashLegalTenderSourceType.SAVINGS_TXN.getId(), lines.get(0).getSourceType());
        assertEquals(100, lines.get(0).getQuantity());
        assertEquals(new BigDecimal("5000000"), lines.get(0).getLineAmount());
    }

    private JsonCommand jsonCommand(final String json) {
        final JsonElement parsed = this.fromJsonHelper.parse(json);
        return JsonCommand.from(json, parsed, this.fromJsonHelper, "SAVINGS_TRANSACTION", null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }
}
