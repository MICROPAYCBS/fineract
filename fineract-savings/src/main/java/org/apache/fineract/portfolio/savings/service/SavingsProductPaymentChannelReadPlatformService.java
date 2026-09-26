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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.charge.data.ChargeData;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.savings.data.SavingsProductPaymentChannelData;
import org.apache.fineract.portfolio.savings.data.SavingsProductPaymentChannelData.SavingsProductPaymentChannelChargeData;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannel;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannelCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannelRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsProductPaymentChannelReadPlatformService {

    private final SavingsProductPaymentChannelRepository channelRepository;

    public Collection<SavingsProductPaymentChannelData> retrieveProductChannels(final Long productId) {
        final List<SavingsProductPaymentChannel> channels = this.channelRepository.findByProductId(productId);
        final List<SavingsProductPaymentChannelData> result = new ArrayList<>();
        for (final SavingsProductPaymentChannel channel : channels) {
            result.add(toData(channel));
        }
        return result;
    }

    public boolean hasActiveCatalog(final Long productId) {
        return productId != null && this.channelRepository.countByProductIdAndActiveTrue(productId) > 0;
    }

    public SavingsProductPaymentChannelData toData(final SavingsProductPaymentChannel channel) {
        final PaymentType paymentType = channel.getPaymentType();
        final PaymentTypeData paymentTypeData = PaymentTypeData.builder().id(paymentType.getId()).name(paymentType.getName())
                .description(paymentType.getDescription()).isCashPayment(paymentType.getIsCashPayment()).position(paymentType.getPosition())
                .codeName(paymentType.getCodeName()).isSystemDefined(paymentType.getIsSystemDefined()).build();
        final List<SavingsProductPaymentChannelChargeData> charges = new ArrayList<>();
        for (final SavingsProductPaymentChannelCharge channelCharge : channel.getCharges()) {
            final Charge charge = channelCharge.getCharge();
            final BigDecimal amount = channelCharge.getAmount() != null ? channelCharge.getAmount() : charge.getAmount();
            final ChargeData chargeData = charge.toData();
            charges.add(SavingsProductPaymentChannelChargeData.builder().id(channelCharge.getId()).chargeId(charge.getId())
                    .charge(chargeData).amount(amount).build());
        }
        return SavingsProductPaymentChannelData.builder().id(channel.getId()).paymentTypeId(paymentType.getId())
                .paymentType(paymentTypeData).isPremium(channel.isPremium()).isActive(channel.isActive()).name(channel.getName())
                .description(channel.getDescription()).charges(charges).build();
    }
}
