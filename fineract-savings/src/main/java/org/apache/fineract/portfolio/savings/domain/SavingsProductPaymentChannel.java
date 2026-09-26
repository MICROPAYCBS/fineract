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
import jakarta.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;

@Entity
@Table(name = "m_savings_product_payment_channel", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "savings_product_id", "payment_type_id" }, name = "uk_spp_channel_product_payment") })
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class SavingsProductPaymentChannel extends AbstractPersistableCustom<Long> {

    @ManyToOne(optional = false)
    @JoinColumn(name = "savings_product_id", nullable = false)
    private SavingsProduct product;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payment_type_id", nullable = false)
    private PaymentType paymentType;

    @Column(name = "is_premium", nullable = false)
    private boolean premium;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @OneToMany(mappedBy = "productPaymentChannel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<SavingsProductPaymentChannelCharge> charges = new HashSet<>();

    public static SavingsProductPaymentChannel create(final SavingsProduct product, final PaymentType paymentType, final boolean premium,
            final boolean active, final String name, final String description) {
        return new SavingsProductPaymentChannel().setProduct(product).setPaymentType(paymentType).setPremium(premium).setActive(active)
                .setName(name).setDescription(description);
    }

    public void replaceCharges(final Set<SavingsProductPaymentChannelCharge> newCharges) {
        this.charges.clear();
        if (newCharges != null) {
            for (final SavingsProductPaymentChannelCharge charge : newCharges) {
                charge.setProductPaymentChannel(this);
                this.charges.add(charge);
            }
        }
    }
}
