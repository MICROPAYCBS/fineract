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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountLedgerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlAccountLedgerDataValidatorTest {

    private GlAccountLedgerDataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new GlAccountLedgerDataValidator();
    }

    @Test
    void acceptsValidRequest() {
        validator.validate(validBuilder().build());
    }

    @Test
    void acceptsDepartmentZero() {
        validator.validate(validBuilder().departmentId(0L).build());
    }

    @Test
    void rejectsMissingStartDate() {
        assertThatThrownBy(() -> validator.validate(validBuilder().startDate(null).build()))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> validator
                .validate(validBuilder().startDate(LocalDate.of(2026, 7, 10)).endDate(LocalDate.of(2026, 7, 1)).build()))
                        .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void rejectsBlankCurrency() {
        assertThatThrownBy(() -> validator.validate(validBuilder().currencyCode(" ").build()))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    private static GlAccountLedgerRequest.GlAccountLedgerRequestBuilder validBuilder() {
        return GlAccountLedgerRequest.builder() //
                .glAccountId(15L) //
                .startDate(LocalDate.of(2026, 7, 1)) //
                .endDate(LocalDate.of(2026, 7, 16)) //
                .officeId(1L) //
                .currencyCode("UGX");
    }
}
