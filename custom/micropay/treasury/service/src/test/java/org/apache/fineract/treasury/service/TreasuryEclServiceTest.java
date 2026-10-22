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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.domain.TreasuryCounterparty;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyRepository;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealEcl;
import org.apache.fineract.treasury.domain.TreasuryDealEclRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEvent;
import org.apache.fineract.treasury.domain.TreasuryDealEventRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEventType;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.domain.TreasuryEclParameter;
import org.apache.fineract.treasury.domain.TreasuryEclParameterRepository;
import org.apache.fineract.treasury.domain.TreasuryEventType;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Worked example: a 500,000,000 placement at 12% with 7,397,260.27 accrued after 45 days (exposure 507,397,260.27),
 * with a counterparty rated AA- (stage 1: 12-month PD 0.5%; stage 2: lifetime PD 4%; LGD 45%).
 */
class TreasuryEclServiceTest {

    private static final LocalDate BUSINESS = LocalDate.of(2026, 11, 30);
    private static final long OPS = 5L;

    private final TreasuryActor actor = mock(TreasuryActor.class);
    private final TreasuryDealRepository dealRepository = mock(TreasuryDealRepository.class);
    private final TreasuryDealTypeService dealTypeService = mock(TreasuryDealTypeService.class);
    private final TreasuryDealEclRepository eclRepository = mock(TreasuryDealEclRepository.class);
    private final TreasuryEclParameterRepository parameters = mock(TreasuryEclParameterRepository.class);
    private final TreasuryCounterpartyRepository counterparties = mock(TreasuryCounterpartyRepository.class);
    private final TreasuryReferenceDataLookup lookup = mock(TreasuryReferenceDataLookup.class);
    private final TreasuryJournalPoster poster = mock(TreasuryJournalPoster.class);
    private final TreasuryDealEventRepository events = mock(TreasuryDealEventRepository.class);
    private final AppUserRepositoryWrapper users = mock(AppUserRepositoryWrapper.class);
    private final TreasuryEclService service = new TreasuryEclService(new TreasuryHoldingDataValidator(new FromJsonHelper()), this.actor,
            this.dealRepository, this.dealTypeService, this.eclRepository, this.parameters, this.counterparties, this.lookup, this.poster,
            this.events, this.users, mock(PlatformTransactionManager.class));
    private final List<TreasuryDealEcl> rows = new ArrayList<>();
    private final List<TreasuryDealEvent> recorded = new ArrayList<>();
    private final TreasuryCounterparty counterparty = new TreasuryCounterparty();

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", mock(FineractPlatformTenantConnection.class)));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS)));
        lenient().when(this.actor.userId(any())).thenReturn(OPS);
        lenient().when(this.lookup.decimalPlaces("UGX")).thenReturn(2);
        this.counterparty.setRating("aa- ");
        lenient().when(this.counterparties.findById(7L)).thenReturn(Optional.of(this.counterparty));
        lenient().when(this.eclRepository.countByDealId(any())).thenAnswer(call -> (long) this.rows.size());
        lenient().when(this.eclRepository.save(any())).thenAnswer(call -> {
            this.rows.add(call.getArgument(0));
            return call.getArgument(0);
        });
        lenient().when(this.events.save(any())).thenAnswer(call -> {
            this.recorded.add(call.getArgument(0));
            return call.getArgument(0);
        });
        lenient().when(this.poster.post(any(), any(), anyString(), anyString(), any(), anyString(), any(), any()))
                .thenAnswer(call -> call.getArgument(2));
        lenient().when(this.parameters.findByRatingCodeAndStage(anyString(), anyInt())).thenReturn(Optional.empty());
        final AppUser system = mock(AppUser.class);
        lenient().when(system.getId()).thenReturn(1L);
        lenient().when(this.users.fetchSystemUser()).thenReturn(system);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void stageOneThenTwoThenBackToOne() {
        parameter("AA-", 1, "0.5", "2", "45");
        parameter("AA-", 2, "0.5", "4", "45");
        final TreasuryDeal deal = placement();

        Map<String, Object> changes = this.service.recalculate(40L, JsonCommand.from("{}")).getChanges();
        assertEquals(new BigDecimal("507397260.27"), changes.get("exposure"));
        assertEquals(new BigDecimal("1141643.84"), changes.get("ecl"));
        verify(this.poster).post(eq(TreasuryEventType.DEAL_IMPAIRMENT), eq(40L), eq("TR-DE-40-1"), eq("UGX"), eq(BUSINESS), anyString(),
                any(), eq(List.of(leg(44, true, "1141643.84"), leg(45, false, "1141643.84"))));

        changes = this.service.setStage(40L, JsonCommand.from("{\"stage\": 2, \"reason\": \"Rating watch negative\"}")).getChanges();
        assertEquals(new BigDecimal("9133150.68"), changes.get("ecl"));
        assertEquals(new BigDecimal("7991506.84"), changes.get("eclChange"));
        assertEquals(1, changes.get("previousStage"));
        assertEquals(TreasuryDealEventType.ECL_STAGE_CHANGED, this.recorded.get(this.recorded.size() - 2).getEventType());

        changes = this.service.setStage(40L, JsonCommand.from("{\"stage\": 1, \"reason\": \"Watch removed\"}")).getChanges();
        assertEquals(new BigDecimal("-7991506.84"), changes.get("eclChange"));
        verify(this.poster).post(eq(TreasuryEventType.DEAL_IMPAIRMENT), eq(40L), eq("TR-DE-40-3"), eq("UGX"), eq(BUSINESS), anyString(),
                any(), eq(List.of(leg(45, true, "7991506.84"), leg(44, false, "7991506.84"))));
        assertEquals(new BigDecimal("1141643.84"), deal.getEclAllowance());
        assertEquals("AA-", this.rows.getLast().getBasis());

        // released at maturity or termination
        this.service.release(deal, type(), LocalDate.of(2027, 1, 14), 1L);
        verify(this.poster).post(eq(TreasuryEventType.DEAL_IMPAIRMENT), eq(40L), eq("TR-DE-40-4"), eq("UGX"), eq(LocalDate.of(2027, 1, 14)),
                anyString(), any(), eq(List.of(leg(45, true, "1141643.84"), leg(44, false, "1141643.84"))));
        assertEquals(0, deal.getEclAllowance().signum());
        assertEquals(TreasuryDealEventType.ECL_RELEASED, this.recorded.getLast().getEventType());
    }

