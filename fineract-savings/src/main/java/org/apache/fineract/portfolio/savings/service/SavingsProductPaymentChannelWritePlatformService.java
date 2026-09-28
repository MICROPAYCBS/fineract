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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.savings.data.SavingsProductPaymentChannelLink;
import org.apache.fineract.portfolio.savings.data.SavingsProductPaymentChannelLink.ChannelChargeLink;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannel;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannelCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SavingsProductPaymentChannelWritePlatformService {

    private final SavingsProductPaymentChannelRepository channelRepository;
    private final SavingsPaymentChannelFeeHoldService feeHoldService;

    @Transactional
    public void syncProductChannels(final SavingsProduct product, final List<SavingsProductPaymentChannelLink> links) {
        if (product == null || product.getId() == null || links == null) {
            return;
        }

        final Map<Long, SavingsProductPaymentChannel> existingByPaymentType = this.channelRepository.findByProductId(product.getId())
                .stream().collect(Collectors.toMap(c -> c.getPaymentType().getId(), Function.identity(), (a, b) -> a));

        final LocalDate today = DateUtils.getBusinessLocalDate();
        final Set<Long> keepPaymentTypeIds = new HashSet<>();
        for (final SavingsProductPaymentChannelLink link : links) {
            final Long paymentTypeId = link.paymentType().getId();
            keepPaymentTypeIds.add(paymentTypeId);
            SavingsProductPaymentChannel channel = existingByPaymentType.get(paymentTypeId);
            final boolean persisted = channel != null && channel.getId() != null;
            final boolean wasActive = persisted && channel.isActive();
            if (channel == null) {
                channel = SavingsProductPaymentChannel.create(product, link.paymentType(), link.premium(), link.active(), link.name(),
                        link.description());
            } else {
                channel.setPremium(link.premium());
                channel.setActive(link.active());
                channel.setName(link.name());
                channel.setDescription(link.description());
            }
            syncChannelCharges(channel, link.charges());
            this.channelRepository.saveAndFlush(channel);
            recordProductActiveChange(channel, persisted, wasActive, today);
        }

        for (final SavingsProductPaymentChannel existing : existingByPaymentType.values()) {
            if (!keepPaymentTypeIds.contains(existing.getPaymentType().getId())) {
                // Soft-remove so account subscription FKs remain valid. Turning it off pauses scheduled fees.
                final boolean wasActive = existing.isActive();
                existing.setActive(false);
                syncChannelCharges(existing, List.of());
                this.channelRepository.saveAndFlush(existing);
                if (wasActive) {
                    this.feeHoldService.openProductHold(existing, today);
                }
            }
        }
    }

    private void recordProductActiveChange(final SavingsProductPaymentChannel channel, final boolean persisted, final boolean wasActive,
            final LocalDate today) {
        if (wasActive && !channel.isActive()) {
            this.feeHoldService.openProductHold(channel, today);
        } else if (persisted && !wasActive && channel.isActive()) {
            this.feeHoldService.closeProductHold(channel, today);
        }
    }

    /**
     * Upsert by charge id so re-saving the same mapping does not INSERT then collide with
     * {@code uk_spp_channel_charge} (clear+recreate can insert before orphan DELETE).
     */
    private void syncChannelCharges(final SavingsProductPaymentChannel channel, final List<ChannelChargeLink> chargeLinks) {
        final Map<Long, SavingsProductPaymentChannelCharge> existingByChargeId = channel.getCharges().stream()
                .collect(Collectors.toMap(c -> c.getCharge().getId(), Function.identity(), (a, b) -> a));

        final Set<Long> keepChargeIds = new HashSet<>();
        for (final ChannelChargeLink chargeLink : chargeLinks) {
            final Long chargeId = chargeLink.charge().getId();
            keepChargeIds.add(chargeId);
            final SavingsProductPaymentChannelCharge existing = existingByChargeId.get(chargeId);
            if (existing != null) {
                existing.setAmount(chargeLink.amount());
            } else {
                channel.getCharges().add(SavingsProductPaymentChannelCharge.create(channel, chargeLink.charge(), chargeLink.amount()));
            }
        }

        final Iterator<SavingsProductPaymentChannelCharge> iterator = channel.getCharges().iterator();
        while (iterator.hasNext()) {
            final SavingsProductPaymentChannelCharge existing = iterator.next();
            if (!keepChargeIds.contains(existing.getCharge().getId())) {
                iterator.remove();
            }
        }
    }
}
