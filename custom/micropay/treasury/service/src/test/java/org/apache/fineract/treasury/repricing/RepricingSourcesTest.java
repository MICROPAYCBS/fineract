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
package org.apache.fineract.treasury.repricing;

import static org.apache.fineract.treasury.repricing.RepricingFixtures.AS_OF;
import static org.apache.fineract.treasury.repricing.RepricingFixtures.context;
import static org.apache.fineract.treasury.repricing.RepricingFixtures.d;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.treasury.liquidity.AssumptionType;
import org.apache.fineract.treasury.liquidity.Assumptions;
import org.apache.fineract.treasury.liquidity.LadderContext;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.junit.jupiter.api.Test;

/** Loans fixed, floating and non-performing; the administered distribution of savings and bank balances. */
class RepricingSourcesTest {

    @Test
    void fixedLoansRepriceAsPrincipalFallsDueFloatingAtTheirReset() {
        final List<RepricingPosition> fixed = LoanRepricingSource.positions(context(List.of()),
                List.of(installment(1L, false, false, null, AS_OF.plusDays(20), "300"),
                        installment(1L, false, false, null, AS_OF.plusDays(50), "300")));
        assertEquals(2, fixed.size());
        assertEquals(RateType.FIXED, fixed.get(0).rateType());
        assertEquals(AS_OF.plusDays(50), fixed.get(1).repricingDate());
        final List<RepricingPosition> floating = LoanRepricingSource.positions(context(List.of()),
                List.of(installment(2L, false, true, AS_OF.plusDays(45), AS_OF.plusDays(20), "300"),
                        installment(2L, false, true, AS_OF.plusDays(45), AS_OF.plusDays(200), "300"),
                        installment(3L, false, true, null, AS_OF.plusDays(200), "100")));
        assertTrue(
                floating.stream().filter(p -> p.productId() == 2L)
                        .allMatch(p -> p.rateType() == RateType.FLOATING && AS_OF.plusDays(45).equals(p.repricingDate())),
                "every installment at the next reset");
        assertNull(floating.get(2).repricingDate(), "no reset known: the first bucket");
    }

    @Test
    void nonPerformingLoansAreNotRateSensitive() {
        final List<RepricingPosition> npa = LoanRepricingSource.positions(context(List.of()),
                List.of(installment(1L, true, false, null, AS_OF.minusDays(100), "300")));
        assertEquals(RateType.NON_SENSITIVE, npa.getFirst().rateType());
        final LadderContext banded = context(List
                .of(new Assumptions.Row(AssumptionType.REPRICING_NON_SENSITIVE, LadderSourceCode.LOANS, null, null, 31, null, d("50"))));
        final List<RepricingPosition> half = LoanRepricingSource.positions(banded,
                List.of(installment(1L, false, false, null, AS_OF.minusDays(40), "100"),
                        installment(1L, false, false, null, AS_OF.plusDays(20), "200")));
        assertEquals(0, d("150").compareTo(half.stream().filter(p -> p.rateType() == RateType.NON_SENSITIVE).map(RepricingPosition::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)), "half of every installment of a loan 40 days in arrears");
        assertEquals(0, d("300").compareTo(LoanRepricingSource
                .positions(banded,
                        List.of(installment(1L, false, false, null, AS_OF.minusDays(10), "100"),
                                installment(1L, false, false, null, AS_OF.plusDays(20), "200")))
                .stream().filter(p -> p.rateType() == RateType.FIXED).map(RepricingPosition::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)), "10 days in arrears: below the band");
    }

