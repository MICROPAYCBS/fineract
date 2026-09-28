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

import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReactivatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Rolls savings channel fees forward when a payment type is turned back on, in the same transaction as the hold close.
 */
@Component
@RequiredArgsConstructor
public class PaymentTypeReactivatedListener {

    private final SavingsPaymentChannelFeeHoldService feeHoldService;

    @EventListener
    public void onPaymentTypeReactivated(final PaymentTypeReactivatedEvent event) {
        this.feeHoldService.skipMissedCyclesForPaymentType(event.paymentTypeId());
    }
}
