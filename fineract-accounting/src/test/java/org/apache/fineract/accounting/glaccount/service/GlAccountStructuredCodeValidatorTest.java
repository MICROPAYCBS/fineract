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
package org.apache.fineract.accounting.glaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.apache.fineract.accounting.glaccount.domain.GLAccountType;
import org.apache.fineract.accounting.glaccount.exception.GlAccountCodeCategoryMismatchException;
import org.apache.fineract.accounting.glaccount.exception.GlAccountCodeHeaderMismatchException;
import org.apache.fineract.accounting.glaccount.exception.GlAccountInvalidCodeFormatException;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GlAccountStructuredCodeValidatorTest {

    @Mock
    private ConfigurationDomainService configurationDomainService;

    private GlAccountStructuredCodeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new GlAccountStructuredCodeValidator(configurationDomainService);
    }

    @Test
    void skipsValidationWhenEnforcementDisabled() {
        when(configurationDomainService.isStructuredGlCodesEnforced()).thenReturn(false);

        assertThatCode(() -> validator.validateIfEnabled("4001", GLAccountType.INCOME.getValue())).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateIfEnabled("120001", GLAccountType.ASSET.getValue(), "110000"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsValidStructuredIncomeCode() {
        when(configurationDomainService.isStructuredGlCodesEnforced()).thenReturn(true);
        when(configurationDomainService.retrieveStructuredGlCodeLength()).thenReturn(6);

        assertThatCode(() -> validator.validateIfEnabled("400100", GLAccountType.INCOME.getValue())).doesNotThrowAnyException();
    }

    @Test
    void rejectsWrongLength() {
        assertThatThrownBy(() -> validator.validate("4001", GLAccountType.INCOME.getValue(), 6))
                .isInstanceOf(GlAccountInvalidCodeFormatException.class);
    }

    @Test
    void rejectsNonNumericCode() {
        assertThatThrownBy(() -> validator.validate("40A100", GLAccountType.INCOME.getValue(), 6))
                .isInstanceOf(GlAccountInvalidCodeFormatException.class);
    }

    @Test
    void rejectsCategoryMismatch() {
        assertThatThrownBy(() -> validator.validate("510000", GLAccountType.INCOME.getValue(), 6))
                .isInstanceOf(GlAccountCodeCategoryMismatchException.class);
    }

    @Test
    void derivesHeaderStemFromTrailingZeros() {
        assertThat(GlAccountStructuredCodeValidator.deriveHeaderStem("110000")).isEqualTo("110");
        assertThat(GlAccountStructuredCodeValidator.deriveHeaderStem("110500")).isEqualTo("1105");
        assertThat(GlAccountStructuredCodeValidator.deriveHeaderStem("100000")).isEqualTo("1");
    }

    @Test
    void acceptsChildCodeMatchingHeaderStem() {
        when(configurationDomainService.isStructuredGlCodesEnforced()).thenReturn(true);
        when(configurationDomainService.retrieveStructuredGlCodeLength()).thenReturn(6);

        assertThatCode(() -> validator.validateIfEnabled("110001", GLAccountType.ASSET.getValue(), "110000"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsChildCodeNotMatchingHeaderStem() {
        when(configurationDomainService.isStructuredGlCodesEnforced()).thenReturn(true);
        when(configurationDomainService.retrieveStructuredGlCodeLength()).thenReturn(6);

        assertThatThrownBy(() -> validator.validateIfEnabled("120001", GLAccountType.ASSET.getValue(), "110000"))
                .isInstanceOf(GlAccountCodeHeaderMismatchException.class);
    }

    @Test
    void rejectsChildCodeEqualToParentHeader() {
        when(configurationDomainService.isStructuredGlCodesEnforced()).thenReturn(true);
        when(configurationDomainService.retrieveStructuredGlCodeLength()).thenReturn(6);

        assertThatThrownBy(() -> validator.validateIfEnabled("110000", GLAccountType.ASSET.getValue(), "110000"))
                .isInstanceOf(GlAccountCodeHeaderMismatchException.class);
    }

    @Test
    void rejectsChildWhenParentHasNoTrailingZeros() {
        when(configurationDomainService.isStructuredGlCodesEnforced()).thenReturn(true);
        when(configurationDomainService.retrieveStructuredGlCodeLength()).thenReturn(6);

        assertThatThrownBy(() -> validator.validateIfEnabled("110124", GLAccountType.ASSET.getValue(), "110123"))
                .isInstanceOf(GlAccountCodeHeaderMismatchException.class);
    }
}
