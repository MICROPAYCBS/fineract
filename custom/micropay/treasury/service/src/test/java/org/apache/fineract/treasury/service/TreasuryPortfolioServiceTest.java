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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.treasury.data.TreasuryPortfolioData;
import org.apache.fineract.treasury.data.TreasuryPortfolioData.Holding;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyRepository;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.junit.jupiter.api.Test;

class TreasuryPortfolioServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 30);

    private final TreasuryDealRepository dealRepository = mock(TreasuryDealRepository.class);
    private final TreasuryDealTypeService dealTypeService = mock(TreasuryDealTypeService.class);
    private final TreasuryCounterpartyRepository counterparties = mock(TreasuryCounterpartyRepository.class);
    private final TreasuryPledgeService pledgeService = mock(TreasuryPledgeService.class);
    private final TreasuryPortfolioService service = new TreasuryPortfolioService(this.dealRepository, this.dealTypeService,
            this.counterparties, this.pledgeService);

    @Test
    void holdingsCarryTheirMeasurementAndTotalsAddUp() {
        // FVOCI bond: amortised cost 98,545,000 + 1,198,895.03 accrued, adjustment -345,000, allowance 717,832.04 in
        // OCI
        final TreasuryDeal bond = TreasuryValuationServiceTest.bond(DealClassification.FVOCI);
        bond.setAmortisationAccrued(new BigDecimal("45000"));
        bond.setFairValueAdjustment(new BigDecimal("-345000"));
        bond.setLastFairValue(new BigDecimal("98200000"));
        bond.setEclAllowance(new BigDecimal("717832.04"));
        // placement at amortised cost with an allowance, half of it pledged
        final TreasuryDeal placement = TreasuryValuationServiceTest.bill();
        placement.setId(7L);
        placement.setDealTypeId(6L);
        placement.setClassification(DealClassification.AMORTISED_COST);
        placement.setEclAllowance(new BigDecimal("1000000"));
        final TreasuryDeal borrowing = TreasuryValuationServiceTest.bill();
        borrowing.setId(6L);
        borrowing.setDealTypeId(4L);
        when(this.dealRepository.findByStatusOrderByIdAsc(DealStatus.SETTLED)).thenReturn(List.of(bond, placement, borrowing));
        when(this.dealTypeService.find(5L)).thenReturn(TreasuryValuationServiceTest.type(InstrumentKind.GOVERNMENT_BOND));
        when(this.dealTypeService.find(6L)).thenReturn(TreasuryValuationServiceTest.type(InstrumentKind.TREASURY_BILL));
        when(this.dealTypeService.find(4L)).thenReturn(TreasuryValuationServiceTest.type(InstrumentKind.BORROWING));
        when(this.counterparties.findById(any())).thenReturn(Optional.empty());
        when(this.pledgeService.encumberedAmount(any(), eq(DATE))).thenReturn(BigDecimal.ZERO);
        when(this.pledgeService.encumberedAmount(eq(7L), eq(DATE))).thenReturn(new BigDecimal("500000000"));
        when(this.pledgeService.unencumberedAmount(bond, DATE)).thenReturn(new BigDecimal("100000000"));
        when(this.pledgeService.unencumberedAmount(placement, DATE)).thenReturn(new BigDecimal("500000000"));

        final TreasuryPortfolioData portfolio = this.service.portfolio(DATE, "ugx");

        assertEquals(2, portfolio.holdings().size(), "the borrowing is not a holding");
        final Holding b = portfolio.holdings().getFirst();
        assertEquals(new BigDecimal("98545000"), b.amortisedCost());
        assertEquals(new BigDecimal("99743895.03"), b.grossCarryingAmount());
        assertEquals(new BigDecimal("99398895.03"), b.carryingAmount(), "FVOCI: at fair value, the allowance is in OCI");
        assertEquals(new BigDecimal("372832.04"), b.ociReserve());
        assertEquals(new BigDecimal("98200000"), b.fairValue());
        final Holding p = portfolio.holdings().getLast();
        assertNull(p.fairValue());
        assertEquals(new BigDecimal("972307378.47"), p.carryingAmount(), "amortised cost less the allowance");
        assertEquals(new BigDecimal("500000000"), p.pledged());
        assertEquals("UGX", portfolio.totals().getFirst().currencyCode());
        assertEquals(new BigDecimal("1071706273.50"), portfolio.totals().getFirst().carryingAmount());
        assertEquals(new BigDecimal("600000000"), portfolio.totals().getFirst().unencumbered());
    }
}
