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
package org.apache.fineract.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.treasury.data.TreasuryExposureData;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.LimitCheck;
import org.apache.fineract.treasury.domain.TreasuryCounterparty;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyRepository;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TreasuryCounterpartyServiceTest {

    private final TreasuryCounterpartyRepository counterpartyRepository = mock(TreasuryCounterpartyRepository.class);
    private final TreasuryDealRepository dealRepository = mock(TreasuryDealRepository.class);
    private final TreasuryCounterpartyService service = new TreasuryCounterpartyService(null, this.counterpartyRepository,
            this.dealRepository, mock(TreasuryReferenceDataLookup.class), mock(JdbcTemplate.class));
    private final TreasuryCounterparty counterparty = new TreasuryCounterparty();

    @BeforeEach
    void setUp() {
        this.counterparty.setId(7L);
        this.counterparty.setShortCode("STB");
        this.counterparty.setExposureLimit(new BigDecimal("2000000000"));
        this.counterparty.setLimitCurrencyCode("UGX");
        this.counterparty.setWarningPercent(BigDecimal.valueOf(80));
        when(this.counterpartyRepository.findById(7L)).thenReturn(Optional.of(this.counterparty));
        // a settled placement rolled into an approved deal (interest capitalised), and another placement
        when(this.dealRepository.findByCounterpartyIdAndStatusIn(eq(7L), anyCollection()))
                .thenReturn(List.of(deal(31L, DealStatus.SETTLED, "500000000", null), deal(33L, DealStatus.APPROVED, "514794520.55", 31L),
                        deal(40L, DealStatus.SETTLED, "100000000", null)));
    }

    @Test
    void aRolledDealCountsOnceThroughItsReplacement() {
        final TreasuryExposureData exposure = this.service.exposure(7L);

        assertEquals(0, new BigDecimal("614794520.55").compareTo(exposure.exposure()));
        assertEquals(List.of(33L, 40L), exposure.deals().stream().map(TreasuryExposureData.DealExposure::dealId).toList());
    }

    @Test
    void aFurtherDealIsCheckedAgainstTheSameExposure() {
        final TreasuryDeal next = deal(50L, DealStatus.CAPTURED, "1000000000", null);
        next.setCurrencyCode("UGX");

        final LimitCheck.Result result = this.service.check(this.counterparty, next);

        assertEquals(0, new BigDecimal("1614794520.55").compareTo(result.exposureAfter()));
        assertEquals(LimitCheck.Outcome.WARNING, result.outcome());
    }

    private static TreasuryDeal deal(final Long id, final DealStatus status, final String exposure, final Long rolledFrom) {
        final TreasuryDeal deal = new TreasuryDeal();
        deal.setId(id);
        deal.setStatus(status);
        deal.setCurrencyCode("UGX");
        deal.setExposureAmount(new BigDecimal(exposure));
        deal.setRolledFromDealId(rolledFrom);
        return deal;
    }
}
