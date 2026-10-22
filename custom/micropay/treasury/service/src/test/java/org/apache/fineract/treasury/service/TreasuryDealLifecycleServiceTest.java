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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.accounting.journalentry.exception.JournalEntryInvalidException;
import org.apache.fineract.accounting.journalentry.exception.JournalEntryInvalidException.GlJournalEntryInvalidReason;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.treasury.deal.CashflowStatus;
import org.apache.fineract.treasury.deal.DayCount;
import org.apache.fineract.treasury.deal.DealCalculator.Flow;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.DealPricer;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealAccrualRepository;
import org.apache.fineract.treasury.domain.TreasuryDealCashflow;
import org.apache.fineract.treasury.domain.TreasuryDealCashflowRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEventRepository;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.domain.TreasuryEventType;
import org.apache.fineract.treasury.domain.TreasurySetting;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class TreasuryDealLifecycleServiceTest {

    private static final LocalDate VALUE = LocalDate.of(2026, 10, 16);
    private static final LocalDate MATURITY = LocalDate.of(2027, 1, 14);
    private static final long SETTLER = 4L;
    private static final long OPS = 5L;

    private final TreasuryActor actor = mock(TreasuryActor.class);
    private final TreasuryDealRepository dealRepository = mock(TreasuryDealRepository.class);
    private final TreasuryDealCashflowRepository cashflowRepository = mock(TreasuryDealCashflowRepository.class);
    private final TreasuryDealAccrualRepository accrualRepository = mock(TreasuryDealAccrualRepository.class);
    private final TreasuryAccountRepository accountRepository = mock(TreasuryAccountRepository.class);
    private final TreasuryDealTypeService dealTypeService = mock(TreasuryDealTypeService.class);
    private final TreasuryDealWriteService dealWriteService = mock(TreasuryDealWriteService.class);
    private final TreasurySettingService settingService = mock(TreasurySettingService.class);
    private final TreasuryBalanceReader balanceReader = mock(TreasuryBalanceReader.class);
    private final TreasuryJournalPoster journalPoster = mock(TreasuryJournalPoster.class);
    private final TreasuryReferenceDataLookup lookup = mock(TreasuryReferenceDataLookup.class);
    private final AppUserRepositoryWrapper users = mock(AppUserRepositoryWrapper.class);
    private final TreasuryDealLifecycleService service = new TreasuryDealLifecycleService(this.actor,
            new TreasuryDealDataValidator(new FromJsonHelper()), this.dealRepository, this.cashflowRepository,
            mock(TreasuryDealEventRepository.class), this.accrualRepository, this.accountRepository, this.dealTypeService,
            this.dealWriteService, this.settingService, this.balanceReader, this.journalPoster, this.lookup, this.users,
            mock(PlatformTransactionManager.class));
    private final TreasurySetting setting = new TreasurySetting();
    private final TreasuryDealType type = new TreasuryDealType();
    private final Map<Long, List<TreasuryDealCashflow>> flows = new HashMap<>();
    private final AtomicLong flowIds = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", mock(FineractPlatformTenantConnection.class)));
        business(LocalDate.of(2027, 1, 20));
        this.type.setId(5L);
        this.type.setCode("MM");
        this.type.setInstrumentKind(InstrumentKind.MONEY_MARKET_PLACEMENT);
        this.type.setPrincipalGlAccountId(20L);
        this.type.setInterestReceivableGlAccountId(21L);
        this.type.setInterestIncomeGlAccountId(23L);
        this.type.setRealisedGainGlAccountId(29L);
        this.type.setRealisedLossGlAccountId(30L);
        lenient().when(this.dealTypeService.find(5L)).thenReturn(this.type);
        final TreasuryAccount account = new TreasuryAccount();
        account.setId(3L);
        account.setGlAccountId(10L);
        account.setOfficeId(1L);
        account.setCurrencyCode("UGX");
        account.setStatus(TreasuryAccountStatus.ACTIVE);
        lenient().when(this.accountRepository.findById(3L)).thenReturn(Optional.of(account));
        lenient().when(this.settingService.settingsFor("UGX")).thenReturn(this.setting);
        lenient().when(this.lookup.decimalPlaces("UGX")).thenReturn(2);
        lenient().when(this.cashflowRepository.findByDealIdOrderBySequenceNumberAsc(any()))
                .thenAnswer(call -> this.flows.getOrDefault(call.getArgument(0), List.of()));
        lenient().when(this.journalPoster.post(any(), any(), anyString(), anyString(), any(), anyString(), any(), any()))
                .thenAnswer(call -> call.getArgument(2));
        lenient().when(this.journalPoster.post(any(), any(), anyString(), any(), anyString(), any(), any()))
                .thenAnswer(call -> "TR-DT-" + call.getArgument(1));
        final AppUser system = mock(AppUser.class);
        lenient().when(system.getId()).thenReturn(1L);
        lenient().when(this.users.fetchSystemUser()).thenReturn(system);
        lenient().when(this.actor.userId(any())).thenReturn(OPS);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void accrualPostsOnceForADate() {
        final TreasuryDeal deal = placement(40L);

        assertTrue(this.service.accrue(deal, this.type, VALUE.plusDays(45)));
        verify(this.journalPoster).post(eq(TreasuryEventType.DEAL_ACCRUAL), eq(40L), eq("TR-DA-40-1"), eq("UGX"), eq(VALUE.plusDays(44)),
                anyString(), any(), eq(List.of(new Leg(1L, 21L, JournalEntryType.DEBIT, new BigDecimal("7397260.27")),
                        new Leg(1L, 23L, JournalEntryType.CREDIT, new BigDecimal("7397260.27")))));
        assertEquals(new BigDecimal("7397260.27"), deal.getInterestAccrued());
        assertEquals(VALUE.plusDays(45), deal.getAccruedToDate());

        assertFalse(this.service.accrue(deal, this.type, VALUE.plusDays(45)), "a rerun for the same date posts nothing");
        assertFalse(this.service.accrue(deal, this.type, VALUE.plusDays(30)), "nor does an earlier date");
        verify(this.journalPoster, times(1)).post(any(), any(), anyString(), anyString(), any(), anyString(), any(), any());
    }

    @Test
    void jobMaturesThePlacementAndCountsAClosedPeriodAsSkipped() {
        final TreasuryDeal good = placement(40L);
        final TreasuryDeal closed = placement(41L);
        when(this.dealRepository.findByStatusOrderByIdAsc(DealStatus.SETTLED)).thenReturn(List.of(good, closed));
        when(this.journalPoster.post(any(), eq(41L), anyString(), anyString(), any(), anyString(), any(), any()))
                .thenThrow(new JournalEntryInvalidException(GlJournalEntryInvalidReason.ACCOUNTING_CLOSED, MATURITY, null, null));

        final TreasuryDealLifecycleService.RunSummary summary = this.service.run(LocalDate.of(2027, 1, 19));

        assertEquals(2, summary.deals());
        assertEquals(1, summary.skipped());
        assertEquals(1, summary.matured());
        assertEquals(DealStatus.MATURED, good.getStatus());
        assertEquals(0, good.getExposureAmount().signum());
        assertTrue(this.flows.get(40L).stream().allMatch(f -> f.getStatus() == CashflowStatus.SETTLED));
        assertEquals(new BigDecimal("14794520.55"), good.getInterestSettled());
        assertEquals(new BigDecimal("500000000.00"), good.getPrincipalSettled());
        verify(this.journalPoster).post(eq(TreasuryEventType.DEAL_CASHFLOW), eq(40L), anyString(), eq("UGX"), eq(MATURITY), anyString(),
                any(), eq(List.of(new Leg(1L, 10L, JournalEntryType.DEBIT, new BigDecimal("500000000.00")),
                        new Leg(1L, 20L, JournalEntryType.CREDIT, new BigDecimal("500000000.00")))));
    }

    @Test
    void autoSettleOffOnlyAccrues() {
        this.setting.setAutoSettleCashflows(false);
        final TreasuryDeal deal = placement(40L);
        when(this.dealRepository.findByStatusOrderByIdAsc(DealStatus.SETTLED)).thenReturn(List.of(deal));

        final TreasuryDealLifecycleService.RunSummary summary = this.service.run(LocalDate.of(2027, 1, 19));

        assertEquals(1, summary.accrued());
        assertEquals(0, summary.cashflowsSettled());
        assertEquals(DealStatus.SETTLED, deal.getStatus());
    }

    @Test
    void partialCashflowStaysOpen() {
        business(MATURITY);
        final TreasuryDeal deal = placement(40L);
        final TreasuryDealCashflow interest = this.flows.get(40L).getFirst();

        final Map<String, Object> changes = this.service
                .settleCashflow(40L, JsonCommand.from("{\"cashflowId\": " + interest.getId() + ", \"amount\": 14000000}")).getChanges();

        assertEquals("PARTIAL", changes.get("cashflowStatus"));
        assertEquals(new BigDecimal("794520.55"), changes.get("remaining"));
        assertEquals(DealStatus.SETTLED, deal.getStatus());
    }

    @Test
    void terminationAccruesThenBooksTheLoss() {
        business(VALUE.plusDays(45));
        final TreasuryDeal deal = placement(40L);

        final Map<String, Object> changes = this.service
                .terminate(40L, JsonCommand.from("{\"proceeds\": 507000000, \"reason\": \"Liquidity needed\"}")).getChanges();

        assertEquals(new BigDecimal("-397260.27"), changes.get("realisedResult"));
        assertEquals(DealStatus.TERMINATED, deal.getStatus());
        assertTrue(this.flows.get(40L).stream().allMatch(f -> f.getStatus() == CashflowStatus.CANCELLED));
        assertEquals(0, deal.getExposureAmount().signum());
    }

    @Test
    void settlerCannotTerminateAndTheDateMustBeBeforeMaturity() {
        business(VALUE.plusDays(45));
        placement(40L);

        when(this.actor.userId(any())).thenReturn(SETTLER);
        assertCode("deal.same.user", () -> this.service.terminate(40L, JsonCommand.from("{\"proceeds\": 1, \"reason\": \"x\"}")));
        when(this.actor.userId(any())).thenReturn(OPS);
        assertCode("deal.termination.date.invalid", () -> this.service.terminate(40L,
                JsonCommand.from("{\"proceeds\": 1, \"reason\": \"x\", \"transactionDate\": \"2026-10-15\"}")));
    }

    @Test
    void rolloverCapitalisingInterestStartsApprovedWhenPreApproved() {
        business(MATURITY);
        final TreasuryDeal old = placement(40L);
        old.setRolloverPreApproved(true);
        old.setApprovedBy(2L);
        when(this.dealWriteService.captureRolledDeal(any(), eq(OPS), any())).thenAnswer(call -> {
            final TreasuryDeal draft = call.getArgument(0);
            draft.setId(41L);
            draft.setStatus(DealStatus.CAPTURED);
            return draft;
        });

        this.service.rollover(40L, JsonCommand.from("{\"rate\": 11.5, \"maturityDate\": \"2027-04-14\", \"capitaliseInterest\": true}"));

        verify(this.dealWriteService).captureRolledDeal(argThat(d -> d.getAmount().compareTo(new BigDecimal("514794520.55")) == 0
                && d.getValueDate().equals(MATURITY) && d.getRolledFromDealId() == 40L), eq(OPS), any());
        verify(this.dealWriteService).approveRolledDeal(argThat(d -> d.getId() == 41L), eq(2L), anyString());
        assertEquals(41L, old.getRolledToDealId());
    }

    @Test
    void rolloverOfPrincipalOnlyNeedsApprovalWithoutPreApproval() {
        business(MATURITY.minusDays(2));
        final TreasuryDeal old = placement(40L);
        when(this.dealWriteService.captureRolledDeal(any(), eq(OPS), any())).thenAnswer(call -> {
            final TreasuryDeal draft = call.getArgument(0);
            draft.setId(41L);
            draft.setStatus(DealStatus.CAPTURED);
            return draft;
        });

        this.service.rollover(40L, JsonCommand.from("{\"rate\": 11.5, \"maturityDate\": \"2027-04-14\"}"));

        verify(this.dealWriteService).captureRolledDeal(argThat(d -> d.getAmount().compareTo(new BigDecimal("500000000.00")) == 0), eq(OPS),
                any());
        verify(this.dealWriteService, never()).approveRolledDeal(any(), any(), any());
        assertEquals(41L, old.getRolledToDealId());
    }

    @Test
    void rolloverOutsideItsWindowIsRefused() {
        business(MATURITY.minusDays(10));
        placement(40L);

        assertCode("deal.rollover.window",
                () -> this.service.rollover(40L, JsonCommand.from("{\"rate\": 11, \"maturityDate\": \"2027-04-14\"}")));
    }

    private TreasuryDeal placement(final Long id) {
        final DealPricer.Priced priced = DealPricer.price(new DealPricer.Terms(InstrumentKind.MONEY_MARKET_PLACEMENT,
                new BigDecimal("500000000"), new BigDecimal("12"), null, DayCount.ACT_365, 0, false, VALUE, MATURITY, null, 2));
        final TreasuryDeal deal = new TreasuryDeal();
        deal.setId(id);
        deal.setDealTypeId(5L);
        deal.setCounterpartyId(7L);
        deal.setSettlementAccountId(3L);
        deal.setOfficeId(1L);
        deal.setCurrencyCode("UGX");
        deal.setTradeDate(VALUE);
        deal.setValueDate(VALUE);
        deal.setMaturityDate(MATURITY);
        deal.setAmount(new BigDecimal("500000000"));
        deal.setRate(new BigDecimal("12"));
        deal.setDayCount(DayCount.ACT_365);
        deal.setFees(BigDecimal.ZERO);
        deal.setClassification(DealClassification.AMORTISED_COST);
        deal.setConsideration(priced.consideration());
        deal.setAccruedInterestPurchased(BigDecimal.ZERO);
        deal.setPremium(BigDecimal.ZERO);
        deal.setDiscount(BigDecimal.ZERO);
        deal.setExposureAmount(priced.exposureAmount());
        deal.setStatus(DealStatus.SETTLED);
        deal.setSettledBy(SETTLER);
        deal.setAccruedToDate(VALUE);
        lenient().when(this.dealRepository.findById(id)).thenReturn(Optional.of(deal));
        final List<TreasuryDealCashflow> rows = new ArrayList<>();
        for (final Flow flow : priced.flows()) {
            final TreasuryDealCashflow row = new TreasuryDealCashflow();
            row.setId(this.flowIds.incrementAndGet());
            row.setDealId(id);
            row.setSequenceNumber(flow.sequence());
            row.setFlowType(flow.type());
            row.setPeriodStart(flow.periodStart());
            row.setPeriodEnd(flow.periodEnd());
            row.setDueDate(flow.dueDate());
            row.setAmount(flow.amount());
            row.setPrincipalOutstanding(flow.outstanding());
            rows.add(row);
            lenient().when(this.cashflowRepository.findById(row.getId())).thenReturn(Optional.of(row));
        }
        this.flows.put(id, rows);
        return deal;
    }

    private static void business(final LocalDate date) {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, date)));
    }

    private static void assertCode(final String suffix, final org.junit.jupiter.api.function.Executable call) {
        assertEquals("error.msg.treasury." + suffix, assertThrows(TreasuryWorkflowException.class, call).getGlobalisationMessageCode());
    }
}
