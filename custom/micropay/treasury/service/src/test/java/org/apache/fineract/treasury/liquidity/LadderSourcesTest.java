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
package org.apache.fineract.treasury.liquidity;

import static org.apache.fineract.treasury.liquidity.LadderFixtures.AS_OF;
import static org.apache.fineract.treasury.liquidity.LadderFixtures.behavioural;
import static org.apache.fineract.treasury.liquidity.LadderFixtures.row;
import static org.apache.fineract.treasury.liquidity.LadderFixtures.total;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.domain.TreasuryLadderItem;
import org.junit.jupiter.api.Test;

/** The behavioural adjustment of each source, on worked examples with the placeholder buckets. */
class LadderSourcesTest {

    @Test
    void savingsCoreShareStaysAndTheVolatileRestRunsOffByBucket() {
        // 1,000,000 with 60% core: 400,000 volatile; 5, 5, 10, 10, 10, 10% of it run off, the rest stays to the last
        // bucket
        final LadderContext context = LadderFixtures
                .context(new Assumptions(List.of(row(AssumptionType.CORE_SHARE, LadderSourceCode.SAVINGS, null, null, null, null, "60"),
                        row(AssumptionType.RUN_OFF, LadderSourceCode.SAVINGS, null, "ON", null, null, "5"),
                        row(AssumptionType.RUN_OFF, LadderSourceCode.SAVINGS, null, "D2_8", null, null, "5"),
                        row(AssumptionType.RUN_OFF, LadderSourceCode.SAVINGS, null, "D9_1M", null, null, "10"),
                        row(AssumptionType.RUN_OFF, LadderSourceCode.SAVINGS, null, "M1_3", null, null, "10"),
                        row(AssumptionType.RUN_OFF, LadderSourceCode.SAVINGS, null, "M3_6", null, null, "10"),
                        row(AssumptionType.RUN_OFF, LadderSourceCode.SAVINGS, null, "M6_12", null, null, "10"))));
        final LadderBuilder.Ladder ladder = LadderBuilder.build(context, SavingsLadderSource.flows(context,
                List.of(new SavingsLadderSource.ProductBalance(1L, "Voluntary savings", new BigDecimal("1000000")))));

        assertEquals(new BigDecimal("-20000.00"), total(ladder, "ON", LadderSourceCode.SAVINGS));
        assertEquals(new BigDecimal("-20000.00"), total(ladder, "D2_8", LadderSourceCode.SAVINGS));
        assertEquals(new BigDecimal("-40000.00"), total(ladder, "D9_1M", LadderSourceCode.SAVINGS));
        assertEquals(new BigDecimal("-40000.00"), total(ladder, "M6_12", LadderSourceCode.SAVINGS));
        assertEquals(new BigDecimal("-800000.00"), total(ladder, "Y1_PLUS", LadderSourceCode.SAVINGS),
                "core 600,000 + 200,000 not run off");
        assertEquals(new BigDecimal("-1000000.00"), ladder.totals().getLast().cumulativeGap());
    }

    @Test
    void savingsWithoutAssumptionsAreContractualAndRunOffAbove100PercentIsFlagged() {
        final LadderContext contractual = LadderFixtures.context(Assumptions.NONE);
        final LadderBuilder.Ladder ladder = LadderBuilder.build(contractual, SavingsLadderSource.flows(contractual,
                List.of(new SavingsLadderSource.ProductBalance(1L, "Savings", new BigDecimal("500")))));
        assertEquals(new BigDecimal("-500"), total(ladder, "ON", LadderSourceCode.SAVINGS));

        final LadderContext over = LadderFixtures
                .context(new Assumptions(List.of(row(AssumptionType.RUN_OFF, LadderSourceCode.SAVINGS, null, "ON", null, null, "80"),
                        row(AssumptionType.RUN_OFF, LadderSourceCode.SAVINGS, null, "D2_8", null, null, "40"),
                        row(AssumptionType.RUN_OFF, LadderSourceCode.SAVINGS, null, "Q9", null, null, "1"))));
        final LadderBuilder.Ladder capped = LadderBuilder.build(over,
                SavingsLadderSource.flows(over, List.of(new SavingsLadderSource.ProductBalance(1L, "Savings", new BigDecimal("1000")))));
        assertEquals(new BigDecimal("-200.00"), total(capped, "D2_8", LadderSourceCode.SAVINGS), "never more than what is left");
        assertTrue(over.warnings().stream().anyMatch(w -> w.contains("above 100%")));
        assertTrue(over.warnings().stream().anyMatch(w -> w.contains("Q9")));
    }

