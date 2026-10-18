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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.MathUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.data.OutstandingAmountsDTO;
import org.apache.fineract.portfolio.loanaccount.data.PeriodicAccrualPayoffData;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanSchedulePeriodData;
import org.apache.fineract.portfolio.loanaccount.service.LoanSummaryDataProvider;
import org.apache.fineract.portfolio.loanaccount.service.LoanSummaryProviderDelegate;
import org.springframework.stereotype.Component;

/**
 * Builds the periodic-accrual payoff split for a prepay quote. Contract interest is read from the stored schedule.
 * Earned interest uses {@code calculatePrepaymentAmount} when that is what the prepay template uses, and otherwise the
 * same due-plus-current-slice basis as {@code totalUnpaidPayableDueInterest} and
 * {@code totalUnpaidPayableNotDueInterest}.
 */
@Component
@RequiredArgsConstructor
public class PeriodicAccrualPayoffAssembler {

    private final LoanSummaryProviderDelegate loanSummaryProviderDelegate;

    /**
     * Snapshot of the stored schedule, taken before prepayment recalculation can rebuild one. Null when the product
     * does not use periodic accrual.
     */
    public StoredSchedule captureIfPeriodic(final Loan loan) {
        if (!Boolean.TRUE.equals(loan.isPeriodicAccrualAccountingEnabledOnLoanProduct())) {
            return null;
        }
        final MonetaryCurrency currency = loan.getCurrency();
        Money principal = Money.zero(currency);
        Money interestOnContract = Money.zero(currency);
        Money fees = Money.zero(currency);
        Money penalties = Money.zero(currency);
        Money interestPosted = Money.zero(currency);
        Money interestBooked = Money.zero(currency);
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            principal = principal.plus(installment.getPrincipalOutstanding(currency));
            interestOnContract = interestOnContract.plus(installment.getInterestOutstanding(currency));
            fees = fees.plus(installment.getFeeChargesOutstanding(currency));
            penalties = penalties.plus(installment.getPenaltyChargesOutstanding(currency));
            interestPosted = interestPosted.plus(unpaidPostedInterest(installment, currency));
            interestBooked = interestBooked.plus(installment.getInterestAccrued(currency));
        }
        return new StoredSchedule(principal, interestOnContract, fees, penalties, interestPosted, interestBooked);
    }

    public PeriodicAccrualPayoffData assemble(final Loan loan, final LocalDate onDate, final StoredSchedule stored,
            final OutstandingAmountsDTO prepay) {
        final LocalDate asOfDate = onDate != null ? onDate : DateUtils.getBusinessLocalDate();
        final MonetaryCurrency currency = loan.getCurrency();
        final BigDecimal interestEarned = interestEarnedOutstanding(loan, asOfDate, currency, prepay);
        final BigDecimal interestNotYetInBooks = loan.isNpa() ? Money.zero(currency).getAmount()
                : money(currency, interestEarned).minus(stored.interestPosted()).getAmount();
        return PeriodicAccrualPayoffData.builder().asOfDate(asOfDate).accruedTill(loan.getAccruedTill()).accrualPaused(loan.isNpa())
                .principalOutstanding(stored.principal().getAmount()).feeChargesOutstanding(stored.fees().getAmount())
                .penaltyChargesOutstanding(stored.penalties().getAmount())
                .interestOnContractOutstanding(stored.interestOnContract().getAmount()).interestEarnedOutstanding(interestEarned)
                .interestPostedOutstanding(stored.interestPosted().getAmount()).interestBooked(stored.interestBooked().getAmount())
                .contractPayoff(
                        stored.principal().plus(stored.interestOnContract()).plus(stored.fees()).plus(stored.penalties()).getAmount())
                .earnedPayoff(
                        stored.principal().plus(money(currency, interestEarned)).plus(stored.fees()).plus(stored.penalties()).getAmount())
                .interestNotYetInBooks(interestNotYetInBooks).build();
    }

    /**
     * Unpaid interest already recognized on one installment: accrued minus paid, waived and written off, not below
     * zero. Not pro-rated.
     */
    private static Money unpaidPostedInterest(final LoanRepaymentScheduleInstallment installment, final MonetaryCurrency currency) {
        final Money unpaid = installment.getInterestAccrued(currency).minus(installment.getInterestPaid(currency))
                .minus(installment.getInterestWaived(currency)).minus(installment.getInterestWrittenOff(currency));
        return unpaid.isLessThanZero() ? Money.zero(currency) : unpaid;
    }

    /**
     * Active loans without interest recalculation earn the due installments plus the current installment's slice. Every
     * other state uses the interest {@code fetchPrepaymentDetail} already computed, including a charged-off or
     * contract-terminated loan whose prepay amount is the stored schedule.
     */
    private BigDecimal interestEarnedOutstanding(final Loan loan, final LocalDate asOfDate, final MonetaryCurrency currency,
            final OutstandingAmountsDTO prepay) {
        if (loan.isChargeOffOnDate(asOfDate) || loan.isContractTermination() || loan.isInterestBearingAndInterestRecalculationEnabled()) {
            return prepay.interest().getAmount();
        }
        final LoanSummaryDataProvider provider = this.loanSummaryProviderDelegate
                .resolveLoanSummaryDataProvider(loan.transactionProcessingStrategy());
        final List<LoanSchedulePeriodData> periods = toSchedulePeriods(loan.getRepaymentScheduleInstallments(), currency);
        final BigDecimal due = MathUtil.nullToZero(provider.computeTotalUnpaidPayableDueInterestAmount(periods, asOfDate));
        final BigDecimal notDue = MathUtil.nullToZero(
                provider.computeTotalUnpaidPayableNotDueInterestAmountOnActualPeriod(loan, periods, asOfDate, currency.toData(), due));
        return money(currency, due).plus(notDue).getAmount();
    }

    private static List<LoanSchedulePeriodData> toSchedulePeriods(final List<LoanRepaymentScheduleInstallment> installments,
            final MonetaryCurrency currency) {
        final List<LoanSchedulePeriodData> periods = new ArrayList<>();
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (installment.getFromDate() == null || installment.getDueDate() == null) {
                continue;
            }
            periods.add(LoanSchedulePeriodData.builder().period(installment.getInstallmentNumber()).fromDate(installment.getFromDate())
                    .dueDate(installment.getDueDate())
                    .daysInPeriod(DateUtils.getExactDifferenceInDays(installment.getFromDate(), installment.getDueDate()))
                    .downPaymentPeriod(installment.isDownPayment()).interestDue(amount(installment.getInterestCharged(currency)))
                    .interestPaid(amount(installment.getInterestPaid(currency)))
                    .interestWaived(amount(installment.getInterestWaived(currency)))
                    .interestWrittenOff(amount(installment.getInterestWrittenOff(currency)))
                    .interestOutstanding(amount(installment.getInterestOutstanding(currency))).build());
        }
        return periods;
    }

    private static BigDecimal amount(final Money money) {
        return money == null ? BigDecimal.ZERO : money.getAmount();
    }

    private static Money money(final MonetaryCurrency currency, final BigDecimal amount) {
        return Money.of(currency, MathUtil.nullToZero(amount));
    }

    public record StoredSchedule(Money principal, Money interestOnContract, Money fees, Money penalties, Money interestPosted,
            Money interestBooked) {
    }
}
