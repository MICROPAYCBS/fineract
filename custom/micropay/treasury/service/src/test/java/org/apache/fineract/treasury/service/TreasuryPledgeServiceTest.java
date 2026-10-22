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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.deal.PledgePurpose;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealEventRepository;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.domain.TreasuryPledge;
import org.apache.fineract.treasury.domain.TreasuryPledgeRepository;
import org.apache.fineract.treasury.domain.TreasuryPledgeStatus;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A 500,000,000 placement settled on 2026-10-16, pledged in parts. */
class TreasuryPledgeServiceTest {

    private static final LocalDate VALUE = LocalDate.of(2026, 10, 16);
    private static final LocalDate MATURITY = LocalDate.of(2027, 1, 14);

    private final TreasuryActor actor = mock(TreasuryActor.class);
    private final TreasuryDealRepository dealRepository = mock(TreasuryDealRepository.class);
    private final TreasuryDealTypeService dealTypeService = mock(TreasuryDealTypeService.class);
    private final TreasuryPledgeRepository pledgeRepository = mock(TreasuryPledgeRepository.class);
    private final TreasuryPledgeService service = new TreasuryPledgeService(new TreasuryHoldingDataValidator(new FromJsonHelper()),
            this.actor, this.dealRepository, this.dealTypeService, mock(TreasuryCounterpartyService.class), this.pledgeRepository,
            mock(TreasuryDealEventRepository.class));
    private final List<TreasuryPledge> pledges = new ArrayList<>();
    private TreasuryDeal deal;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", mock(FineractPlatformTenantConnection.class)));
        business(LocalDate.of(2026, 11, 2));
        lenient().when(this.actor.userId(any())).thenReturn(5L);
        this.deal = deal(40L, InstrumentKind.MONEY_MARKET_PLACEMENT, 6L, DealStatus.SETTLED);
        deal(50L, InstrumentKind.BORROWING, 7L, DealStatus.SETTLED);
        deal(51L, InstrumentKind.TREASURY_BILL, 8L, DealStatus.SETTLED);
        lenient().when(this.pledgeRepository.saveAndFlush(any())).thenAnswer(call -> {
            final TreasuryPledge pledge = call.getArgument(0);
            if (pledge.getId() == null) {
                pledge.setId((long) this.pledges.size() + 1);
                this.pledges.add(pledge);
            }
            return pledge;
        });
        lenient().when(this.pledgeRepository.findById(anyLong()))
                .thenAnswer(call -> this.pledges.stream().filter(p -> p.getId().equals(call.getArgument(0))).findFirst());
        lenient().when(this.pledgeRepository.findByDealIdOrderByIdAsc(40L)).thenAnswer(call -> List.copyOf(this.pledges));
        lenient().when(this.pledgeRepository.findByDealIdAndStatus(40L, TreasuryPledgeStatus.ACTIVE))
                .thenAnswer(call -> this.pledges.stream().filter(p -> p.getStatus() == TreasuryPledgeStatus.ACTIVE).toList());
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void pledgesMayNotExceedTheOutstandingNominal() {
        final Map<String, Object> changes = this.service.pledge(40L, JsonCommand
                .from("{\"pledgeeCounterpartyId\": 9, \"purpose\": \"borrowing_collateral\", \"amount\": 300000000, \"linkedDealId\": 50, "
                        + "\"startDate\": \"2026-10-20\", \"expectedEndDate\": \"2026-12-31\", \"reason\": \"Collateral for DFI line\"}"))
                .getChanges();
        assertEquals(new BigDecimal("200000000"), changes.get("unencumbered"));
        assertEquals(PledgePurpose.BORROWING_COLLATERAL, this.pledges.getFirst().getPurpose());

        assertCode("deal.pledge.exceeds.unencumbered", () -> this.service.pledge(40L,
                JsonCommand.from("{\"pledgeeCounterpartyId\": 9, \"purpose\": \"REPO\", \"amount\": 250000000}")));
        assertDoesNotThrow(() -> this.service.pledge(40L,
                JsonCommand.from("{\"pledgeeCounterpartyId\": 9, \"purpose\": \"REPO\", \"amount\": 200000000}")));
        assertEquals(0, this.service.unencumberedAmount(this.deal, LocalDate.of(2026, 11, 2)).signum());
        // the first pledge started on 20 October, the second on the business date
        assertEquals(new BigDecimal("500000000"), this.service.unencumberedAmount(this.deal, LocalDate.of(2026, 10, 19)));
        assertEquals(new BigDecimal("200000000"), this.service.unencumberedAmount(this.deal, LocalDate.of(2026, 10, 25)));
    }