    @Test
    void loanInArrearsIsHaircutAndACurrentLoanPrepays() {
        final LadderContext context = LadderFixtures
                .context(new Assumptions(List.of(row(AssumptionType.ARREARS_HAIRCUT, LadderSourceCode.LOANS, null, null, 1, 30, "10"),
                        row(AssumptionType.ARREARS_HAIRCUT, LadderSourceCode.LOANS, null, null, 31, 90, "50"),
                        row(AssumptionType.PREPAYMENT, LadderSourceCode.LOANS, null, null, null, null, "2"))));
        final List<LoanLadderSource.Installment> installments = List.of(
                // loan 1: 45 days in arrears
                new LoanLadderSource.Installment(1L, 9L, "Group loan", AS_OF.minusDays(45), new BigDecimal("50000"),
                        new BigDecimal("5000")),
                new LoanLadderSource.Installment(1L, 9L, "Group loan", AS_OF.plusDays(15), new BigDecimal("50000"), new BigDecimal("5000")),
                // loan 2: current, principal due in 60 days
                new LoanLadderSource.Installment(2L, 9L, "Group loan", AS_OF.plusDays(10), new BigDecimal("100000"),
                        new BigDecimal("8000")),
                new LoanLadderSource.Installment(2L, 9L, "Group loan", AS_OF.plusDays(60), new BigDecimal("100000"),
                        new BigDecimal("4000")));
        final LadderBuilder.Ladder ladder = LadderBuilder.build(context, LoanLadderSource.flows(context, installments));

        assertEquals(new BigDecimal("-27500.00"), behavioural(ladder, "ON", LadderSourceCode.LOANS).subtract(new BigDecimal("2000.00")),
                "half of the 55,000 overdue is not expected (the 2,000 is loan 2's prepayment)");
        assertEquals(new BigDecimal("-27500.00"), behavioural(ladder, "D9_1M", LadderSourceCode.LOANS), "and half of what falls due later");
        assertEquals(new BigDecimal("-2000.00"), behavioural(ladder, "M1_3", LadderSourceCode.LOANS),
                "2% of the 60-day principal comes early");
        assertEquals(new BigDecimal("102000.00"), total(ladder, "M1_3", LadderSourceCode.LOANS));
    }

    @Test
    void termDepositsRollOverAndCommitmentsAreDrawnInPart() {
        final LadderContext context = LadderFixtures
                .context(new Assumptions(List.of(row(AssumptionType.ROLLOVER, LadderSourceCode.TERM_DEPOSITS, null, null, null, null, "70"),
                        row(AssumptionType.ROLLOVER, LadderSourceCode.TERM_DEPOSITS, 4L, null, null, null, "20"),
                        row(AssumptionType.DRAWDOWN, LadderSourceCode.COMMITMENTS, null, null, null, null, "80"))));
        final LadderBuilder.Ladder deposits = LadderBuilder.build(context,
                TermDepositLadderSource.flows(context, List.of(
                        new TermDepositLadderSource.Deposit(3L, "Fixed 3m", false, AS_OF.plusDays(40), new BigDecimal("1000000")),
                        new TermDepositLadderSource.Deposit(4L, "Fixed 6m", false, AS_OF.plusDays(100), new BigDecimal("1000000")))));
        assertEquals(new BigDecimal("-300000.00"), total(deposits, "M1_3", LadderSourceCode.TERM_DEPOSITS), "70% rolls over");
        assertEquals(new BigDecimal("-800000.00"), total(deposits, "M3_6", LadderSourceCode.TERM_DEPOSITS), "product 4 rolls only 20%");

        final LadderBuilder.Ladder commitments = LadderBuilder.build(context, CommitmentLadderSource.flows(context,
                List.of(new CommitmentLadderSource.Commitment(9L, "Group loan", AS_OF.minusDays(3), new BigDecimal("500000")))));
        assertEquals(new BigDecimal("-400000.00"), total(commitments, "ON", LadderSourceCode.COMMITMENTS),
                "overdue disbursement on day one, 80% expected to be drawn");
    }

