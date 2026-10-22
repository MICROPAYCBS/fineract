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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.treasury.deal.DayCount;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.deal.PriceSource;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealEvent;
import org.apache.fineract.treasury.domain.TreasuryDealEventRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEventType;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.domain.TreasuryDealValuationRepository;
import org.apache.fineract.treasury.domain.TreasuryEventType;
import org.apache.fineract.treasury.domain.TreasuryPrice;
import org.apache.fineract.treasury.domain.TreasurySetting;
import org.apache.fineract.treasury.service.DealValuationPlanner.Release;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;
import org.apache.fineract.treasury.service.TreasuryValuationService.Outcome;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Worked examples: a 100,000,000 bond (14%, bought at 98.50 with a 1,500,000 discount) at FVOCI revalued up, then down,
 * then sold; a 1,000,000,000 91-day bill bought at 11% held at FVTPL.
 */
class TreasuryValuationServiceTest {

    private static final long USER = 1L;

    private final TreasuryDealValuationRepository valuations = mock(TreasuryDealValuationRepository.class);
    private final TreasuryPriceService prices = mock(TreasuryPriceService.class);
    private final TreasurySettingService settings = mock(TreasurySettingService.class);
    private final TreasuryJournalPoster poster = mock(TreasuryJournalPoster.class);
    private final TreasuryDealEventRepository events = mock(TreasuryDealEventRepository.class);
    private final TreasuryReferenceDataLookup lookup = mock(TreasuryReferenceDataLookup.class);
    private final TreasuryValuationService service = new TreasuryValuationService(mock(TreasuryDealRepository.class),
            mock(TreasuryDealTypeService.class), this.valuations, this.prices, this.settings, this.poster, this.events, this.lookup,
            mock(AppUserRepositoryWrapper.class), mock(PlatformTransactionManager.class));
    private final List<TreasuryDealEvent> recorded = new ArrayList<>();
    private final Map<String, TreasuryPrice> priceBook = new HashMap<>();
    private long valuationCount;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", mock(FineractPlatformTenantConnection.class)));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 12, 20))));
        lenient().when(this.lookup.decimalPlaces("UGX")).thenReturn(2);
        lenient().when(this.settings.settingsFor("UGX")).thenReturn(new TreasurySetting());
        lenient().when(this.valuations.countByDealId(any())).thenAnswer(call -> this.valuationCount);
        lenient().when(this.valuations.save(any())).thenAnswer(call -> {
            this.valuationCount++;
            return call.getArgument(0);
        });
        lenient().when(this.events.save(any())).thenAnswer(call -> {
            this.recorded.add(call.getArgument(0));
            return call.getArgument(0);
        });
        lenient().when(this.poster.post(any(), any(), anyString(), anyString(), any(), anyString(), any(), any()))
                .thenAnswer(call -> call.getArgument(2));
        lenient().when(this.prices.latest(anyString(), any())).thenAnswer(call -> this.priceBook.entrySet().stream()
                .filter(e -> e.getKey().startsWith(call.getArgument(0) + "|") && !e.getValue().getPriceDate().isAfter(call.getArgument(1)))
                .map(Map.Entry::getValue).max((a, b) -> a.getPriceDate().compareTo(b.getPriceDate())));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void fvociBondUpThenDownThenStale() {
        final TreasuryDealType type = type(InstrumentKind.GOVERNMENT_BOND);
        final TreasuryDeal bond = bond(DealClassification.FVOCI);

        // end of October: 20,000 of the discount accreted, carried at 98,520,000; quoted at 99.00 -> +480,000 to OCI
        bond.setAccruedToDate(LocalDate.of(2026, 11, 1));
        bond.setAmortisationAccrued(new BigDecimal("20000"));
        price("UG0000001234", LocalDate.of(2026, 10, 30), "99");
        assertEquals(Outcome.REVALUED, this.service.revalue(bond, type, LocalDate.of(2026, 10, 31), USER));
        verify(this.poster).post(eq(TreasuryEventType.DEAL_VALUATION), eq(9L), eq("TR-DV-9-1"), eq("UGX"), eq(LocalDate.of(2026, 10, 31)),
                anyString(), any(), eq(List.of(leg(40, true, "480000.00"), leg(43, false, "480000.00"))));
        assertEquals(new BigDecimal("480000.00"), bond.getFairValueAdjustment());

        // a rerun for the same day changes nothing
        assertEquals(Outcome.UNCHANGED, this.service.revalue(bond, type, LocalDate.of(2026, 10, 31), USER));

        // end of November: carried at 98,545,000, quoted at 98.20 -> adjustment -345,000, a change of -825,000
        bond.setAccruedToDate(LocalDate.of(2026, 12, 1));
        bond.setAmortisationAccrued(new BigDecimal("45000"));
        price("UG0000001234", LocalDate.of(2026, 11, 28), "98.2");
        assertEquals(Outcome.REVALUED, this.service.revalue(bond, type, LocalDate.of(2026, 11, 30), USER));
        verify(this.poster).post(eq(TreasuryEventType.DEAL_VALUATION), eq(9L), eq("TR-DV-9-2"), eq("UGX"), eq(LocalDate.of(2026, 11, 30)),
                anyString(), any(), eq(List.of(leg(43, true, "825000.00"), leg(40, false, "825000.00"))));
        assertEquals(new BigDecimal("-345000.00"), bond.getFairValueAdjustment());
        assertEquals(new BigDecimal("98200000.00"), bond.getLastFairValue());

        // mid-December the latest price (28 November) is 17 days old: not revalued
        bond.setAccruedToDate(LocalDate.of(2026, 12, 16));
        assertEquals(Outcome.STALE, this.service.revalue(bond, type, LocalDate.of(2026, 12, 15), USER));
        assertEquals(TreasuryDealEventType.STALE_PRICE, this.recorded.getLast().getEventType());
        assertEquals(new BigDecimal("-345000.00"), bond.getFairValueAdjustment());

        // sold: the -345,000 leaves the reserve with the adjustment; the loss reaches profit or loss through the
        // realised result, measured from amortised cost
        final Release release = this.service.release(bond, type);
        assertEquals(List.of(leg(40, true, "345000.00"), leg(43, false, "345000.00")), release.legs());
        assertEquals(new BigDecimal("-345000.00"), release.ociRecycled());
        this.service.released(bond, release, USER, "TR-DT-9");
        assertEquals(0, bond.getFairValueAdjustment().signum());
        assertEquals(TreasuryDealEventType.FAIR_VALUE_RELEASED, this.recorded.getLast().getEventType());
    }

