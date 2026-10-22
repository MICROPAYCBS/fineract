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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.treasury.deal.DayCount;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.deal.LimitCheck;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.domain.TreasuryCounterparty;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyStatus;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealCashflowRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEventRepository;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.domain.TreasuryEventType;
import org.apache.fineract.treasury.domain.TreasurySetting;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TreasuryDealWriteServiceTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 16);
    private static final long CAPTURER = 1L;
    private static final long APPROVER = 2L;
    private static final long CONFIRMER = 3L;
    private static final long SETTLER = 4L;

    private final TreasuryActor actor = mock(TreasuryActor.class);
    private final TreasuryDealRepository dealRepository = mock(TreasuryDealRepository.class);
    private final TreasuryAccountRepository accountRepository = mock(TreasuryAccountRepository.class);
    private final TreasuryDealTypeService dealTypeService = mock(TreasuryDealTypeService.class);
    private final TreasuryCounterpartyService counterpartyService = mock(TreasuryCounterpartyService.class);
    private final TreasurySettingService settingService = mock(TreasurySettingService.class);
    private final TreasuryBalanceReader balanceReader = mock(TreasuryBalanceReader.class);
    private final TreasuryJournalPoster journalPoster = mock(TreasuryJournalPoster.class);
    private final TreasuryReferenceDataLookup lookup = mock(TreasuryReferenceDataLookup.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final TreasuryDealWriteService service = new TreasuryDealWriteService(this.actor,
            new TreasuryDealDataValidator(new FromJsonHelper()), this.dealRepository, mock(TreasuryDealCashflowRepository.class),
            mock(TreasuryDealEventRepository.class), this.accountRepository, this.dealTypeService, this.counterpartyService,
            this.settingService, this.balanceReader, this.journalPoster, this.lookup, this.appUserRepository,
            mock(TreasuryLimitEngine.class));
    private final TreasurySetting setting = new TreasurySetting();
    private final TreasuryCounterparty counterparty = new TreasuryCounterparty();

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", mock(FineractPlatformTenantConnection.class)));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE)));
        final TreasuryDealType type = new TreasuryDealType();
        type.setId(5L);
        type.setCode("MM-PLACEMENT");
        type.setInstrumentKind(InstrumentKind.MONEY_MARKET_PLACEMENT);
        type.setDefaultDayCount(DayCount.ACT_365);
        type.setPrincipalGlAccountId(20L);
        type.setFeeExpenseGlAccountId(24L);
        type.setActive(true);
        lenient().when(this.dealTypeService.find(5L)).thenReturn(type);
        this.counterparty.setId(7L);
        this.counterparty.setShortCode("STANBIC");
        this.counterparty.setExposureLimit(new BigDecimal("1000000000"));
        this.counterparty.setLimitCurrencyCode("UGX");
        this.counterparty.setWarningPercent(new BigDecimal("80"));
        this.counterparty.setStatus(TreasuryCounterpartyStatus.ACTIVE);
        lenient().when(this.counterpartyService.active(7L)).thenReturn(this.counterparty);
        lenient().when(this.counterpartyService.find(7L)).thenReturn(this.counterparty);
        final TreasuryAccount account = new TreasuryAccount();
        account.setId(3L);
        account.setCode("VAULT");
        account.setGlAccountId(10L);
        account.setOfficeId(1L);
        account.setCurrencyCode("UGX");
        account.setStatus(TreasuryAccountStatus.ACTIVE);
        lenient().when(this.accountRepository.findById(3L)).thenReturn(Optional.of(account));
        lenient().when(this.settingService.settingsFor("UGX")).thenReturn(this.setting);
        lenient().when(this.lookup.decimalPlaces("UGX")).thenReturn(0);
        lenient().when(this.dealRepository.saveAndFlush(any())).thenAnswer(call -> {
            final TreasuryDeal deal = call.getArgument(0);
            if (deal.getId() == null) {
                deal.setId(40L);
            }
            return deal;
        });
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void captureWithinTheLimitPricesTheDeal() {
        limit(LimitCheck.Outcome.OK, "50.00");
        actAs(CAPTURER);

        final Map<String, Object> changes = this.service.capture(captureCommand()).getChanges();

        assertEquals("CAPTURED", changes.get("status"));
        assertEquals(new BigDecimal("500000000"), changes.get("consideration"));
    }

    @Test
    void captureAboveTheLimitIsRefusedWithoutReferral() {
        limit(LimitCheck.Outcome.EXCEEDED, "120.00");
        actAs(CAPTURER);

        assertCode("deal.limit.exceeded", () -> this.service.capture(captureCommand()));
        verify(this.dealRepository, never()).saveAndFlush(any());
    }

    @Test
    void captureAboveTheLimitIsFlaggedWithReferral() {
        this.setting.setAllowLimitReferral(true);
        limit(LimitCheck.Outcome.EXCEEDED, "120.00");
        actAs(CAPTURER);

        final Map<String, Object> changes = this.service.capture(captureCommand()).getChanges();

        assertEquals(Boolean.TRUE, changes.get("limitExceeded"));
        assertTrue(String.valueOf(changes.get("warning")).contains("over-limit approval"));
    }

    @Test
    void warningLevelIsReported() {
        limit(LimitCheck.Outcome.WARNING, "85.00");
        actAs(CAPTURER);

        assertTrue(String.valueOf(this.service.capture(captureCommand()).getChanges().get("warning")).contains("85.00%"));
    }

    @Test
    void capturerCannotApprove() {
        deal(DealStatus.CAPTURED);
        actAs(CAPTURER);

        assertCode("deal.same.user", () -> this.service.approve(40L, JsonCommand.from("{}")));
    }

    @Test
    void lastEditorCannotApprove() {
        deal(DealStatus.CAPTURED).setLastEditedBy(APPROVER);
        actAs(APPROVER);

        assertCode("deal.same.user", () -> this.service.approve(40L, JsonCommand.from("{}")));
    }

    @Test
    void overLimitApprovalNeedsTheExtraPermission() {
        this.setting.setAllowLimitReferral(true);
        deal(DealStatus.CAPTURED);
        limit(LimitCheck.Outcome.EXCEEDED, "120.00");
        actAs(APPROVER);
        final AppUser approver = mock(AppUser.class);
        when(this.appUserRepository.findById(APPROVER)).thenReturn(Optional.of(approver));

        assertCode("deal.overlimit.approval.not.permitted", () -> this.service.approve(40L, JsonCommand.from("{}")));

        when(approver.hasAnyPermission("ALL_FUNCTIONS", "APPROVE_TREASURYDEAL_OVERLIMIT")).thenReturn(true);
        assertEquals("APPROVED", this.service.approve(40L, JsonCommand.from("{}")).getChanges().get("status"));
    }

    @Test
    void approverCannotConfirmAndConfirmationNeedsAReference() {
        deal(DealStatus.APPROVED).setApprovedBy(APPROVER);
        actAs(APPROVER);

        assertCode("deal.confirmation.reference.required", () -> this.service.confirm(40L, JsonCommand.from("{}")));
        assertCode("deal.same.user", () -> this.service.confirm(40L, JsonCommand.from("{\"confirmationReference\": \"SWIFT-300\"}")));
    }

    @Test
    void confirmerCannotSettle() {
        deal(DealStatus.CONFIRMED).setConfirmedBy(CONFIRMER);
        actAs(CONFIRMER);

        assertCode("deal.same.user", () -> this.service.settle(40L, JsonCommand.from("{}")));
    }

    @Test
    void settlementWaitsForTheValueDate() {
        deal(DealStatus.CONFIRMED).setValueDate(BUSINESS_DATE.plusDays(1));
        actAs(SETTLER);

        assertCode("deal.value.date.in.future", () -> this.service.settle(40L, JsonCommand.from("{}")));
    }

    @Test
    void settlementNeedsTheCash() {
        deal(DealStatus.CONFIRMED);
        when(this.balanceReader.glBalances(List.of(3L), null)).thenReturn(Map.of(3L, new BigDecimal("400000000")));
        actAs(SETTLER);

        assertCode("deal.insufficient.balance", () -> this.service.settle(40L, JsonCommand.from("{}")));
        verify(this.journalPoster, never()).post(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void settlementPostsOnTheValueDate() {
        final TreasuryDeal deal = deal(DealStatus.CONFIRMED);
        when(this.balanceReader.glBalances(List.of(3L), null)).thenReturn(Map.of(3L, new BigDecimal("600000000")));
        when(this.journalPoster.post(any(), any(), any(), any(), any(), any(), any())).thenReturn("TR-DS-40");
        actAs(SETTLER);

        this.service.settle(40L, JsonCommand.from("{}"));

        verify(this.journalPoster).post(eq(TreasuryEventType.DEAL_SETTLEMENT), eq(40L), eq("UGX"), eq(BUSINESS_DATE), anyString(), any(),
                any());
        assertEquals(DealStatus.SETTLED, deal.getStatus());
        assertEquals("TR-DS-40", deal.getSettlementTransactionId());
        assertEquals(SETTLER, deal.getSettledBy());
    }

    @Test
    void settledDealCannotBeCancelled() {
        deal(DealStatus.SETTLED);

        assertCode("deal.cannot.cancel", () -> this.service.cancel(40L, JsonCommand.from("{\"reason\": \"x\"}")));
    }

    @Test
    void rejectionNeedsAReason() {
        deal(DealStatus.CAPTURED);
        actAs(APPROVER);

        assertCode("deal.reason.required", () -> this.service.reject(40L, JsonCommand.from("{}")));
        assertEquals("REJECTED",
                this.service.reject(40L, JsonCommand.from("{\"reason\": \"Rate off market\"}")).getChanges().get("status"));
    }

    private TreasuryDeal deal(final DealStatus status) {
        final TreasuryDeal deal = new TreasuryDeal();
        deal.setId(40L);
        deal.setDealTypeId(5L);
        deal.setCounterpartyId(7L);
        deal.setSettlementAccountId(3L);
        deal.setOfficeId(1L);
        deal.setCurrencyCode("UGX");
        deal.setTradeDate(BUSINESS_DATE);
        deal.setValueDate(BUSINESS_DATE);
        deal.setMaturityDate(BUSINESS_DATE.plusDays(90));
        deal.setAmount(new BigDecimal("500000000"));
        deal.setRate(new BigDecimal("12"));
        deal.setDayCount(DayCount.ACT_365);
        deal.setFees(BigDecimal.ZERO);
        deal.setClassification(DealClassification.AMORTISED_COST);
        deal.setConsideration(new BigDecimal("500000000"));
        deal.setExposureAmount(new BigDecimal("500000000"));
        deal.setStatus(status);
        deal.setCapturedBy(CAPTURER);
        when(this.dealRepository.findById(40L)).thenReturn(Optional.of(deal));
        return deal;
    }

    private void limit(final LimitCheck.Outcome outcome, final String utilisation) {
        when(this.counterpartyService.check(eq(this.counterparty), any()))
                .thenReturn(new LimitCheck.Result(outcome, new BigDecimal("1200000000"), new BigDecimal(utilisation), BigDecimal.ZERO));
    }

    private void actAs(final long userId) {
        when(this.actor.userId(any())).thenReturn(userId);
    }

    private static JsonCommand captureCommand() {
        return JsonCommand.from("{\"dealTypeId\": 5, \"counterpartyId\": 7, \"settlementAccountId\": 3, \"tradeDate\": \"2026-10-16\","
                + " \"valueDate\": \"2026-10-16\", \"maturityDate\": \"2027-01-14\", \"amount\": 500000000, \"rate\": 12}");
    }

    private static void assertCode(final String suffix, final org.junit.jupiter.api.function.Executable call) {
        assertEquals("error.msg.treasury." + suffix, assertThrows(TreasuryWorkflowException.class, call).getGlobalisationMessageCode());
    }
}
