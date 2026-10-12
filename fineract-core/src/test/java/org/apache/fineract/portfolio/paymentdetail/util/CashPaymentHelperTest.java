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
package org.apache.fineract.portfolio.paymentdetail.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.junit.jupiter.api.Test;

class CashPaymentHelperTest {

    @Test
    void nullPaymentDetailIsNotCash() {
        assertFalse(CashPaymentHelper.isCashPayment(null));
    }

    @Test
    void paymentDetailWithoutTypeIsNotCash() {
        assertFalse(CashPaymentHelper.isCashPayment(PaymentDetail.instance(null, null, null, null, null, null)));
    }

    @Test
    void nonCashPaymentTypeIsNotCash() {
        final PaymentType paymentType = new PaymentType();
        paymentType.setIsCashPayment(false);
        assertFalse(CashPaymentHelper.isCashPayment(PaymentDetail.instance(paymentType, null, null, null, null, null)));
    }

    @Test
    void cashPaymentTypeIsCash() {
        final PaymentType paymentType = new PaymentType();
        paymentType.setIsCashPayment(true);
        assertTrue(CashPaymentHelper.isCashPayment(PaymentDetail.instance(paymentType, null, null, null, null, null)));
    }
}
