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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.charge.domain.Charge;

@Entity
@Table(name = "m_savings_product_payment_channel_charge", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "product_payment_channel_id", "charge_id" }, name = "uk_spp_channel_charge") })
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class SavingsProductPaymentChannelCharge extends AbstractPersistableCustom<Long> {

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_payment_channel_id", nullable = false)
    private SavingsProductPaymentChannel productPaymentChannel;

    @ManyToOne(optional = false)
    @JoinColumn(name = "charge_id", nullable = false)
    private Charge charge;

    @Column(name = "amount", scale = 6, precision = 19)
    private BigDecimal amount;

    public static SavingsProductPaymentChannelCharge create(final SavingsProductPaymentChannel channel, final Charge charge,
            final BigDecimal amount) {
        return new SavingsProductPaymentChannelCharge().setProductPaymentChannel(channel).setCharge(charge).setAmount(amount);
    }
}
