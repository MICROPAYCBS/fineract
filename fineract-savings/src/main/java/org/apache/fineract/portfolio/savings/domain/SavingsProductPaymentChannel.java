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
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.savings.data.SavingsChannelLimitValues;
import org.apache.fineract.portfolio.savings.data.SavingsProductPaymentChannelLimits;

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

    @Column(name = "max_debit_per_txn", scale = 6, precision = 19)
    private BigDecimal maxDebitPerTxn;

    @Column(name = "max_debit_per_day", scale = 6, precision = 19)
    private BigDecimal maxDebitPerDay;

    @Column(name = "max_debit_per_month", scale = 6, precision = 19)
    private BigDecimal maxDebitPerMonth;

    @Column(name = "max_debit_count_per_day")
    private Integer maxDebitCountPerDay;

    @Column(name = "max_debit_count_per_month")
    private Integer maxDebitCountPerMonth;

    @Column(name = "max_credit_per_txn", scale = 6, precision = 19)
    private BigDecimal maxCreditPerTxn;

    @Column(name = "max_credit_per_day", scale = 6, precision = 19)
    private BigDecimal maxCreditPerDay;

    @Column(name = "max_credit_per_month", scale = 6, precision = 19)
    private BigDecimal maxCreditPerMonth;

    @Column(name = "max_credit_count_per_day")
    private Integer maxCreditCountPerDay;

    @Column(name = "max_credit_count_per_month")
    private Integer maxCreditCountPerMonth;

    @Column(name = "is_account_transfer_channel", nullable = false)
    private boolean accountTransferChannel;

    @OneToMany(mappedBy = "productPaymentChannel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<SavingsProductPaymentChannelCharge> charges = new HashSet<>();

    public static SavingsProductPaymentChannel create(final SavingsProduct product, final PaymentType paymentType, final boolean premium,
            final boolean active, final String name, final String description) {
        return new SavingsProductPaymentChannel().setProduct(product).setPaymentType(paymentType).setPremium(premium).setActive(active)
                .setName(name).setDescription(description);
    }

    public void applyLimits(final SavingsProductPaymentChannelLimits limits) {
        final SavingsProductPaymentChannelLimits values = limits == null ? SavingsProductPaymentChannelLimits.none() : limits;
        final SavingsChannelLimitValues debit = values.debit();
        final SavingsChannelLimitValues credit = values.credit();
        this.maxDebitPerTxn = debit.maxPerTxn();
        this.maxDebitPerDay = debit.maxPerDay();
        this.maxDebitPerMonth = debit.maxPerMonth();
        this.maxDebitCountPerDay = debit.maxCountPerDay();
        this.maxDebitCountPerMonth = debit.maxCountPerMonth();
        this.maxCreditPerTxn = credit.maxPerTxn();
        this.maxCreditPerDay = credit.maxPerDay();
        this.maxCreditPerMonth = credit.maxPerMonth();
        this.maxCreditCountPerDay = credit.maxCountPerDay();
        this.maxCreditCountPerMonth = credit.maxCountPerMonth();
        this.accountTransferChannel = values.accountTransferChannel();
    }

    public SavingsChannelLimitValues debitLimits() {
        return new SavingsChannelLimitValues(this.maxDebitPerTxn, this.maxDebitPerDay, this.maxDebitPerMonth, this.maxDebitCountPerDay,
                this.maxDebitCountPerMonth);
    }

    public SavingsChannelLimitValues creditLimits() {
        return new SavingsChannelLimitValues(this.maxCreditPerTxn, this.maxCreditPerDay, this.maxCreditPerMonth, this.maxCreditCountPerDay,
                this.maxCreditCountPerMonth);
    }

    public SavingsChannelLimitValues limitsFor(final SavingsChannelLimitDirection direction) {
        return direction == SavingsChannelLimitDirection.CREDIT ? creditLimits() : debitLimits();
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