    @Test
    void fvtplBillGainsAsItsYieldFalls() {
        final TreasuryDealType type = type(InstrumentKind.TREASURY_BILL);
        final TreasuryDeal bill = bill();
        // after 31 days at 11% the bill is carried at 982,238,966.63; the market yield is now 10.5%
        bill.setAccruedToDate(LocalDate.of(2026, 11, 16));
        bill.setInterestAccrued(new BigDecimal("8931588.16"));
        price("TB-2027-01-15", LocalDate.of(2026, 11, 13), null).setYieldRate(new BigDecimal("10.5"));

        assertEquals(Outcome.REVALUED, this.service.revalue(bill, type, LocalDate.of(2026, 11, 15), USER));
        verify(this.poster).post(eq(TreasuryEventType.DEAL_VALUATION), eq(8L), eq("TR-DV-8-1"), eq("UGX"), eq(LocalDate.of(2026, 11, 15)),
                anyString(), any(), eq(List.of(leg(40, true, "793621.57"), leg(41, false, "793621.57"))));
        assertEquals(new BigDecimal("983032588.20"), bill.getLastFairValue());

        // at maturity the adjustment is reversed against realised gain or loss
        this.service.releaseAtMaturity(bill, type, USER);
        verify(this.poster).post(eq(TreasuryEventType.DEAL_VALUATION), eq(8L), eq("TR-DV-8-M"), eq("UGX"), eq(LocalDate.of(2027, 1, 15)),
                anyString(), any(), eq(List.of(leg(30, true, "793621.57"), leg(40, false, "793621.57"))));
        assertEquals(new BigDecimal("-793621.57"), bill.getRealisedResult());
        assertEquals(0, bill.getFairValueAdjustment().signum());
    }

