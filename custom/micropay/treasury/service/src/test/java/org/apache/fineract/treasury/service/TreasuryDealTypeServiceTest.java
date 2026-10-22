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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.domain.TreasuryDealTypeRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator;
import org.apache.fineract.treasury.service.TreasuryReferenceDataLookup.GlAccountInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TreasuryDealTypeServiceTest {

    /** GL account id to classification: 1 asset, 2 liability, 3 equity, 4 income, 5 expense; 99 is a header account. */
    private static final Map<Long, Integer> GL = Map.ofEntries(Map.entry(11L, 1), Map.entry(12L, 1), Map.entry(13L, 4), Map.entry(14L, 1),
            Map.entry(15L, 1), Map.entry(21L, 2), Map.entry(22L, 2), Map.entry(23L, 5), Map.entry(31L, 3), Map.entry(41L, 4),
            Map.entry(51L, 5), Map.entry(99L, 1));

    private final TreasuryReferenceDataLookup lookup = mock(TreasuryReferenceDataLookup.class);
    private final TreasuryDealTypeService service = new TreasuryDealTypeService(new TreasuryDealDataValidator(new FromJsonHelper()),
            mock(TreasuryDealTypeRepository.class), this.lookup);

    @BeforeEach
    void setUp() {
        when(this.lookup.findGlAccount(anyLong())).thenAnswer(call -> {
            final Long id = call.getArgument(0);
            return GL.containsKey(id) ? Optional.of(new GlAccountInfo(id, GL.get(id), id == 99L ? 2 : 1, false)) : Optional.empty();
        });
    }

    @Test
    void assetTypeNeedsPrincipalReceivableAndIncome() {
        final TreasuryDealType type = type(InstrumentKind.TREASURY_BILL);
        type.setPrincipalGlAccountId(11L);
        type.setInterestReceivableGlAccountId(12L);

        assertCode("deal.type.gl.account.required", () -> this.service.validateAccounts(type));
        type.setInterestIncomeGlAccountId(13L);
        assertDoesNotThrow(() -> this.service.validateAccounts(type));
    }

    @Test
    void bondTypeAlsoNeedsPremiumAndDiscount() {
        final TreasuryDealType type = type(InstrumentKind.GOVERNMENT_BOND);
        type.setPrincipalGlAccountId(11L);
        type.setInterestReceivableGlAccountId(12L);
        type.setInterestIncomeGlAccountId(13L);

        assertCode("deal.type.gl.account.required", () -> this.service.validateAccounts(type));
        type.setPremiumGlAccountId(14L);
        type.setDiscountGlAccountId(15L);
        assertDoesNotThrow(() -> this.service.validateAccounts(type));
    }

    @Test
    void liabilityTypeNeedsLiabilityPrincipal() {
        final TreasuryDealType type = type(InstrumentKind.BORROWING);
        type.setPrincipalGlAccountId(11L);
        type.setInterestPayableGlAccountId(22L);
        type.setInterestExpenseGlAccountId(23L);

        assertCode("deal.type.gl.account.wrong.type", () -> this.service.validateAccounts(type));
        type.setPrincipalGlAccountId(21L);
        assertDoesNotThrow(() -> this.service.validateAccounts(type));
    }

    @Test
    void headerAccountIsRefused() {
        final TreasuryDealType type = type(InstrumentKind.MONEY_MARKET_PLACEMENT);
        type.setPrincipalGlAccountId(99L);
        type.setInterestReceivableGlAccountId(12L);
        type.setInterestIncomeGlAccountId(13L);

        assertCode("deal.type.gl.account.wrong.type", () -> this.service.validateAccounts(type));
    }

    @Test
    void fairValueReserveIsEquityAndTheAdjustmentAnAsset() {
        final TreasuryDealType type = bond();
        type.setFairValueReserveGlAccountId(13L);

        assertCode("deal.type.gl.account.wrong.type", () -> this.service.validateAccounts(type));
        type.setFairValueReserveGlAccountId(31L);
        type.setFairValueAdjustmentGlAccountId(41L);
        assertCode("deal.type.gl.account.wrong.type", () -> this.service.validateAccounts(type));
        type.setFairValueAdjustmentGlAccountId(12L);
        type.setFairValueGainGlAccountId(41L);
        type.setFairValueLossGlAccountId(51L);
        type.setImpairmentExpenseGlAccountId(51L);
        type.setEclAllowanceGlAccountId(14L);
        assertDoesNotThrow(() -> this.service.validateAccounts(type));
    }

    @Test
    void billsAndBondsAtFairValueNeedTheirValuationAccounts() {
        final TreasuryDealType type = bond();
        assertDoesNotThrow(() -> TreasuryDealTypeService.requireValuationAccounts(type, DealClassification.AMORTISED_COST));
        assertCode("deal.type.valuation.gl.account.missing",
                () -> TreasuryDealTypeService.requireValuationAccounts(type, DealClassification.FVOCI));

        type.setFairValueAdjustmentGlAccountId(12L);
        type.setFairValueReserveGlAccountId(31L);
        type.setRealisedGainGlAccountId(41L);
        type.setRealisedLossGlAccountId(51L);
        assertDoesNotThrow(() -> TreasuryDealTypeService.requireValuationAccounts(type, DealClassification.FVOCI));
        assertCode("deal.type.valuation.gl.account.missing",
                () -> TreasuryDealTypeService.requireValuationAccounts(type, DealClassification.FVTPL));
        type.setFairValueGainGlAccountId(41L);
        type.setFairValueLossGlAccountId(51L);
        assertDoesNotThrow(() -> TreasuryDealTypeService.requireValuationAccounts(type, DealClassification.FVTPL));

        // placements are never revalued, whatever their classification
        assertDoesNotThrow(() -> TreasuryDealTypeService.requireValuationAccounts(type(InstrumentKind.MONEY_MARKET_PLACEMENT),
                DealClassification.FVTPL));
    }

    private static TreasuryDealType bond() {
        final TreasuryDealType type = type(InstrumentKind.GOVERNMENT_BOND);
        type.setPrincipalGlAccountId(11L);
        type.setInterestReceivableGlAccountId(12L);
        type.setInterestIncomeGlAccountId(13L);
        type.setPremiumGlAccountId(14L);
        type.setDiscountGlAccountId(15L);
        return type;
    }

    private static TreasuryDealType type(final InstrumentKind kind) {
        final TreasuryDealType type = new TreasuryDealType();
        type.setCode("T");
        type.setInstrumentKind(kind);
        type.setDirection(kind.direction());
        type.setInterestMethod(kind.interestMethod());
        return type;
    }

    private static void assertCode(final String suffix, final org.junit.jupiter.api.function.Executable call) {
        assertEquals("error.msg.treasury." + suffix, assertThrows(TreasuryWorkflowException.class, call).getGlobalisationMessageCode());
    }
}
