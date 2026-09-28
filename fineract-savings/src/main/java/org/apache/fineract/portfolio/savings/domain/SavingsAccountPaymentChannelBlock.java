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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * Account-level hold on a product payment channel. The premium subscription, when there is one, stays active.
 * {@code unblockedOnDate} null means the hold is still in force.
 */
@Entity
@Table(name = "m_savings_account_payment_channel_block")
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class SavingsAccountPaymentChannelBlock extends AbstractPersistableCustom<Long> {

    @ManyToOne(optional = false)
    @JoinColumn(name = "savings_account_id", nullable = false)
    private SavingsAccount savingsAccount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_payment_channel_id", nullable = false)
    private SavingsProductPaymentChannel productPaymentChannel;

    @Column(name = "blocked_on_date", nullable = false)
    private LocalDate blockedOnDate;

    @Column(name = "unblocked_on_date")
    private LocalDate unblockedOnDate;

    public static SavingsAccountPaymentChannelBlock block(final SavingsAccount savingsAccount,
            final SavingsProductPaymentChannel productPaymentChannel, final LocalDate blockedOnDate) {
        return new SavingsAccountPaymentChannelBlock().setSavingsAccount(savingsAccount).setProductPaymentChannel(productPaymentChannel)
                .setBlockedOnDate(blockedOnDate);
    }

    public boolean isOpen() {
        return this.unblockedOnDate == null;
    }
}