    @Test
    void fvociAllowanceGoesToTheReserve() {
        parameter("DEFAULT", 1, "1.2", "5", "60");
        this.counterparty.setRating(null);
        final TreasuryDeal bond = TreasuryValuationServiceTest.bond(DealClassification.FVOCI);
        bond.setId(41L);
        when(this.dealRepository.findById(41L)).thenReturn(Optional.of(bond));
        when(this.dealTypeService.find(5L)).thenReturn(TreasuryValuationServiceTest.type(InstrumentKind.GOVERNMENT_BOND));

        // exposure 100,000,000 - 1,500,000 + 1,198,895.03 accrued = 99,698,895.03; x 1.2% x 60%
        final Map<String, Object> changes = this.service.recalculate(41L, JsonCommand.from("{}")).getChanges();

        assertEquals(new BigDecimal("717832.04"), changes.get("ecl"));
        verify(this.poster).post(eq(TreasuryEventType.DEAL_IMPAIRMENT), eq(41L), eq("TR-DE-41-1"), eq("UGX"), eq(BUSINESS), anyString(),
                any(), eq(List.of(leg(44, true, "717832.04"), leg(43, false, "717832.04"))));
    }

    @Test
    void manualOverridesAndStageThree() {
        parameter("AA-", 3, "100", "100", "45");
        final TreasuryDeal deal = placement();

        Map<String, Object> changes = this.service
                .setStage(40L, JsonCommand.from("{\"stage\": 3, \"reason\": \"Bank in receivership\", \"manualLgd\": 60}")).getChanges();
        assertEquals(new BigDecimal("304438356.16"), changes.get("ecl"));
        assertEquals(Boolean.TRUE, changes.get("stage3InterestOnGross"));
        assertEquals("MANUAL+AA-", this.rows.getLast().getBasis());

        changes = this.service.setStage(40L, JsonCommand.from(
                "{\"stage\": 3, \"reason\": \"Recovery agreed\", \"manualEcl\": 250000000, \"manualEclReason\": \"Liquidator's estimate\"}"))
                .getChanges();
        assertEquals(new BigDecimal("250000000.00"), changes.get("ecl"));
        assertEquals("MANUAL_ECL", this.rows.getLast().getBasis());

        this.service.setStage(40L, JsonCommand.from("{\"stage\": 3, \"reason\": \"Back to the table\", \"clearOverrides\": true}"));
        assertEquals(new BigDecimal("228328767.12"), deal.getEclAllowance());
    }

