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
package org.apache.fineract.portfolio.savings.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.HashMap;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SavingsAccountProductValidityDateTest {

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2024, 6, 15));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void validateNewApplicationState_allowsSubmittedDateWithinProductWindow() {
        final SavingsProduct product = productWithDates(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        final SavingsAccount account = accountWith(product, LocalDate.of(2024, 6, 15));

        assertDoesNotThrow(() -> account.validateNewApplicationState("savingsaccount"));
    }

    @Test
    void validateNewApplicationState_allowsWhenProductDatesAreNull() {
        final SavingsProduct product = productWithDates(null, null);
        final SavingsAccount account = accountWith(product, LocalDate.of(2024, 6, 15));

        assertDoesNotThrow(() -> account.validateNewApplicationState("savingsaccount"));
    }

    @Test
    void validateNewApplicationState_rejectsSubmittedDateBeforeProductStartDate() {
        final SavingsProduct product = productWithDates(LocalDate.of(2024, 7, 1), LocalDate.of(2024, 12, 31));
        final SavingsAccount account = accountWith(product, LocalDate.of(2024, 6, 15));

        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> account.validateNewApplicationState("savingsaccount"));
        assertThat(exception.getErrors()).anyMatch(error -> error.getUserMessageGlobalisationCode()
                .contains("cannot.be.before.savings.product.start.date"));
    }

    @Test
    void validateNewApplicationState_rejectsSubmittedDateAfterProductCloseDate() {
        final SavingsProduct product = productWithDates(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 5, 31));
        final SavingsAccount account = accountWith(product, LocalDate.of(2024, 6, 15));

        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> account.validateNewApplicationState("savingsaccount"));
        assertThat(exception.getErrors()).anyMatch(error -> error.getUserMessageGlobalisationCode()
                .contains("cannot.be.after.savings.product.close.date"));
    }

    private SavingsProduct productWithDates(final LocalDate startDate, final LocalDate closeDate) {
        final SavingsProduct product = new SavingsProduct() {};
        product.setStartDate(startDate);
        product.setCloseDate(closeDate);
        return product;
    }

    private SavingsAccount accountWith(final SavingsProduct product, final LocalDate submittedOnDate) {
        final SavingsAccount account = new SavingsAccount() {};
        account.product = product;
        account.submittedOnDate = submittedOnDate;
        return account;
    }
}
