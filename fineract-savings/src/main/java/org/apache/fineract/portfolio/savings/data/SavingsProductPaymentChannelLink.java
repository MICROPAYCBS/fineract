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
package org.apache.fineract.portfolio.savings.data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;

/**
 * Parsed product payment-channel catalog entry before persistence.
 */
public record SavingsProductPaymentChannelLink(PaymentType paymentType, boolean premium, boolean active, String name, String description,
        List<ChannelChargeLink> charges) {

    public record ChannelChargeLink(Charge charge, BigDecimal amount) {}

    public List<ChannelChargeLink> charges() {
        return charges == null ? Collections.emptyList() : charges;
    }

    public static List<ChannelChargeLink> mutableCharges() {
        return new ArrayList<>();
    }
}