    @Test
    void dealAssetsComeInLiabilitiesGoOutAndPlacementsRoll() {
        final LadderContext context = LadderFixtures
                .context(new Assumptions(List.of(row(AssumptionType.ROLLOVER, LadderSourceCode.DEALS, null, null, null, null, "50"))));
        final LocalDate maturity = AS_OF.plusDays(20);
        final LadderBuilder.Ladder ladder = LadderBuilder.build(context, DealLadderSource.flows(context, List.of(
                new DealLadderSource.DealFlow(1L, "MM", InstrumentKind.MONEY_MARKET_PLACEMENT, maturity, false, maturity,
                        new BigDecimal("14794520.55")),
                new DealLadderSource.DealFlow(1L, "MM", InstrumentKind.MONEY_MARKET_PLACEMENT, maturity, true, maturity,
                        new BigDecimal("500000000")),
                new DealLadderSource.DealFlow(2L, "TB", InstrumentKind.TREASURY_BILL, maturity, true, maturity,
                        new BigDecimal("1000000000")),
                new DealLadderSource.DealFlow(3L, "BR", InstrumentKind.BORROWING, maturity, true, maturity, new BigDecimal("200000000"))),
                List.of(new DealLadderSource.Settlement(1L, "MM", InstrumentKind.MONEY_MARKET_PLACEMENT, AS_OF.plusDays(1),
                        new BigDecimal("100000000")))));

        // + interest + placement half rolled + bill in full - borrowing half rolled
        assertEquals(new BigDecimal("14794520.55").add(new BigDecimal("250000000.00")).add(new BigDecimal("1000000000"))
                .subtract(new BigDecimal("100000000.00")), total(ladder, "D9_1M", LadderSourceCode.DEALS));
        assertEquals(new BigDecimal("-100000000"), total(ladder, "ON", LadderSourceCode.DEALS),
                "an approved placement pays out on its value date");
    }

    @Test
    void agentFloatDemandFromHistory() {
        // 3,000,000 net top-ups over 30 days: 100,000 a day, taken out over each bounded bucket
        final LadderContext context = LadderFixtures.context(Assumptions.NONE);
        final LadderBuilder.Ladder ladder = LadderBuilder.build(context,
                AgentFloatLadderSource.flows(context, new BigDecimal("3000000"), 30, new BigDecimal("100")));

        assertEquals(new BigDecimal("-200000.00"), total(ladder, "ON", LadderSourceCode.AGENT_FLOAT));
        assertEquals(new BigDecimal("-700000.00"), total(ladder, "D2_8", LadderSourceCode.AGENT_FLOAT));
        assertEquals(new BigDecimal("-2200000.00"), total(ladder, "D9_1M", LadderSourceCode.AGENT_FLOAT));
        assertEquals(0, total(ladder, "Y1_PLUS", LadderSourceCode.AGENT_FLOAT).signum(), "the open-ended bucket is not projected");
        final LadderBuilder.Ladder half = LadderBuilder.build(context,
                AgentFloatLadderSource.flows(context, new BigDecimal("3000000"), 30, new BigDecimal("50")));
        assertEquals(new BigDecimal("-100000.00"), total(half, "ON", LadderSourceCode.AGENT_FLOAT));
    }

