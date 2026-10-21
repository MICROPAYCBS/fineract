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
package com.micropay.fineract.loan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.core.serialization.GoogleGsonSerializerHelper;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionData;
import org.apache.fineract.portfolio.loanaccount.data.OutstandingAmountsDTO;
import org.apache.fineract.portfolio.loanaccount.data.PeriodicAccrualPayoffData;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.service.CumulativeLoanSummaryDataProvider;
import org.apache.fineract.portfolio.loanaccount.service.LoanSummaryDataProvider;
import org.apache.fineract.portfolio.loanaccount.service.LoanSummaryProviderDelegate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PeriodicAccrualPayoffAssemblerTest {

    private static final LocalDate PAST_FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate PERIOD_FROM = LocalDate.of(2026, 2, 1);
    private static final LocalDate AS_OF = LocalDate.of(2026, 2, 15);
    private static final LocalDate PERIOD_DUE = LocalDate.of(2026, 3, 1);
    private static final LocalDate FUTURE_DUE = LocalDate.of(2026, 4, 1);
    private static final MonetaryCurrency USD = new MonetaryCurrency("USD", 2, null);

    private MockedStatic<MoneyHelper> moneyHelper;
    private CumulativeLoanSummaryDataProvider cumulative;
    private PeriodicAccrualPayoffAssembler assembler;

    @BeforeEach
    void setUp() {
        moneyHelper = org.mockito.Mockito.mockStatic(MoneyHelper.class);
        moneyHelper.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        moneyHelper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        cumulative = org.mockito.Mockito.spy(new CumulativeLoanSummaryDataProvider());
        assembler = new PeriodicAccrualPayoffAssembler(new LoanSummaryProviderDelegate(List.of(cumulative)));
    }

    @AfterEach
    void tearDown() {
        moneyHelper.close();
    }

    @Test
    void periodicAccrualWithoutRecalculationSplitsUnearnedInterestAndReportsNoGapWhenAccrualIsCaughtUp() {
        final Loan loan = periodicLoan(false, false);
        final List<LoanRepaymentScheduleInstallment> installments = schedule("140.00");
        final PeriodicAccrualPayoffData payoff = quote(loan, installments, prepay("780.00"));

        assertThat(payoff.getAsOfDate()).isEqualTo(AS_OF);
        assertThat(payoff.getAccruedTill()).isEqualTo(AS_OF);
        assertThat(payoff.isAccrualPaused()).isFalse();
        assertThat(payoff.getInterestEarnedOutstanding()).isEqualByComparingTo("340.00");
        assertThat(payoff.getInterestOnContractOutstanding()).isEqualByComparingTo("780.00");
        assertThat(payoff.getInterestPostedOutstanding()).isEqualByComparingTo("340.00");
        assertThat(payoff.getInterestBooked()).isEqualByComparingTo("340.00");
        assertThat(payoff.getContractPayoff()).isEqualByComparingTo("1795.00");
        assertThat(payoff.getEarnedPayoff()).isEqualByComparingTo("1355.00");
        assertThat(payoff.getContractPayoff().subtract(payoff.getEarnedPayoff())).isEqualByComparingTo("440.00");
        assertThat(payoff.getInterestNotYetInBooks()).isEqualByComparingTo("0.00");
        assertThat(payoff.getPrincipalOutstanding()).isEqualByComparingTo("1000.00");
        assertThat(payoff.getFeeChargesOutstanding()).isEqualByComparingTo("10.00");
        assertThat(payoff.getPenaltyChargesOutstanding()).isEqualByComparingTo("5.00");
    }

    @Test
    void accrualBehindThePayoffDateLeavesEarnedInterestOutOfTheBooksAndDoesNotChangeTheContract() {
        final Loan loan = periodicLoan(false, false);
        when(loan.getAccruedTill()).thenReturn(PERIOD_FROM);
        final PeriodicAccrualPayoffData caughtUp = quote(periodicLoan(false, false), schedule("140.00"), prepay("780.00"));
        final PeriodicAccrualPayoffData behind = quote(loan, schedule("40.00"), prepay("780.00"));

        assertThat(behind.getInterestEarnedOutstanding()).isEqualByComparingTo("340.00");
        assertThat(behind.getInterestPostedOutstanding()).isEqualByComparingTo("240.00");
        assertThat(behind.getInterestNotYetInBooks()).isEqualByComparingTo("100.00");
        assertThat(behind.getContractPayoff()).isEqualByComparingTo(caughtUp.getContractPayoff());
        assertThat(behind.isAccrualPaused()).isFalse();
    }

    @Test
    void nonPerformingLoanKeepsEarnedInterestAheadOfPostedInterestAndReportsNoBooksGap() {
        final Loan loan = periodicLoan(false, true);
        when(loan.getAccruedTill()).thenReturn(PERIOD_FROM);
        final PeriodicAccrualPayoffData payoff = quote(loan, schedule("40.00"), prepay("780.00"));

        assertThat(payoff.isAccrualPaused()).isTrue();
        assertThat(payoff.getInterestEarnedOutstanding()).isEqualByComparingTo("340.00");
        assertThat(payoff.getInterestPostedOutstanding()).isEqualByComparingTo("240.00");
        assertThat(payoff.getInterestNotYetInBooks()).isEqualByComparingTo("0.00");
    }

    @Test
    void interestBookedKeepsInterestAlreadyPaidOrWaived() {
        final Loan loan = periodicLoan(false, false);
        final List<LoanRepaymentScheduleInstallment> installments = schedule("40.00");
        final Money accrued = money("250.00");
        final Money paid = money("200.00");
        final Money waived = money("50.00");
        final LoanRepaymentScheduleInstallment first = installments.get(0);
        when(first.getInterestAccrued(USD)).thenReturn(accrued);
        when(first.getInterestPaid(USD)).thenReturn(paid);
        when(first.getInterestWaived(USD)).thenReturn(waived);
        final PeriodicAccrualPayoffData payoff = quote(loan, installments, prepay("780.00"));

        assertThat(payoff.getInterestBooked()).isEqualByComparingTo("290.00");
        assertThat(payoff.getInterestPostedOutstanding()).isEqualByComparingTo("40.00");
    }

    @Test
    void interestRecalculationUsesThePrepayInterestAndKeepsTheStoredScheduleAsTheContract() {
        final Loan loan = periodicLoan(true, false);
        final List<LoanRepaymentScheduleInstallment> installments = schedule("140.00");
        final PeriodicAccrualPayoffAssembler.StoredSchedule stored = capture(loan, installments);
        final Money rebuiltInterest = money("1.00");
        when(installments.get(1).getInterestOutstanding(USD)).thenReturn(rebuiltInterest);
        final PeriodicAccrualPayoffData payoff = assembler.assemble(loan, AS_OF, stored, prepay("150.00"));

        assertThat(payoff.getInterestEarnedOutstanding()).isEqualByComparingTo("150.00");
        assertThat(payoff.getInterestOnContractOutstanding()).isEqualByComparingTo("780.00");
        assertThat(payoff.getEarnedPayoff()).isEqualByComparingTo("1165.00");
        assertThat(payoff.getContractPayoff()).isEqualByComparingTo("1795.00");
        verify(cumulative, never()).computeTotalUnpaidPayableDueInterestAmount(any(), any());
    }

    @Test
    void chargedOffAndContractTerminatedLoansUseThePrepayInterestRatherThanTheEarnedSlice() {
        final Loan chargedOff = periodicLoan(true, false);
        when(chargedOff.isChargeOffOnDate(AS_OF)).thenReturn(true);
        final PeriodicAccrualPayoffData chargedOffPayoff = quote(chargedOff, schedule("40.00"), prepay("780.00"));

        final Loan terminated = periodicLoan(false, false);
        when(terminated.isContractTermination()).thenReturn(true);
        final PeriodicAccrualPayoffData terminatedPayoff = quote(terminated, schedule("40.00"), prepay("780.00"));

        assertThat(chargedOffPayoff.getInterestEarnedOutstanding()).isEqualByComparingTo("780.00");
        assertThat(terminatedPayoff.getInterestEarnedOutstanding()).isEqualByComparingTo("780.00");
        verify(cumulative, never()).computeTotalUnpaidPayableNotDueInterestAmountOnActualPeriod(any(), any(), any(), any(), any());
    }

    @Test
    void progressiveLoansUseTheResolvedSummaryProviderForTheEarnedSlice() {
        final LoanSummaryDataProvider progressive = mock(LoanSummaryDataProvider.class);
        when(progressive.accept("advanced-payment-allocation-strategy")).thenReturn(true);
        when(progressive.computeTotalUnpaidPayableDueInterestAmount(any(), eq(AS_OF))).thenReturn(new BigDecimal("200.00"));
        when(progressive.computeTotalUnpaidPayableNotDueInterestAmountOnActualPeriod(any(), any(), eq(AS_OF), any(), any()))
                .thenReturn(new BigDecimal("15.00"));
        final PeriodicAccrualPayoffAssembler progressiveAssembler = new PeriodicAccrualPayoffAssembler(
                new LoanSummaryProviderDelegate(List.of(new CumulativeLoanSummaryDataProvider(), progressive)));

        final Loan loan = periodicLoan(false, false);
        when(loan.transactionProcessingStrategy()).thenReturn("advanced-payment-allocation-strategy");
        final PeriodicAccrualPayoffData payoff = progressiveAssembler.assemble(loan, AS_OF, capture(loan, schedule("0.00")),
                prepay("780.00"));

        assertThat(payoff.getInterestEarnedOutstanding()).isEqualByComparingTo("215.00");
        assertThat(payoff.getInterestOnContractOutstanding()).isEqualByComparingTo("780.00");
        verify(progressive).computeTotalUnpaidPayableNotDueInterestAmountOnActualPeriod(eq(loan), any(), eq(AS_OF), any(),
                eq(new BigDecimal("200.00")));
    }

    @Test
    void cashAndUpfrontAccrualProductsHaveNoBreakdown() {
        final Loan cash = mock(Loan.class);
        when(cash.isPeriodicAccrualAccountingEnabledOnLoanProduct()).thenReturn(false);
        final Loan upfront = mock(Loan.class);
        when(upfront.isPeriodicAccrualAccountingEnabledOnLoanProduct()).thenReturn(false);

        assertThat(assembler.captureIfPeriodic(cash)).isNull();
        assertThat(assembler.captureIfPeriodic(upfront)).isNull();
    }

    @Test
    void breakdownSerializesAccruedTillAsNullAndIsOmittedWhenAbsent() {
        final PeriodicAccrualPayoffData payoff = PeriodicAccrualPayoffData.builder().asOfDate(AS_OF).accruedTill(null).accrualPaused(true)
                .principalOutstanding(new BigDecimal("1.00")).feeChargesOutstanding(BigDecimal.ZERO)
                .penaltyChargesOutstanding(BigDecimal.ZERO).interestOnContractOutstanding(BigDecimal.ZERO)
                .interestEarnedOutstanding(BigDecimal.ZERO).interestPostedOutstanding(BigDecimal.ZERO)
                .interestBooked(new BigDecimal("2.00")).contractPayoff(new BigDecimal("1.00")).earnedPayoff(new BigDecimal("1.00"))
                .interestNotYetInBooks(BigDecimal.ZERO).build();
        final LoanTransactionData withBreakdown = LoanTransactionData.builder().amount(new BigDecimal("9.00")).date(AS_OF)
                .manuallyReversed(false).build();
        withBreakdown.setPeriodicAccrualPayoff(payoff);
        final String present = GoogleGsonSerializerHelper.createSimpleGson().toJson(withBreakdown);
        final String absent = GoogleGsonSerializerHelper.createSimpleGson()
                .toJson(LoanTransactionData.builder().amount(new BigDecimal("9.00")).manuallyReversed(false).build());

        assertThat(present).contains("\"amount\":9.00");
        assertThat(present).contains("\"periodicAccrualPayoff\"");
        assertThat(present).contains("\"accruedTill\":null");
        assertThat(present).contains("\"interestBooked\":2.00");
        assertThat(present).contains("[2026,2,15]");
        assertThat(absent).doesNotContain("periodicAccrualPayoff");
    }

    private PeriodicAccrualPayoffData quote(final Loan loan, final List<LoanRepaymentScheduleInstallment> installments,
            final OutstandingAmountsDTO prepay) {
        return assembler.assemble(loan, AS_OF, capture(loan, installments), prepay);
    }

    private PeriodicAccrualPayoffAssembler.StoredSchedule capture(final Loan loan,
            final List<LoanRepaymentScheduleInstallment> installments) {
        when(loan.getRepaymentScheduleInstallments()).thenReturn(installments);
        return assembler.captureIfPeriodic(loan);
    }

    private Loan periodicLoan(final boolean interestRecalculation, final boolean npa) {
        final Loan loan = mock(Loan.class);
        when(loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()).thenReturn(true);
        when(loan.getCurrency()).thenReturn(USD);
        when(loan.transactionProcessingStrategy()).thenReturn("mifos-standard-strategy");
        when(loan.isInterestBearingAndInterestRecalculationEnabled()).thenReturn(interestRecalculation);
        when(loan.isChargeOffOnDate(any())).thenReturn(false);
        when(loan.isContractTermination()).thenReturn(false);
        when(loan.isNpa()).thenReturn(npa);
        when(loan.getAccruedTill()).thenReturn(AS_OF);
        return loan;
    }

    private List<LoanRepaymentScheduleInstallment> schedule(final String currentAccrued) {
        return List.of(installment(1, PAST_FROM, PERIOD_FROM, "400.00", "200.00", "200.00", "10.00", "5.00"),
                installment(2, PERIOD_FROM, PERIOD_DUE, "0.00", "280.00", currentAccrued, "0.00", "0.00"),
                installment(3, PERIOD_DUE, FUTURE_DUE, "600.00", "300.00", "0.00", "0.00", "0.00"));
    }

    private LoanRepaymentScheduleInstallment installment(final int number, final LocalDate from, final LocalDate due,
            final String principal, final String interest, final String accrued, final String fees, final String penalties) {
        final Money principalMoney = money(principal);
        final Money interestMoney = money(interest);
        final Money accruedMoney = money(accrued);
        final Money feesMoney = money(fees);
        final Money penaltiesMoney = money(penalties);
        final Money zero = Money.zero(USD);
        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        when(installment.getInstallmentNumber()).thenReturn(number);
        when(installment.getFromDate()).thenReturn(from);
        when(installment.getDueDate()).thenReturn(due);
        when(installment.isDownPayment()).thenReturn(false);
        when(installment.getPrincipalOutstanding(USD)).thenReturn(principalMoney);
        when(installment.getInterestCharged(USD)).thenReturn(interestMoney);
        when(installment.getInterestOutstanding(USD)).thenReturn(interestMoney);
        when(installment.getInterestPaid(USD)).thenReturn(zero);
        when(installment.getInterestWaived(USD)).thenReturn(zero);
        when(installment.getInterestWrittenOff(USD)).thenReturn(zero);
        when(installment.getInterestAccrued(USD)).thenReturn(accruedMoney);
        when(installment.getFeeChargesOutstanding(USD)).thenReturn(feesMoney);
        when(installment.getPenaltyChargesOutstanding(USD)).thenReturn(penaltiesMoney);
        return installment;
    }

    private OutstandingAmountsDTO prepay(final String interest) {
        return new OutstandingAmountsDTO(USD).principal(money("1000.00")).interest(money(interest)).feeCharges(money("10.00"))
                .penaltyCharges(money("5.00"));
    }

    private static Money money(final String amount) {
        return Money.of(USD, new BigDecimal(amount));
    }
}
