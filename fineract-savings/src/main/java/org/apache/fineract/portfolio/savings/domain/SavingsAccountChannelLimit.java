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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.savings.data.SavingsChannelLimitValues;
import org.apache.fineract.portfolio.savings.service.SavingsChannelLimitRules;

@Entity
@Table(name = "m_savings_account_channel_limit", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "savings_account_id", "product_payment_channel_id", "direction" }, name = "uk_sacl_acct_chan_dir") })
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class SavingsAccountChannelLimit extends AbstractPersistableCustom<Long> {

    @ManyToOne(optional = false)
    @JoinColumn(name = "savings_account_id", nullable = false)
    private SavingsAccount savingsAccount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_payment_channel_id", nullable = false)
    private SavingsProductPaymentChannel productPaymentChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private SavingsChannelLimitDirection direction;

    @Column(name = "max_per_txn", scale = 6, precision = 19)
    private BigDecimal maxPerTxn;

    @Column(name = "max_per_day", scale = 6, precision = 19)
    private BigDecimal maxPerDay;

    @Column(name = "max_per_month", scale = 6, precision = 19)
    private BigDecimal maxPerMonth;

    @Column(name = "max_count_per_day")
    private Integer maxCountPerDay;

    @Column(name = "max_count_per_month")
    private Integer maxCountPerMonth;

    @Column(name = "pending_max_per_txn", scale = 6, precision = 19)
    private BigDecimal pendingMaxPerTxn;

    @Column(name = "pending_max_per_day", scale = 6, precision = 19)
    private BigDecimal pendingMaxPerDay;

    @Column(name = "pending_max_per_month", scale = 6, precision = 19)
    private BigDecimal pendingMaxPerMonth;

    @Column(name = "pending_max_count_per_day")
    private Integer pendingMaxCountPerDay;

    @Column(name = "pending_max_count_per_month")
    private Integer pendingMaxCountPerMonth;

    @Column(name = "pending_effective_on")
    private OffsetDateTime pendingEffectiveOn;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;

    @Column(name = "last_modified_by", nullable = false)
    private Long lastModifiedBy;

    @Column(name = "last_modified_on_utc", nullable = false)
    private OffsetDateTime lastModifiedOnUtc;

    public static SavingsAccountChannelLimit create(final SavingsAccount savingsAccount, final SavingsProductPaymentChannel channel,
            final SavingsChannelLimitDirection direction, final Long userId, final OffsetDateTime now) {
        return new SavingsAccountChannelLimit().setSavingsAccount(savingsAccount).setProductPaymentChannel(channel).setDirection(direction)
                .setCreatedBy(userId).setCreatedOnUtc(now).setLastModifiedBy(userId).setLastModifiedOnUtc(now);
    }

    public SavingsChannelLimitValues liveValues() {
        return new SavingsChannelLimitValues(this.maxPerTxn, this.maxPerDay, this.maxPerMonth, this.maxCountPerDay, this.maxCountPerMonth);
    }

    public SavingsChannelLimitValues pendingValues() {
        if (this.pendingEffectiveOn == null) {
            return null;
        }
        return new SavingsChannelLimitValues(this.pendingMaxPerTxn, this.pendingMaxPerDay, this.pendingMaxPerMonth,
                this.pendingMaxCountPerDay, this.pendingMaxCountPerMonth);
    }

    public void replaceImmediately(final SavingsChannelLimitValues values, final Long userId, final OffsetDateTime now) {
        copyLive(values);
        clearPending();
        touch(userId, now);
    }

    /**
     * Decreases apply now. An increase is stored as the full future limit until {@code pendingEffectiveOn}.
     */
    public void applySelfService(final SavingsChannelLimitValues proposed, final int coolingHours, final Long userId,
            final OffsetDateTime now) {
        final boolean increase = SavingsChannelLimitRules.anyLessRestrictive(liveValues(), proposed);
        if (!increase || coolingHours <= 0) {
            replaceImmediately(proposed, userId, now);
            return;
        }
        if (!SavingsChannelLimitRules.lessRestrictive(this.maxPerTxn, proposed.maxPerTxn())) {
            this.maxPerTxn = proposed.maxPerTxn();
        }
        if (!SavingsChannelLimitRules.lessRestrictive(this.maxPerDay, proposed.maxPerDay())) {
            this.maxPerDay = proposed.maxPerDay();
        }
        if (!SavingsChannelLimitRules.lessRestrictive(this.maxPerMonth, proposed.maxPerMonth())) {
            this.maxPerMonth = proposed.maxPerMonth();
        }
        if (!SavingsChannelLimitRules.lessRestrictive(this.maxCountPerDay, proposed.maxCountPerDay())) {
            this.maxCountPerDay = proposed.maxCountPerDay();
        }
        if (!SavingsChannelLimitRules.lessRestrictive(this.maxCountPerMonth, proposed.maxCountPerMonth())) {
            this.maxCountPerMonth = proposed.maxCountPerMonth();
        }
        this.pendingMaxPerTxn = proposed.maxPerTxn();
        this.pendingMaxPerDay = proposed.maxPerDay();
        this.pendingMaxPerMonth = proposed.maxPerMonth();
        this.pendingMaxCountPerDay = proposed.maxCountPerDay();
        this.pendingMaxCountPerMonth = proposed.maxCountPerMonth();
        this.pendingEffectiveOn = now.plusHours(coolingHours);
        touch(userId, now);
    }

    public boolean applyPendingIfDue(final OffsetDateTime now) {
        if (this.pendingEffectiveOn == null || this.pendingEffectiveOn.isAfter(now)) {
            return false;
        }
        this.maxPerTxn = this.pendingMaxPerTxn;
        this.maxPerDay = this.pendingMaxPerDay;
        this.maxPerMonth = this.pendingMaxPerMonth;
        this.maxCountPerDay = this.pendingMaxCountPerDay;
        this.maxCountPerMonth = this.pendingMaxCountPerMonth;
        clearPending();
        return true;
    }

    private void copyLive(final SavingsChannelLimitValues values) {
        this.maxPerTxn = values.maxPerTxn();
        this.maxPerDay = values.maxPerDay();
        this.maxPerMonth = values.maxPerMonth();
        this.maxCountPerDay = values.maxCountPerDay();
        this.maxCountPerMonth = values.maxCountPerMonth();
    }

    private void clearPending() {
        this.pendingMaxPerTxn = null;
        this.pendingMaxPerDay = null;
        this.pendingMaxPerMonth = null;
        this.pendingMaxCountPerDay = null;
        this.pendingMaxCountPerMonth = null;
        this.pendingEffectiveOn = null;
    }

    private void touch(final Long userId, final OffsetDateTime now) {
        this.lastModifiedBy = userId;
        this.lastModifiedOnUtc = now;
    }
}
