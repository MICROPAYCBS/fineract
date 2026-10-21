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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.junit.jupiter.api.Test;

class SavingsAccountChargeChannelTest {

    private static final Long CHANNEL_ID = 3L;
    private static final Long OTHER_CHANNEL_ID = 9L;

    @Test
    void withdrawalFeeAppliesOnlyForItsChannel() {
        final SavingsAccountCharge charge = fee(ChargeTimeType.WITHDRAWAL_FEE, null, null);
        charge.bindToPaymentChannel(CHANNEL_ID);

        assertTrue(charge.appliesToPaymentChannel(paymentType(CHANNEL_ID)));
        assertFalse(charge.appliesToPaymentChannel(paymentType(OTHER_CHANNEL_ID)));
        assertFalse(charge.appliesToPaymentChannel(null));
    }

    @Test
    void withdrawalFeeWithoutChannelAppliesForAnyPaymentType() {
        final SavingsAccountCharge charge = fee(ChargeTimeType.WITHDRAWAL_FEE, null, null);

        assertTrue(charge.appliesToPaymentChannel(paymentType(CHANNEL_ID)));
        assertTrue(charge.appliesToPaymentChannel(null));
    }

    @Test
    void monthlyFeeIsNotGatedByChannel() {
        final SavingsAccountCharge charge = fee(ChargeTimeType.MONTHLY_FEE, LocalDate.of(2026, 3, 15), MonthDay.of(3, 15));
        charge.bindToPaymentChannel(CHANNEL_ID);

        assertTrue(charge.appliesToPaymentChannel(paymentType(OTHER_CHANNEL_ID)));
        assertTrue(charge.appliesToPaymentChannel(null));
    }

    private static SavingsAccountCharge fee(final ChargeTimeType chargeTime, final LocalDate dueDate, final MonthDay feeOnMonthDay) {
        final Charge definition = mock(Charge.class);
        when(definition.isPenalty()).thenReturn(false);
        when(definition.isTiered()).thenReturn(false);
        return SavingsAccountCharge.createNewWithoutSavingsAccount(definition, new BigDecimal("50"), chargeTime, ChargeCalculationType.FLAT,
                dueDate, true, feeOnMonthDay, chargeTime.isMonthlyFee() ? 1 : null);
    }

    private static PaymentType paymentType(final Long id) {
        final PaymentType paymentType = new PaymentType();
        paymentType.setId(id);
        return paymentType;
    }
}
