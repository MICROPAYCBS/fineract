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

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;

@Entity
@Table(name = "m_savings_account_payment_channel")
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class SavingsAccountPaymentChannel extends AbstractPersistableCustom<Long> {

    @ManyToOne(optional = false)
    @JoinColumn(name = "savings_account_id", nullable = false)
    private SavingsAccount savingsAccount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_payment_channel_id", nullable = false)
    private SavingsProductPaymentChannel productPaymentChannel;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payment_type_id", nullable = false)
    private PaymentType paymentType;

    @Column(name = "status_enum", nullable = false)
    private Integer status;

    @Column(name = "subscribed_on_date", nullable = false)
    private LocalDate subscribedOnDate;

    @Column(name = "unsubscribed_on_date")
    private LocalDate unsubscribedOnDate;

    @OneToMany(mappedBy = "accountPaymentChannel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<SavingsAccountPaymentChannelCharge> linkedCharges = new HashSet<>();

    public static SavingsAccountPaymentChannel subscribe(final SavingsAccount account, final SavingsProductPaymentChannel productChannel,
            final LocalDate subscribedOnDate) {
        return new SavingsAccountPaymentChannel().setSavingsAccount(account).setProductPaymentChannel(productChannel)
                .setPaymentType(productChannel.getPaymentType()).setStatus(SavingsPaymentChannelStatus.ACTIVE.getValue())
                .setSubscribedOnDate(subscribedOnDate);
    }

    public void unsubscribe(final LocalDate unsubscribedOnDate) {
        this.status = SavingsPaymentChannelStatus.INACTIVE.getValue();
        this.unsubscribedOnDate = unsubscribedOnDate;
    }

    public boolean isActive() {
        return SavingsPaymentChannelStatus.fromInt(this.status).isActive();
    }

    public void addLinkedCharge(final SavingsAccountCharge savingsAccountCharge) {
        final SavingsAccountPaymentChannelCharge link = SavingsAccountPaymentChannelCharge.create(this, savingsAccountCharge);
        this.linkedCharges.add(link);
    }
}
