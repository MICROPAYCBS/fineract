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

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_savings_account_payment_channel_charge", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "account_payment_channel_id", "savings_account_charge_id" }, name = "uk_sap_channel_charge") })
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class SavingsAccountPaymentChannelCharge extends AbstractPersistableCustom<Long> {

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_payment_channel_id", nullable = false)
    private SavingsAccountPaymentChannel accountPaymentChannel;

    @ManyToOne(optional = false)
    @JoinColumn(name = "savings_account_charge_id", nullable = false)
    private SavingsAccountCharge savingsAccountCharge;

    public static SavingsAccountPaymentChannelCharge create(final SavingsAccountPaymentChannel subscription,
            final SavingsAccountCharge savingsAccountCharge) {
        return new SavingsAccountPaymentChannelCharge().setAccountPaymentChannel(subscription)
                .setSavingsAccountCharge(savingsAccountCharge);
    }
}
