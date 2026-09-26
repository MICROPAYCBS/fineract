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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannel;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannelRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsPaymentChannelStatus;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannel;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SavingsAccountPaymentChannelAllowListServiceTest {

    private SavingsProductPaymentChannelRepository productChannelRepository;
    private SavingsAccountPaymentChannelRepository accountChannelRepository;
    private SavingsAccountPaymentChannelAllowListService service;

    @BeforeEach
    void setUp() {
        productChannelRepository = mock(SavingsProductPaymentChannelRepository.class);
        accountChannelRepository = mock(SavingsAccountPaymentChannelRepository.class);
        service = new SavingsAccountPaymentChannelAllowListService(productChannelRepository, accountChannelRepository);
    }

    @Test
    void emptyCatalogMeansUnrestricted() {
        when(productChannelRepository.findByProductIdAndActiveTrue(1L)).thenReturn(List.of());
        assertNull(service.allowedPaymentTypeIds(10L, 1L));
    }

    @Test
    void nonPremiumAlwaysAllowedPremiumRequiresSubscription() {
        final PaymentType free = paymentType(1L);
        final PaymentType premium = paymentType(2L);
        final SavingsProductPaymentChannel freeChannel = channel(free, false);
        final SavingsProductPaymentChannel premiumChannel = channel(premium, true);
        when(productChannelRepository.findByProductIdAndActiveTrue(1L)).thenReturn(List.of(freeChannel, premiumChannel));
        when(accountChannelRepository.findFirstBySavingsAccountIdAndPaymentTypeIdAndStatusOrderByIdDesc(eq(10L), eq(2L),
                eq(SavingsPaymentChannelStatus.ACTIVE.getValue()))).thenReturn(Optional.empty());

        final Set<Long> allowed = service.allowedPaymentTypeIds(10L, 1L);
        assertEquals(Set.of(1L), allowed);
    }

    @Test
    void premiumAllowedWhenSubscribed() {
        final PaymentType premium = paymentType(2L);
        when(productChannelRepository.findByProductIdAndActiveTrue(1L)).thenReturn(List.of(channel(premium, true)));
        when(accountChannelRepository.findFirstBySavingsAccountIdAndPaymentTypeIdAndStatusOrderByIdDesc(eq(10L), eq(2L),
                eq(SavingsPaymentChannelStatus.ACTIVE.getValue()))).thenReturn(Optional.of(mock(SavingsAccountPaymentChannel.class)));

        assertEquals(Set.of(2L), service.allowedPaymentTypeIds(10L, 1L));
    }

    @Test
    void validateRejectsDisallowedPaymentType() {
        final PaymentType premium = paymentType(2L);
        when(productChannelRepository.findByProductIdAndActiveTrue(1L)).thenReturn(List.of(channel(premium, true)));
        when(accountChannelRepository.findFirstBySavingsAccountIdAndPaymentTypeIdAndStatusOrderByIdDesc(any(), any(), any()))
                .thenReturn(Optional.empty());

        final SavingsAccount account = mock(SavingsAccount.class);
        when(account.getId()).thenReturn(10L);
        when(account.productId()).thenReturn(1L);
        final PaymentDetail detail = mock(PaymentDetail.class);
        when(detail.getPaymentType()).thenReturn(premium);

        assertThrows(PlatformApiDataValidationException.class, () -> service.validatePaymentTypeAllowed(account, detail));
    }

    @Test
    void filterPaymentTypeOptionsKeepsAllowedOnly() {
        final PaymentType free = paymentType(1L);
        when(productChannelRepository.findByProductIdAndActiveTrue(1L)).thenReturn(List.of(channel(free, false)));

        final List<PaymentTypeData> all = List.of(PaymentTypeData.builder().id(1L).name("Cash").build(),
                PaymentTypeData.builder().id(2L).name("Mobile").build());
        final var filtered = service.filterPaymentTypeOptions(10L, 1L, all);
        assertEquals(1, filtered.size());
        assertTrue(filtered.stream().anyMatch(p -> p.getId().equals(1L)));
    }

    private static PaymentType paymentType(final Long id) {
        final PaymentType pt = new PaymentType();
        pt.setId(id);
        pt.setName("PT-" + id);
        return pt;
    }

    private static SavingsProductPaymentChannel channel(final PaymentType paymentType, final boolean premium) {
        return new SavingsProductPaymentChannel().setPaymentType(paymentType).setPremium(premium).setActive(true);
    }
}
