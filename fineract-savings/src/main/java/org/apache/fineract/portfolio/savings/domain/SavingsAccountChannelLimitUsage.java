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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_savings_account_channel_limit_usage", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "savings_account_id", "product_payment_channel_id", "direction", "period_type",
                "period_start" }, name = "uk_saclu_bucket") })
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class SavingsAccountChannelLimitUsage extends AbstractPersistableCustom<Long> {

    @ManyToOne(optional = false)
    @JoinColumn(name = "savings_account_id", nullable = false)
    private SavingsAccount savingsAccount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_payment_channel_id", nullable = false)
    private SavingsProductPaymentChannel productPaymentChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private SavingsChannelLimitDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private SavingsChannelLimitPeriodType periodType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "amount_used", nullable = false, scale = 6, precision = 19)
    private BigDecimal amountUsed = BigDecimal.ZERO;

    @Column(name = "count_used", nullable = false)
    private Integer countUsed = 0;

    public static SavingsAccountChannelLimitUsage start(final SavingsAccount savingsAccount, final SavingsProductPaymentChannel channel,
            final SavingsChannelLimitDirection direction, final SavingsChannelLimitPeriodType periodType, final LocalDate periodStart) {
        return new SavingsAccountChannelLimitUsage().setSavingsAccount(savingsAccount).setProductPaymentChannel(channel)
                .setDirection(direction).setPeriodType(periodType).setPeriodStart(periodStart).setAmountUsed(BigDecimal.ZERO)
                .setCountUsed(0);
    }

    public void add(final BigDecimal amount, final int count) {
        this.amountUsed = this.amountUsed.add(amount);
        this.countUsed = this.countUsed + count;
    }

    public void release(final BigDecimal amount, final int count) {
        this.amountUsed = this.amountUsed.subtract(amount);
        if (this.amountUsed.signum() < 0) {
            this.amountUsed = BigDecimal.ZERO;
        }
        this.countUsed = Math.max(0, this.countUsed - count);
    }
}
