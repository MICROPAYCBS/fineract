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
package org.apache.fineract.portfolio.accounting.glenquiry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountEnquiryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlAccountEnquiryDataValidatorTest {

    private GlAccountEnquiryDataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new GlAccountEnquiryDataValidator();
    }

    @Test
    void rejectsWhenNoFiltersProvided() {
        final GlAccountEnquiryRequest request = GlAccountEnquiryRequest.builder().build();

        assertThatThrownBy(() -> validator.validate(request)).isInstanceOf(PlatformApiDataValidationException.class).satisfies(ex -> {
            final PlatformApiDataValidationException validationException = (PlatformApiDataValidationException) ex;
            assertThat(validationException.getErrors()).anySatisfy(
                    error -> assertThat(error.getUserMessageGlobalisationCode()).contains("filters.at.least.one.required"));
        });
    }

    @Test
    void acceptsGlPrefixOnly() {
        validator.validate(GlAccountEnquiryRequest.builder().glPrefix("1").build());
    }

    @Test
    void acceptsOfficeIdOnly() {
        validator.validate(GlAccountEnquiryRequest.builder().officeId(1L).build());
    }

    @Test
    void acceptsDisabledOnly() {
        validator.validate(GlAccountEnquiryRequest.builder().disabled(false).build());
    }

    @Test
    void acceptsDepartmentIdOnly() {
        validator.validate(GlAccountEnquiryRequest.builder().departmentId(2L).build());
    }

    @Test
    void acceptsCurrencyCodeOnly() {
        validator.validate(GlAccountEnquiryRequest.builder().currencyCode("UGX").build());
    }

    @Test
    void acceptsDescriptionOnly() {
        validator.validate(GlAccountEnquiryRequest.builder().description("cash").build());
    }

    @Test
    void rejectsExcludeZeroBalanceAlone() {
        assertThatThrownBy(() -> validator.validate(GlAccountEnquiryRequest.builder().excludeZeroBalance(false).build()))
                .isInstanceOf(PlatformApiDataValidationException.class);
        assertThatThrownBy(() -> validator.validate(GlAccountEnquiryRequest.builder().excludeZeroBalance(true).build()))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void rejectsBlankDescriptionAlone() {
        assertThatThrownBy(() -> validator.validate(GlAccountEnquiryRequest.builder().description("   ").build()))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void rejectsNonPositiveOfficeId() {
        assertThatThrownBy(() -> validator.validate(GlAccountEnquiryRequest.builder().officeId(0L).build()))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void rejectsNonPositiveDepartmentId() {
        assertThatThrownBy(() -> validator.validate(GlAccountEnquiryRequest.builder().departmentId(0L).build()))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }
}