    @Test
    void missingPriceAndAmortisedCostAreNotRevalued() {
        final TreasuryDealType type = type(InstrumentKind.GOVERNMENT_BOND);
        final TreasuryDeal bond = bond(DealClassification.FVOCI);
        bond.setAccruedToDate(LocalDate.of(2026, 11, 1));

        assertEquals(Outcome.STALE, this.service.revalue(bond, type, LocalDate.of(2026, 10, 31), USER));
        assertTrue(this.recorded.getLast().getNote().startsWith("No clean price of UG0000001234"));

        final TreasuryDealType withoutAccounts = type(InstrumentKind.GOVERNMENT_BOND);
        withoutAccounts.setFairValueReserveGlAccountId(null);
        assertEquals(Outcome.NOT_REVALUED, this.service.revalue(bond, withoutAccounts, LocalDate.of(2026, 10, 31), USER),
                "a deal captured at fair value before its type had valuation accounts is left alone");

        final TreasuryDeal atCost = bond(DealClassification.AMORTISED_COST);
        atCost.setAccruedToDate(LocalDate.of(2026, 11, 1));
        price("UG0000001234", LocalDate.of(2026, 10, 30), "99");
        assertEquals(Outcome.NOT_REVALUED, this.service.revalue(atCost, type, LocalDate.of(2026, 10, 31), USER));
        assertTrue(this.service.release(atCost, type).legs().isEmpty());
        verify(this.poster, never()).post(any(), any(), anyString(), anyString(), any(), anyString(), any(), any());
    }

    private TreasuryPrice price(final String instrument, final LocalDate date, final String clean) {
        final TreasuryPrice price = new TreasuryPrice();
        price.setInstrumentId(instrument);
        price.setPriceDate(date);
        price.setCleanPrice(clean == null ? null : new BigDecimal(clean));
        price.setSource(PriceSource.BOU_SECONDARY);
        this.priceBook.put(instrument + "|" + date, price);
        return price;
    }

    static TreasuryDealType type(final InstrumentKind kind) {
        final TreasuryDealType type = new TreasuryDealType();
        type.setId(5L);
        type.setCode("HT");
        type.setInstrumentKind(kind);
        type.setPrincipalGlAccountId(20L);
        type.setInterestReceivableGlAccountId(21L);
        type.setInterestIncomeGlAccountId(23L);
        type.setDiscountGlAccountId(26L);
        type.setRealisedGainGlAccountId(29L);
        type.setRealisedLossGlAccountId(30L);
        type.setFairValueAdjustmentGlAccountId(40L);
        type.setFairValueGainGlAccountId(41L);
        type.setFairValueLossGlAccountId(42L);
        type.setFairValueReserveGlAccountId(43L);
        type.setImpairmentExpenseGlAccountId(44L);
        type.setEclAllowanceGlAccountId(45L);
        return type;
    }

    static TreasuryDeal bond(final DealClassification classification) {
        final TreasuryDeal deal = deal(9L, LocalDate.of(2031, 3, 15), new BigDecimal("100000000"), classification);
        deal.setIsin("UG0000001234");
        deal.setDayCount(DayCount.ACT_ACT);
        deal.setFrequency(2);
        deal.setCleanPrice(new BigDecimal("98.5"));
        deal.setConsideration(new BigDecimal("99698895.03"));
        deal.setAccruedInterestPurchased(new BigDecimal("1198895.03"));
        deal.setDiscount(new BigDecimal("1500000"));
        return deal;
    }

    static TreasuryDeal bill() {
        final TreasuryDeal deal = deal(8L, LocalDate.of(2027, 1, 15), new BigDecimal("1000000000"), DealClassification.FVTPL);
        deal.setExternalReference("TB-2027-01-15");
        deal.setDayCount(DayCount.ACT_365);
        deal.setRate(new BigDecimal("11"));
        deal.setConsideration(new BigDecimal("973307378.47"));
        return deal;
    }

    private static TreasuryDeal deal(final Long id, final LocalDate maturity, final BigDecimal amount,
            final DealClassification classification) {
        final TreasuryDeal deal = new TreasuryDeal();
        deal.setId(id);
        deal.setDealTypeId(5L);
        deal.setCounterpartyId(7L);
        deal.setOfficeId(1L);
        deal.setCurrencyCode("UGX");
        deal.setValueDate(LocalDate.of(2026, 10, 16));
        deal.setMaturityDate(maturity);
        deal.setAmount(amount);
        deal.setRate(new BigDecimal("14"));
        deal.setClassification(classification);
        deal.setAccruedInterestPurchased(BigDecimal.ZERO);
        deal.setPremium(BigDecimal.ZERO);
        deal.setDiscount(BigDecimal.ZERO);
        deal.setStatus(DealStatus.SETTLED);
        return deal;
    }

    private static Leg leg(final long gl, final boolean debit, final String amount) {
        return new Leg(1L, gl, debit ? JournalEntryType.DEBIT : JournalEntryType.CREDIT, new BigDecimal(amount));
    }

}