    @Test
    void missingParametersAndFvtplAreRefused() {
        placement();
        assertCode("deal.ecl.parameters.missing", () -> this.service.recalculate(40L, JsonCommand.from("{}")));

        final TreasuryDeal bill = TreasuryValuationServiceTest.bill();
        when(this.dealRepository.findById(8L)).thenReturn(Optional.of(bill));
        when(this.dealTypeService.find(5L)).thenReturn(TreasuryValuationServiceTest.type(InstrumentKind.TREASURY_BILL));
        assertCode("deal.ecl.not.applicable", () -> this.service.recalculate(8L, JsonCommand.from("{}")));
    }

    @Test
    void jobSkipsAHoldingWithoutParametersAndLeavesOneAlreadyCalculated() {
        final TreasuryDeal deal = placement();
        when(this.dealRepository.findByStatusOrderByIdAsc(DealStatus.SETTLED)).thenReturn(List.of(deal));

        TreasuryEclService.RunSummary summary = this.service.run(LocalDate.of(2026, 11, 29));
        assertEquals(1, summary.skipped());
        assertEquals(TreasuryDealEventType.ECL_SKIPPED, this.recorded.getLast().getEventType());

        parameter("AA-", 1, "0.5", "2", "45");
        summary = this.service.run(LocalDate.of(2026, 11, 29));
        assertEquals(1, summary.calculated());
        summary = this.service.run(LocalDate.of(2026, 11, 29));
        assertEquals(0, summary.calculated());
        assertEquals(1, this.rows.size());
    }

    private TreasuryDeal placement() {
        final TreasuryDeal deal = new TreasuryDeal();
        deal.setId(40L);
        deal.setDealTypeId(6L);
        deal.setCounterpartyId(7L);
        deal.setOfficeId(1L);
        deal.setCurrencyCode("UGX");
        deal.setValueDate(LocalDate.of(2026, 10, 16));
        deal.setMaturityDate(LocalDate.of(2027, 1, 14));
        deal.setAmount(new BigDecimal("500000000"));
        deal.setConsideration(new BigDecimal("500000000"));
        deal.setClassification(DealClassification.AMORTISED_COST);
        deal.setAccruedInterestPurchased(BigDecimal.ZERO);
        deal.setPremium(BigDecimal.ZERO);
        deal.setDiscount(BigDecimal.ZERO);
        deal.setInterestAccrued(new BigDecimal("7397260.27"));
        deal.setStatus(DealStatus.SETTLED);
        lenient().when(this.dealRepository.findById(40L)).thenReturn(Optional.of(deal));
        lenient().when(this.dealTypeService.find(6L)).thenReturn(type());
        return deal;
    }

    private static TreasuryDealType type() {
        final TreasuryDealType type = TreasuryValuationServiceTest.type(InstrumentKind.MONEY_MARKET_PLACEMENT);
        type.setId(6L);
        return type;
    }

    private void parameter(final String rating, final int stage, final String pd12, final String pdLife, final String lgd) {
        final TreasuryEclParameter parameter = new TreasuryEclParameter();
        parameter.setRatingCode(rating);
        parameter.setStage(stage);
        parameter.setPd12Month(new BigDecimal(pd12));
        parameter.setPdLifetime(new BigDecimal(pdLife));
        parameter.setLgd(new BigDecimal(lgd));
        when(this.parameters.findByRatingCodeAndStage(rating, stage)).thenReturn(Optional.of(parameter));
    }

    private static Leg leg(final long gl, final boolean debit, final String amount) {
        return new Leg(1L, gl, debit ? JournalEntryType.DEBIT : JournalEntryType.CREDIT, new BigDecimal(amount));
    }

    private static void assertCode(final String suffix, final org.junit.jupiter.api.function.Executable call) {
        assertEquals("error.msg.treasury." + suffix, assertThrows(TreasuryWorkflowException.class, call).getGlobalisationMessageCode());
    }
}