    @Test
    void onlySettledAssetDealsArePledgedAndOnlyBorrowingsLinked() {
        assertCode("deal.pledge.not.allowed",
                () -> this.service.pledge(50L, JsonCommand.from("{\"pledgeeCounterpartyId\": 9, \"purpose\": \"REPO\", \"amount\": 1}")));
        assertCode("deal.pledge.linked.deal.invalid", () -> this.service.pledge(40L,
                JsonCommand.from("{\"pledgeeCounterpartyId\": 9, \"purpose\": \"REPO\", \"amount\": 1, \"linkedDealId\": 51}")));
        assertCode("deal.pledge.start.date.invalid", () -> this.service.pledge(40L,
                JsonCommand.from("{\"pledgeeCounterpartyId\": 9, \"purpose\": \"REPO\", \"amount\": 1, \"startDate\": \"2026-10-15\"}")));
        assertCode("deal.pledge.end.date.invalid", () -> this.service.pledge(40L, JsonCommand
                .from("{\"pledgeeCounterpartyId\": 9, \"purpose\": \"REPO\", \"amount\": 1, \"expectedEndDate\": \"2027-02-01\"}")));
    }

    @Test
    void releaseFreesTheNominalAndUnblocksTermination() {
        this.service.pledge(40L,
                JsonCommand.from("{\"pledgeeCounterpartyId\": 9, \"purpose\": \"CENTRAL_BANK_FACILITY\", \"amount\": 400000000}"));
        assertCode("deal.pledged", () -> this.service.requireUnpledged(this.deal, "be terminated"));
        assertCode("deal.pledged", () -> this.service.requirePledgesCovered(this.deal, new BigDecimal("300000000")));
        assertDoesNotThrow(() -> this.service.requirePledgesCovered(this.deal, BigDecimal.ZERO));

        assertCode("deal.pledge.release.date.invalid", () -> this.service.releasePledge(40L,
                JsonCommand.from("{\"pledgeId\": 1, \"reason\": \"Facility repaid\", \"releaseDate\": \"2026-11-01\"}")));
        final Map<String, Object> changes = this.service
                .releasePledge(40L, JsonCommand.from("{\"pledgeId\": 1, \"reason\": \"Facility repaid\"}")).getChanges();
        assertEquals("RELEASED", changes.get("status"));
        assertEquals(new BigDecimal("500000000"), changes.get("unencumbered"));
        assertDoesNotThrow(() -> this.service.requireUnpledged(this.deal, "be terminated"));
        assertCode("deal.pledge.not.active",
                () -> this.service.releasePledge(40L, JsonCommand.from("{\"pledgeId\": 1, \"reason\": \"Again\"}")));
        assertCode("deal.pledge.not.found",
                () -> this.service.releasePledge(40L, JsonCommand.from("{\"pledgeId\": 7, \"reason\": \"Unknown\"}")));
    }

    @Test
    void maturityReleasesEveryPledge() {
        this.service.pledge(40L, JsonCommand.from("{\"pledgeeCounterpartyId\": 9, \"purpose\": \"REPO\", \"amount\": 100000000}"));
        this.service.pledge(40L, JsonCommand.from("{\"pledgeeCounterpartyId\": 9, \"purpose\": \"OTHER\", \"amount\": 100000000}"));

        assertEquals(2, this.service.releaseAll(this.deal, MATURITY, 1L, "Released at maturity"));
        assertEquals(List.of(TreasuryPledgeStatus.RELEASED, TreasuryPledgeStatus.RELEASED),
                this.pledges.stream().map(TreasuryPledge::getStatus).toList());
        assertEquals(MATURITY, this.pledges.getFirst().getReleasedOnDate());
        assertEquals(new BigDecimal("200000000"), this.service.encumberedAmount(40L, MATURITY.minusDays(1)));
        assertEquals(0, this.service.encumberedAmount(40L, MATURITY).signum());
        this.deal.setStatus(DealStatus.MATURED);
        assertEquals(0, this.service.unencumberedAmount(this.deal, MATURITY.minusDays(1)).signum());
    }

    private TreasuryDeal deal(final Long id, final InstrumentKind kind, final Long typeId, final DealStatus status) {
        final TreasuryDeal deal = new TreasuryDeal();
        deal.setId(id);
        deal.setDealTypeId(typeId);
        deal.setOfficeId(1L);
        deal.setValueDate(VALUE);
        deal.setMaturityDate(MATURITY);
        deal.setAmount(new BigDecimal("500000000"));
        deal.setStatus(status);
        final TreasuryDealType type = new TreasuryDealType();
        type.setId(typeId);
        type.setInstrumentKind(kind);
        lenient().when(this.dealRepository.findById(id)).thenReturn(Optional.of(deal));
        lenient().when(this.dealTypeService.find(typeId)).thenReturn(type);
        return deal;
    }

    private static void business(final LocalDate date) {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, date)));
    }

    private static void assertCode(final String suffix, final org.junit.jupiter.api.function.Executable call) {
        assertEquals("error.msg.treasury." + suffix, assertThrows(TreasuryWorkflowException.class, call).getGlobalisationMessageCode());
    }
}