    @Test
    void savingsAreSpreadByTheAssumptionRestInTheFirstBucket() {
        final LadderContext ctx = context(List.of(dist(LadderSourceCode.SAVINGS, null, "ON", "30"),
                dist(LadderSourceCode.SAVINGS, null, "M3_6", "50"), dist(LadderSourceCode.SAVINGS, 9L, "Y1_PLUS", "100")));
        final List<RepricingPosition> general = RepricingDistribution.spread(ctx, LadderSourceCode.SAVINGS, "DEMAND_SAVINGS", 1L, "S",
                RepricingSide.LIABILITY, RateType.ADMINISTERED, RateType.ADMINISTERED, d("1000"));
        assertEquals(List.of("ON", "M3_6", "ON"), general.stream().map(RepricingPosition::bucketCode).toList());
        assertEquals(List.of(d("300.00"), d("500.00"), d("200.00")), general.stream().map(RepricingPosition::amount).toList());
        final List<RepricingPosition> product = RepricingDistribution.spread(ctx, LadderSourceCode.SAVINGS, "DEMAND_SAVINGS", 9L, "P",
                RepricingSide.LIABILITY, RateType.ADMINISTERED, RateType.ADMINISTERED, d("1000"));
        assertEquals(1, product.size(), "the product's own rows replace the general ones");
        assertEquals("Y1_PLUS", product.getFirst().bucketCode());
        final List<RepricingPosition> none = RepricingDistribution.spread(context(List.of()), LadderSourceCode.SAVINGS, "DEMAND_SAVINGS",
                1L, "S", RepricingSide.LIABILITY, RateType.ADMINISTERED, RateType.ADMINISTERED, d("1000"));
        assertEquals("ON", none.getFirst().bucketCode(), "without an assumption: the first bucket");
    }

    @Test
    void bankBalancesAreNonSensitiveUnlessDistributed() {
        final List<RepricingPosition> none = RepricingDistribution.spread(context(List.of()), LadderSourceCode.OPENING, "BANK", null, null,
                RepricingSide.ASSET, RateType.ADMINISTERED, RateType.NON_SENSITIVE, d("1000"));
        assertEquals(RateType.NON_SENSITIVE, none.getFirst().rateType());
        assertNull(none.getFirst().bucketCode());
        final LadderContext ctx = context(
                List.of(dist(LadderSourceCode.OPENING, null, "D9_1M", "40"), dist(LadderSourceCode.OPENING, null, "X", "10")));
        final List<RepricingPosition> some = RepricingDistribution.spread(ctx, LadderSourceCode.OPENING, "BANK", null, null,
                RepricingSide.ASSET, RateType.ADMINISTERED, RateType.NON_SENSITIVE, d("1000"));
        assertEquals(2, some.size());
        assertEquals(0, d("600").compareTo(some.get(1).amount()));
        assertTrue(ctx.warnings().getFirst().contains("X"), "an unknown bucket is warned about and left out");
    }

    private static LoanRepricingSource.Installment installment(final long loan, final boolean npa, final boolean floating,
            final java.time.LocalDate reset, final java.time.LocalDate due, final String principal) {
        return new LoanRepricingSource.Installment(loan, loan, "Loan " + loan, npa, floating, reset, due, d(principal), d(principal));
    }

    private static Assumptions.Row dist(final LadderSourceCode source, final Long product, final String bucket, final String rate) {
        return new Assumptions.Row(AssumptionType.REPRICING_DIST, source, product, bucket, null, null, d(rate));
    }

    @Test
    void loanPositionsAreSummedByRateTypeAndDate() {
        final LocalDate due = LocalDate.of(2026, 11, 30);
        final Map<RepricingPosition, BigDecimal> totals = new LinkedHashMap<>();
        for (final String amount : List.of("100", "250.25")) {
            LoanRepricingSource.add(totals, List.of(new RepricingPosition(LadderSourceCode.LOANS, LoanRepricingSource.PRINCIPAL, 9L,
                    "Group loan", RepricingSide.ASSET, RateType.FIXED, due, null, new BigDecimal(amount))));
        }
        LoanRepricingSource.add(totals, List.of(new RepricingPosition(LadderSourceCode.LOANS, LoanRepricingSource.PRINCIPAL, 9L,
                "Group loan", RepricingSide.ASSET, RateType.FLOATING, due, null, new BigDecimal("40"))));
        assertEquals(List.of(new BigDecimal("350.25"), new BigDecimal("40")), List.copyOf(totals.values()));
    }
}
