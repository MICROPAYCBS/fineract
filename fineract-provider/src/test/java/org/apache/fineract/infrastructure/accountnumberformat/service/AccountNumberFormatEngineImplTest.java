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
package org.apache.fineract.infrastructure.accountnumberformat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.accountnumberformat.data.AccountNumberGenerationContext;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberSequenceScope;
import org.apache.fineract.infrastructure.accountnumberformat.domain.CheckDigitAlgorithm;
import org.apache.fineract.infrastructure.accountnumberformat.domain.EntityAccountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AccountNumberFormatEngineImplTest {

    @Mock
    private AccountNumberSequenceWritePlatformService accountNumberSequenceWritePlatformService;

    private AccountNumberFormatEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new AccountNumberFormatEngineImpl(accountNumberSequenceWritePlatformService);
    }

    @Test
    public void generatesStructuredSavingsAccountNumber() {
        when(accountNumberSequenceWritePlatformService.nextSequenceValue("SAVINGS|001|SV")).thenReturn(42L);

        final AccountNumberGenerationContext context = AccountNumberGenerationContext.builder().entityAccountType(EntityAccountType.SAVINGS)
                .officeCode("001").productCode("SV").entityTypeCode("3")
                .formatPattern("{officeCode:3}{productCode:2}{sequence:9}{checkDigit:1}")
                .sequenceScope(AccountNumberSequenceScope.OFFICE_PRODUCT).checkDigitAlgorithm(CheckDigitAlgorithm.LUHN).preview(false)
                .build();

        final String accountNumber = engine.generate(context);
        assertThat(accountNumber).hasSize(15);
        assertThat(accountNumber).startsWith("001SV");
        assertThat(accountNumber.substring(5, 14)).isEqualTo("000000042");
    }

    @Test
    public void previewDoesNotPersistSequence() {
        when(accountNumberSequenceWritePlatformService.previewNextSequenceValue("CLIENT|001")).thenReturn(7L);

        final AccountNumberGenerationContext context = AccountNumberGenerationContext.builder().entityAccountType(EntityAccountType.CLIENT)
                .officeCode("001").clientTypeCode("I").entityTypeCode("1")
                .formatPattern("{officeCode:3}{clientTypeCode:1}{sequence:8}{checkDigit:1}")
                .sequenceScope(AccountNumberSequenceScope.OFFICE).checkDigitAlgorithm(CheckDigitAlgorithm.LUHN).preview(true).build();

        final String accountNumber = engine.generate(context);
        assertThat(accountNumber).hasSize(13);
        assertThat(accountNumber.substring(4, 12)).isEqualTo("00000007");
    }
}