    @Test
    void ladderItemsOnceOrMonthlyWithinTheirWindow() {
        final TreasuryLadderItem salaries = item(null, 31, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 3, 15));
        assertEquals(List.of(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 31),
                LocalDate.of(2027, 2, 28)), FixedLadderSource.dates(salaries, AS_OF, LocalDate.of(2027, 12, 31)));
        final TreasuryLadderItem tax = item(LocalDate.of(2026, 11, 15), null, LocalDate.of(2026, 1, 1), null);
        assertEquals(List.of(LocalDate.of(2026, 11, 15)), FixedLadderSource.dates(tax, AS_OF, LocalDate.of(2027, 12, 31)));
        assertEquals(List.of(), FixedLadderSource.dates(tax, LocalDate.of(2026, 11, 16), LocalDate.of(2027, 12, 31)));
        final TreasuryLadderItem later = item(null, 5, LocalDate.of(2026, 12, 1), null);
        assertEquals(LocalDate.of(2026, 12, 5), FixedLadderSource.dates(later, AS_OF, LocalDate.of(2027, 1, 1)).getFirst());
    }

    @Test
    void productAssumptionWinsOverTheGeneralOne() {
        final Assumptions assumptions = new Assumptions(
                List.of(row(AssumptionType.ROLLOVER, LadderSourceCode.DEALS, null, null, null, null, "50"),
                        row(AssumptionType.ROLLOVER, LadderSourceCode.DEALS, 7L, null, null, null, "10"),
                        row(AssumptionType.ARREARS_HAIRCUT, LadderSourceCode.LOANS, null, null, 1, 30, "10"),
                        row(AssumptionType.ARREARS_HAIRCUT, LadderSourceCode.LOANS, 9L, null, 1, null, "100")));
        assertEquals(new BigDecimal("10"), assumptions.rate(AssumptionType.ROLLOVER, LadderSourceCode.DEALS, 7L, null).orElseThrow());
        assertEquals(new BigDecimal("50"), assumptions.rate(AssumptionType.ROLLOVER, LadderSourceCode.DEALS, 8L, null).orElseThrow());
        assertTrue(assumptions.rate(AssumptionType.ROLLOVER, LadderSourceCode.TERM_DEPOSITS, 7L, null).isEmpty());
        assertEquals(new BigDecimal("100"),
                assumptions.band(AssumptionType.ARREARS_HAIRCUT, LadderSourceCode.LOANS, 9L, 400).orElseThrow().rate());
        assertTrue(assumptions.band(AssumptionType.ARREARS_HAIRCUT, LadderSourceCode.LOANS, 8L, 31).isEmpty());
        assertTrue(assumptions.band(AssumptionType.ARREARS_HAIRCUT, LadderSourceCode.LOANS, 8L, 0).isEmpty());
    }

    private static TreasuryLadderItem item(final LocalDate oneOff, final Integer day, final LocalDate from, final LocalDate to) {
        final TreasuryLadderItem item = new TreasuryLadderItem();
        item.setOneOffDate(oneOff);
        item.setDayOfMonth(day);
        item.setActiveFrom(from);
        item.setActiveTo(to);
        return item;
    }

    @Test
    void loanFlowsAreSummedByLineProductDateAndSign() {
        final LocalDate due = AS_OF.plusDays(10);
        final Map<LadderFlow, BigDecimal> totals = new LinkedHashMap<>();
        LoanLadderSource.add(totals, List.of(
                LadderFlow.contractual(LadderSourceCode.LOANS, LoanLadderSource.PRINCIPAL, 9L, "Group loan", due, new BigDecimal("100")),
                LadderFlow.behavioural(LadderSourceCode.LOANS, LoanLadderSource.PRINCIPAL, 9L, "Group loan", due, new BigDecimal("-10"))));
        LoanLadderSource.add(totals, List.of(
                LadderFlow.contractual(LadderSourceCode.LOANS, LoanLadderSource.PRINCIPAL, 9L, "Group loan", due, new BigDecimal("50.5")),
                LadderFlow.behavioural(LadderSourceCode.LOANS, LoanLadderSource.PRINCIPAL, 9L, "Group loan", due, new BigDecimal("5")),
                LadderFlow.contractual(LadderSourceCode.LOANS, LoanLadderSource.INTEREST, 9L, "Group loan", due, new BigDecimal("7"))));
        assertEquals(List.of(new BigDecimal("150.5"), new BigDecimal("-10"), new BigDecimal("5"), new BigDecimal("7")),
                List.copyOf(totals.values()));
    }
}
