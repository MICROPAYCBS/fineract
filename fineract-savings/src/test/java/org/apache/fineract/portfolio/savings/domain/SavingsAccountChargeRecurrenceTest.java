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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashMap;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SavingsAccountChargeRecurrenceTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2026, 3, 15);
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 4, 1);
    private static final BigDecimal FEE = new BigDecimal("50");
    private static final MonetaryCurrency USD = new MonetaryCurrency("USD", 2, 0);

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        MoneyHelper.initializeTenantRoundingMode("default", 4);
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE);
        ThreadLocalContextUtil.setBusinessDates(businessDates);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void inactivateClearsNotYetDueOutstandingAndKeepsAmountPaid() {
        final SavingsAccountCharge charge = monthlyFee();
        charge.pay(USD, money("10"));

        charge.inactiavateCharge(BUSINESS_DATE);

        assertFalse(charge.isActive());
        assertEquals(0, charge.amoutOutstanding().compareTo(BigDecimal.ZERO));
        assertTrue(charge.isPaidOrPartiallyPaid(USD));
    }

    @Test
    void payingEndedRecurrenceDoesNotOpenNextCycle() {
        final SavingsAccountCharge charge = monthlyFee();
        charge.endRecurrence();

        charge.pay(USD, money("50"));

        assertFalse(charge.isActive());
        assertEquals(DUE_DATE, charge.getDueDate());
        assertEquals(0, charge.amoutOutstanding().compareTo(BigDecimal.ZERO));
        assertTrue(charge.isRecurrenceEnded());
        assertTrue(charge.isPaid());
    }

    @Test
    void waivingEndedRecurrenceDoesNotRollDueDate() {
        final SavingsAccountCharge charge = monthlyFee();
        charge.endRecurrence();

        charge.waive(USD);

        assertFalse(charge.isActive());
        assertEquals(DUE_DATE, charge.getDueDate());
        assertEquals(0, charge.amoutOutstanding().compareTo(BigDecimal.ZERO));
        assertTrue(charge.isWaived());
        assertTrue(charge.isRecurrenceEnded());
    }

    @Test
    void undoWaiverAfterEndedRecurrenceRestoresSamePeriod() {
        final SavingsAccountCharge charge = monthlyFee();
        charge.endRecurrence();
        charge.waive(USD);

        charge.undoWaiver(USD, money("50"));

        assertEquals(DUE_DATE, charge.getDueDate());
        assertEquals(0, charge.amoutOutstanding().compareTo(FEE));
        assertTrue(charge.isActive());
        assertFalse(charge.isWaived());
        assertTrue(charge.isRecurrenceEnded());
    }

    @Test
    void undoPaymentAfterEndedRecurrenceRestoresSamePeriod() {
        final SavingsAccountCharge charge = monthlyFee();
        charge.endRecurrence();
        charge.pay(USD, money("50"));

        charge.undoPayment(USD, money("50"));

        assertEquals(DUE_DATE, charge.getDueDate());
        assertEquals(0, charge.amoutOutstanding().compareTo(FEE));
        assertTrue(charge.isActive());
        assertTrue(charge.isRecurrenceEnded());
        assertFalse(charge.isPaid());
    }

    private static SavingsAccountCharge monthlyFee() {
        final Charge definition = mock(Charge.class);
        when(definition.isPenalty()).thenReturn(false);
        when(definition.isTiered()).thenReturn(false);
        return SavingsAccountCharge.createNewWithoutSavingsAccount(definition, FEE, ChargeTimeType.MONTHLY_FEE, ChargeCalculationType.FLAT,
                DUE_DATE, true, MonthDay.of(3, 15), 1);
    }

    private static Money money(final String amount) {
        return Money.of(USD, new BigDecimal(amount));
    }
}
