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
package org.apache.fineract.portfolio.savings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepository;
import org.apache.fineract.portfolio.savings.data.SavingsProductPaymentChannelLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SavingsProductPaymentChannelAssemblerTest {

    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();
    private ChargeRepositoryWrapper chargeRepository;
    private PaymentTypeRepository paymentTypeRepository;
    private SavingsProductPaymentChannelAssembler assembler;

    @BeforeEach
    void setUp() {
        chargeRepository = mock(ChargeRepositoryWrapper.class);
        paymentTypeRepository = mock(PaymentTypeRepository.class);
        assembler = new SavingsProductPaymentChannelAssembler(chargeRepository, paymentTypeRepository, fromJsonHelper);
    }

    @Test
    void assemblesFreeAndPremiumChannels() {
        final PaymentType free = paymentType(1L);
        final PaymentType premium = paymentType(3L);
        when(paymentTypeRepository.findById(1L)).thenReturn(Optional.of(free));
        when(paymentTypeRepository.findById(3L)).thenReturn(Optional.of(premium));

        final Charge charge = mock(Charge.class);
        when(charge.getId()).thenReturn(12L);
        when(charge.isSavingsCharge()).thenReturn(true);
        when(charge.getCurrencyCode()).thenReturn("USD");
        when(charge.isTiered()).thenReturn(false);
        when(charge.getChargeTimeType()).thenReturn(ChargeTimeType.WITHDRAWAL_FEE.getValue());
        when(chargeRepository.findOneWithNotFoundDetection(12L)).thenReturn(charge);

        final List<SavingsProductPaymentChannelLink> links = assembler.assemble(command("""
                {
                  "paymentChannels": [
                    { "paymentTypeId": 1, "isPremium": false, "isActive": true, "charges": [] },
                    { "paymentTypeId": 3, "isPremium": true, "isActive": true,
                      "charges": [ { "id": 12, "amount": 50 } ] }
                  ],
                  "locale": "en"
                }
                """), "USD");
        assertEquals(2, links.size());
        assertFalse(links.get(0).premium());
        assertTrue(links.get(1).premium());
        assertEquals(1, links.get(1).charges().size());
        assertEquals(0, new BigDecimal("50").compareTo(links.get(1).charges().get(0).amount()));
    }

    @Test
    void rejectsAmountOverrideOnTieredCharge() {
        final PaymentType premium = paymentType(3L);
        when(paymentTypeRepository.findById(3L)).thenReturn(Optional.of(premium));
        final Charge charge = mock(Charge.class);
        when(charge.getId()).thenReturn(12L);
        when(charge.isSavingsCharge()).thenReturn(true);
        when(charge.getCurrencyCode()).thenReturn("USD");
        when(charge.isTiered()).thenReturn(true);
        when(chargeRepository.findOneWithNotFoundDetection(12L)).thenReturn(charge);

        assertThrows(PlatformApiDataValidationException.class, () -> assembler.assemble(command("""
                {
                  "paymentChannels": [
                    { "paymentTypeId": 3, "isPremium": true, "charges": [ { "id": 12, "amount": 50 } ] }
                  ],
                  "locale": "en"
                }
                """), "USD"));
    }

    @Test
    void rejectsDuplicateChargeIdWithinChannel() {
        final PaymentType premium = paymentType(3L);
        when(paymentTypeRepository.findById(3L)).thenReturn(Optional.of(premium));
        final Charge charge = mock(Charge.class);
        when(charge.getId()).thenReturn(12L);
        when(charge.isSavingsCharge()).thenReturn(true);
        when(charge.getCurrencyCode()).thenReturn("USD");
        when(charge.isTiered()).thenReturn(false);
        when(chargeRepository.findOneWithNotFoundDetection(12L)).thenReturn(charge);

        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> assembler.assemble(command("""
                        {
                          "paymentChannels": [
                            { "paymentTypeId": 3, "isPremium": true,
                              "charges": [ { "id": 12, "amount": 50 }, { "id": 12, "amount": 75 } ] }
                          ],
                          "locale": "en"
                        }
                        """), "USD"));
        assertTrue(ex.getErrors().stream().anyMatch(e -> "duplicated.chargeId".equals(e.getUserMessageGlobalisationCode())
                || (e.getUserMessageGlobalisationCode() != null && e.getUserMessageGlobalisationCode().contains("duplicated.chargeId"))));
    }

    @Test
    void rejectsPerTransactionAboveDailyAndTwoTransferChannels() {
        final PaymentType mobile = paymentType(1L);
        final PaymentType ussd = paymentType(2L);
        when(paymentTypeRepository.findById(1L)).thenReturn(Optional.of(mobile));
        when(paymentTypeRepository.findById(2L)).thenReturn(Optional.of(ussd));

        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> assembler.assemble(command("""
                        {
                          "paymentChannels": [
                            { "paymentTypeId": 1, "isPremium": false, "isActive": true, "isAccountTransferChannel": true,
                              "maxDebitPerTxn": 500, "maxDebitPerDay": 100, "charges": [] },
                            { "paymentTypeId": 2, "isPremium": false, "isActive": true, "isAccountTransferChannel": true,
                              "charges": [] }
                          ],
                          "locale": "en"
                        }
                        """), "USD"));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.getUserMessageGlobalisationCode() != null
                && e.getUserMessageGlobalisationCode().contains("must.not.exceed.daily")));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.getUserMessageGlobalisationCode() != null
                && e.getUserMessageGlobalisationCode().contains("multiple.account.transfer.channels")));
    }

    @Test
    void assemblesChannelCeilings() {
        final PaymentType mobile = paymentType(1L);
        when(paymentTypeRepository.findById(1L)).thenReturn(Optional.of(mobile));
        final List<SavingsProductPaymentChannelLink> links = assembler.assemble(command("""
                {
                  "paymentChannels": [
                    { "paymentTypeId": 1, "isPremium": false, "isActive": true, "isAccountTransferChannel": true,
                      "maxDebitPerTxn": 100, "maxDebitPerDay": 500, "maxDebitCountPerDay": 3, "charges": [] }
                  ],
                  "locale": "en"
                }
                """), "USD");
        assertEquals(1, links.size());
        assertTrue(links.get(0).limits().accountTransferChannel());
        assertEquals(0, new BigDecimal("100").compareTo(links.get(0).limits().debit().maxPerTxn()));
        assertEquals(0, new BigDecimal("500").compareTo(links.get(0).limits().debit().maxPerDay()));
        assertEquals(3, links.get(0).limits().debit().maxCountPerDay());
    }

    @Test
    void rejectsDuplicatePaymentTypeId() {
        final PaymentType free = paymentType(1L);
        when(paymentTypeRepository.findById(1L)).thenReturn(Optional.of(free));

        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> assembler.assemble(command("""
                        {
                          "paymentChannels": [
                            { "paymentTypeId": 1, "isPremium": false, "charges": [] },
                            { "paymentTypeId": 1, "isPremium": true, "charges": [] }
                          ],
                          "locale": "en"
                        }
                        """), "USD"));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.getUserMessageGlobalisationCode() != null
                && e.getUserMessageGlobalisationCode().contains("duplicated")));
    }

    @Test
    void rejectsDailyAboveMonthly() {
        when(paymentTypeRepository.findById(1L)).thenReturn(Optional.of(paymentType(1L)));

        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> assembler.assemble(command("""
                        {
                          "paymentChannels": [
                            { "paymentTypeId": 1, "isPremium": false, "isActive": true,
                              "maxDebitPerDay": 500, "maxDebitPerMonth": 200, "charges": [] }
                          ],
                          "locale": "en"
                        }
                        """), "USD"));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.getUserMessageGlobalisationCode() != null
                && e.getUserMessageGlobalisationCode().contains("must.not.exceed.monthly")));
    }

    @Test
    void rejectsDailyCountAboveMonthlyCount() {
        when(paymentTypeRepository.findById(1L)).thenReturn(Optional.of(paymentType(1L)));

        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> assembler.assemble(command("""
                        {
                          "paymentChannels": [
                            { "paymentTypeId": 1, "isPremium": false, "isActive": true,
                              "maxCreditCountPerDay": 10, "maxCreditCountPerMonth": 4, "charges": [] }
                          ],
                          "locale": "en"
                        }
                        """), "USD"));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.getUserMessageGlobalisationCode() != null
                && e.getUserMessageGlobalisationCode().contains("must.not.exceed.monthly")));
    }

    private static PaymentType paymentType(final Long id) {
        final PaymentType pt = new PaymentType();
        pt.setId(id);
        return pt;
    }

    private JsonCommand command(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), fromJsonHelper, "SAVINGSPRODUCT", null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
