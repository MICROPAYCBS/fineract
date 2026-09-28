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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeHold;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeHoldRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChargeRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannel;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannelBlock;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannelBlockRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannelCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannelChargeRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannelRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsPaymentChannelStatus;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannel;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannelHold;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannelHoldRepository;
import org.apache.fineract.portfolio.savings.service.SavingsPaymentChannelFeeCycle.HoldWindow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pauses monthly and annual fees on a premium subscription while the payment type is inactive, the product channel is
 * inactive, or the account is blocked. An amount already due before the hold stays collectible. Cycles whose due date
 * falls inside a closed hold are rolled forward without a payment.
 */
@Service
@RequiredArgsConstructor
public class SavingsPaymentChannelFeeHoldService {

    private final SavingsProductPaymentChannelHoldRepository productHoldRepository;
    private final PaymentTypeHoldRepository paymentTypeHoldRepository;
    private final SavingsAccountPaymentChannelBlockRepository accountBlockRepository;
    private final SavingsAccountPaymentChannelRepository accountChannelRepository;
    private final SavingsAccountPaymentChannelChargeRepository accountChannelChargeRepository;
    private final SavingsAccountChargeRepository savingsAccountChargeRepository;

    @Transactional
    public void openProductHold(final SavingsProductPaymentChannel channel, final LocalDate startedOn) {
        if (channel == null || channel.getId() == null || startedOn == null) {
            return;
        }
        if (this.productHoldRepository.findByProductPaymentChannel_IdAndEndedOnDateIsNull(channel.getId()).isPresent()) {
            return;
        }
        this.productHoldRepository.save(SavingsProductPaymentChannelHold.open(channel, startedOn));
    }

    @Transactional
    public void closeProductHold(final SavingsProductPaymentChannel channel, final LocalDate endedOn) {
        if (channel == null || channel.getId() == null || endedOn == null) {
            return;
        }
        final var open = this.productHoldRepository.findByProductPaymentChannel_IdAndEndedOnDateIsNull(channel.getId());
        if (open.isEmpty()) {
            return;
        }
        final SavingsProductPaymentChannelHold hold = open.get();
        hold.setEndedOnDate(endedOn);
        this.productHoldRepository.saveAndFlush(hold);
        final List<SavingsAccountPaymentChannel> subscriptions = this.accountChannelRepository
                .findByProductPaymentChannelIdAndStatus(channel.getId(), SavingsPaymentChannelStatus.ACTIVE.getValue());
        for (final SavingsAccountPaymentChannel subscription : subscriptions) {
            skipMissedCycles(subscription);
        }
    }

    @Transactional
    public void skipMissedCyclesForPaymentType(final Long paymentTypeId) {
        if (paymentTypeId == null) {
            return;
        }
        final List<SavingsAccountPaymentChannel> subscriptions = this.accountChannelRepository.findByPaymentTypeIdAndStatus(paymentTypeId,
                SavingsPaymentChannelStatus.ACTIVE.getValue());
        for (final SavingsAccountPaymentChannel subscription : subscriptions) {
            skipMissedCycles(subscription);
        }
    }

    @Transactional
    public void skipMissedCycles(final SavingsAccountPaymentChannel subscription) {
        if (subscription == null || !subscription.isActive()) {
            return;
        }
        final List<HoldWindow> holds = closedHolds(subscription);
        for (final SavingsAccountPaymentChannelCharge link : subscription.getLinkedCharges()) {
            final SavingsAccountCharge charge = link.getSavingsAccountCharge();
            if (SavingsPaymentChannelFeeCycle.skipCyclesDuringHolds(charge, holds)) {
                this.savingsAccountChargeRepository.save(charge);
            }
        }
    }

    /**
     * The due-charge job must not collect this fee while the channel is off system-wide, at product, or on the account.
     */
    public boolean isCollectionPaused(final SavingsAccountCharge charge) {
        final SavingsAccountPaymentChannel subscription = activeSubscription(charge);
        if (subscription == null) {
            return false;
        }
        if (subscription.getPaymentType() != null && Boolean.FALSE.equals(subscription.getPaymentType().getIsActive())) {
            return true;
        }
        if (!subscription.getProductPaymentChannel().isActive()) {
            return true;
        }
        return this.accountBlockRepository.findBySavingsAccount_IdAndProductPaymentChannel_IdAndUnblockedOnDateIsNull(
                subscription.getSavingsAccount().getId(), subscription.getProductPaymentChannel().getId()).isPresent();
    }

    /**
     * Advance a due date that landed inside a hold which has since ended. Used by the due-charge job after it pays a
     * cycle that was already due before the hold, so the following cycles are not back-billed.
     *
     * @return true when the due date moved
     */
    @Transactional
    public boolean advancePastClosedHolds(final SavingsAccountCharge charge) {
        final SavingsAccountPaymentChannel subscription = activeSubscription(charge);
        if (subscription == null) {
            return false;
        }
        final boolean moved = SavingsPaymentChannelFeeCycle.skipCyclesDuringHolds(charge, closedHolds(subscription));
        if (moved) {
            this.savingsAccountChargeRepository.save(charge);
        }
        return moved;
    }

    private SavingsAccountPaymentChannel activeSubscription(final SavingsAccountCharge charge) {
        if (charge == null || charge.getId() == null || !SavingsPaymentChannelFeeCycle.isPausableFee(charge)) {
            return null;
        }
        for (final SavingsAccountPaymentChannelCharge link : this.accountChannelChargeRepository
                .findBySavingsAccountCharge_Id(charge.getId())) {
            final SavingsAccountPaymentChannel subscription = link.getAccountPaymentChannel();
            if (subscription != null && subscription.isActive()) {
                return subscription;
            }
        }
        return null;
    }

    private List<HoldWindow> closedHolds(final SavingsAccountPaymentChannel subscription) {
        final List<HoldWindow> windows = new ArrayList<>();
        final Long accountId = subscription.getSavingsAccount().getId();
        final Long channelId = subscription.getProductPaymentChannel().getId();
        for (final SavingsAccountPaymentChannelBlock block : this.accountBlockRepository
                .findBySavingsAccount_IdAndProductPaymentChannel_IdAndUnblockedOnDateIsNotNull(accountId, channelId)) {
            windows.add(new HoldWindow(block.getBlockedOnDate(), block.getUnblockedOnDate()));
        }
        for (final SavingsProductPaymentChannelHold hold : this.productHoldRepository
                .findByProductPaymentChannel_IdAndEndedOnDateIsNotNull(channelId)) {
            windows.add(new HoldWindow(hold.getStartedOnDate(), hold.getEndedOnDate()));
        }
        if (subscription.getPaymentType() != null && subscription.getPaymentType().getId() != null) {
            for (final PaymentTypeHold hold : this.paymentTypeHoldRepository
                    .findByPaymentType_IdAndEndedOnDateIsNotNull(subscription.getPaymentType().getId())) {
                windows.add(new HoldWindow(hold.getStartedOnDate(), hold.getEndedOnDate()));
            }
        }
        return windows;
    }
}
