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
package org.apache.fineract.treasury.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator.Terms;
import org.junit.jupiter.api.Test;

class DealRulesTest {

    private static final LocalDate TRADE = LocalDate.of(2026, 10, 14);
    private static final LocalDate VALUE = LocalDate.of(2026, 10, 16);

    @Test
    void lifecycleMovesOneStepAtATime() {
        assertEquals(Set.of(DealStatus.CAPTURED), matching(DealStatus::canApprove));
        assertEquals(Set.of(DealStatus.CAPTURED), matching(DealStatus::canUpdate));
        assertEquals(Set.of(DealStatus.CAPTURED), matching(DealStatus::canReject));
        assertEquals(Set.of(DealStatus.APPROVED), matching(DealStatus::canConfirm));
        assertEquals(Set.of(DealStatus.CONFIRMED), matching(DealStatus::canSettle));
        assertEquals(Set.of(DealStatus.CAPTURED, DealStatus.APPROVED, DealStatus.CONFIRMED), matching(DealStatus::canCancel));
        assertEquals(Set.of(DealStatus.APPROVED, DealStatus.CONFIRMED, DealStatus.SETTLED), matching(DealStatus::countsTowardsExposure));
    }

    @Test
    void kindsFixDirectionAndInterestMethod() {
        assertEquals(DealDirection.ASSET, InstrumentKind.TREASURY_BILL.direction());
        assertEquals(InterestMethod.DISCOUNT_YIELD, InstrumentKind.TREASURY_BILL.interestMethod());
        assertEquals(InterestMethod.COUPON, InstrumentKind.GOVERNMENT_BOND.interestMethod());
        assertEquals(DealDirection.LIABILITY, InstrumentKind.CREDIT_LINE_DRAWDOWN.direction());
        assertTrue(InstrumentKind.BORROWING.mayAmortise());
        assertFalse(InstrumentKind.FIXED_DEPOSIT_PLACED.mayAmortise());
    }

    @Test
    void limitWarningAndExcess() {
        final BigDecimal limit = new BigDecimal("1000");
        final BigDecimal warning = new BigDecimal("80");

        assertEquals(LimitCheck.Outcome.OK, LimitCheck.evaluate(limit, warning, new BigDecimal("700"), new BigDecimal("50")).outcome());
        final LimitCheck.Result atWarning = LimitCheck.evaluate(limit, warning, new BigDecimal("700"), new BigDecimal("100"));
        assertEquals(LimitCheck.Outcome.WARNING, atWarning.outcome());
        assertEquals(new BigDecimal("80.00"), atWarning.utilisation());
        assertEquals(new BigDecimal("200"), atWarning.available());
        assertEquals(LimitCheck.Outcome.WARNING,
                LimitCheck.evaluate(limit, warning, new BigDecimal("700"), new BigDecimal("300")).outcome(),
                "exactly at the limit is still within it");
        final LimitCheck.Result above = LimitCheck.evaluate(limit, warning, new BigDecimal("700"), new BigDecimal("301"));
        assertEquals(LimitCheck.Outcome.EXCEEDED, above.outcome());
        assertEquals(new BigDecimal("-1"), above.available());
    }

    @Test
    void noLimitMeansNoCheck() {
        final LimitCheck.Result result = LimitCheck.evaluate(null, new BigDecimal("80"), new BigDecimal("1000000"), new BigDecimal("1"));

        assertEquals(LimitCheck.Outcome.OK, result.outcome());
        assertNull(result.utilisation());
    }

    @Test
    void datesMustBeInOrder() {
        assertRejected(new Terms(InstrumentKind.MONEY_MARKET_PLACEMENT, VALUE, TRADE, VALUE.plusDays(30), null, BigDecimal.TEN,
                BigDecimal.ONE, null, 0, false), "valueDate");
        assertRejected(
                new Terms(InstrumentKind.MONEY_MARKET_PLACEMENT, TRADE, VALUE, VALUE, null, BigDecimal.TEN, BigDecimal.ONE, null, 0, false),
                "maturityDate");
    }

    @Test
    void billsTakeNoPriceOrFrequency() {
        assertRejected(new Terms(InstrumentKind.TREASURY_BILL, TRADE, VALUE, VALUE.plusDays(91), null, BigDecimal.TEN, BigDecimal.ONE,
                new BigDecimal("98"), 0, false), "cleanPrice");
        assertRejected(new Terms(InstrumentKind.TREASURY_BILL, TRADE, VALUE, VALUE.plusDays(91), null, BigDecimal.TEN, BigDecimal.ONE, null,
                2, false), "frequency");
    }

    @Test
    void bondsNeedAPriceAndACouponFrequency() {
        assertRejected(new Terms(InstrumentKind.GOVERNMENT_BOND, TRADE, VALUE, VALUE.plusYears(5), null, BigDecimal.TEN, BigDecimal.ONE,
                null, 2, false), "cleanPrice");
        assertRejected(new Terms(InstrumentKind.GOVERNMENT_BOND, TRADE, VALUE, VALUE.plusYears(5), null, BigDecimal.TEN, BigDecimal.ONE,
                new BigDecimal("99"), 0, false), "frequency");
        assertRejected(new Terms(InstrumentKind.GOVERNMENT_BOND, TRADE, VALUE, VALUE.plusYears(5), VALUE.plusDays(1), BigDecimal.TEN,
                BigDecimal.ONE, new BigDecimal("99"), 2, false), "issueDate");
    }

    @Test
    void onlyBorrowingsAmortiseAndThenByPeriods() {
        assertRejected(new Terms(InstrumentKind.MONEY_MARKET_PLACEMENT, TRADE, VALUE, VALUE.plusYears(1), null, BigDecimal.TEN,
                BigDecimal.ONE, null, 4, true), "amortising");
        assertRejected(
                new Terms(InstrumentKind.BORROWING, TRADE, VALUE, VALUE.plusYears(1), null, BigDecimal.TEN, BigDecimal.ONE, null, 0, true),
                "amortising");
        TreasuryDealDataValidator.validateTerms(
                new Terms(InstrumentKind.BORROWING, TRADE, VALUE, VALUE.plusYears(1), null, BigDecimal.TEN, BigDecimal.ONE, null, 4, true));
    }

    private static void assertRejected(final Terms terms, final String parameter) {
        final PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                () -> TreasuryDealDataValidator.validateTerms(terms));
        assertEquals(List.of(parameter), e.getErrors().stream().map(error -> error.getParameterName()).distinct().toList());
    }

    private static Set<DealStatus> matching(final java.util.function.Predicate<DealStatus> test) {
        return Arrays.stream(DealStatus.values()).filter(test).collect(Collectors.toCollection(() -> EnumSet.noneOf(DealStatus.class)));
    }
}
