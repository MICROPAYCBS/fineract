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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannel;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannelRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsPaymentChannelStatus;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannel;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannelRepository;
import org.springframework.stereotype.Service;

/**
 * When a savings product has a non-empty active payment-channel catalog, deposits/withdrawals may only use
 * non-premium catalogued channels or premium channels with an ACTIVE account subscription. Empty catalog = all payment
 * types allowed (legacy behaviour).
 */
@Service
@RequiredArgsConstructor
public class SavingsAccountPaymentChannelAllowListService {

    private final SavingsProductPaymentChannelRepository productChannelRepository;
    private final SavingsAccountPaymentChannelRepository accountChannelRepository;

    public boolean hasCatalog(final Long productId) {
        return productId != null && this.productChannelRepository.countByProductIdAndActiveTrue(productId) > 0;
    }

    public Set<Long> allowedPaymentTypeIds(final Long savingsAccountId, final Long productId) {
        final List<SavingsProductPaymentChannel> catalog = this.productChannelRepository.findByProductIdAndActiveTrue(productId);
        if (catalog.isEmpty()) {
            return null; // null = unrestricted
        }
        final Set<Long> allowed = new HashSet<>();
        for (final SavingsProductPaymentChannel channel : catalog) {
            final Long paymentTypeId = channel.getPaymentType().getId();
            if (!channel.isPremium()) {
                allowed.add(paymentTypeId);
            } else {
                final Optional<SavingsAccountPaymentChannel> sub = this.accountChannelRepository
                        .findFirstBySavingsAccountIdAndPaymentTypeIdAndStatusOrderByIdDesc(savingsAccountId, paymentTypeId,
                                SavingsPaymentChannelStatus.ACTIVE.getValue());
                if (sub.isPresent()) {
                    allowed.add(paymentTypeId);
                }
            }
        }
        return allowed;
    }

    public void validatePaymentTypeAllowed(final SavingsAccount account, final PaymentDetail paymentDetail) {
        if (paymentDetail == null || paymentDetail.getPaymentType() == null) {
            return;
        }
        final Long paymentTypeId = paymentDetail.getPaymentType().getId();
        final Set<Long> allowed = allowedPaymentTypeIds(account.getId(), account.productId());
        if (allowed == null) {
            return;
        }
        if (!allowed.contains(paymentTypeId)) {
            final List<ApiParameterError> errors = new ArrayList<>();
            final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("savingsaccount.transaction");
            baseDataValidator.reset().parameter("paymentTypeId").value(paymentTypeId)
                    .failWithCode("not.allowed.for.account.channel.subscription");
            throw new PlatformApiDataValidationException(errors);
        }
    }

    public Collection<PaymentTypeData> filterPaymentTypeOptions(final Long savingsAccountId, final Long productId,
            final Collection<PaymentTypeData> allPaymentTypes) {
        final Set<Long> allowed = allowedPaymentTypeIds(savingsAccountId, productId);
        if (allowed == null || allPaymentTypes == null) {
            return allPaymentTypes;
        }
        return allPaymentTypes.stream().filter(pt -> allowed.contains(pt.getId())).collect(Collectors.toList());
    }
}
