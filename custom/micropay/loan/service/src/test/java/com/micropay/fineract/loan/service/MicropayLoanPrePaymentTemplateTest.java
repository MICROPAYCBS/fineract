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
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionData;
import org.apache.fineract.portfolio.loanaccount.data.OutstandingAmountsDTO;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.service.CumulativeLoanSummaryDataProvider;
import org.apache.fineract.portfolio.loanaccount.service.LoanSummaryProviderDelegate;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MicropayLoanPrePaymentTemplateTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 2, 15);
    private static final LocalDate PERIOD_FROM = LocalDate.of(2026, 2, 1);
    private static final LocalDate PERIOD_DUE = LocalDate.of(2026, 3, 1);
    private static final MonetaryCurrency USD = new MonetaryCurrency("USD", 2, null);
    private static final Long LOAN_ID = 42L;

    private MockedStatic<MoneyHelper> moneyHelper;
    private LoanRepositoryWrapper loanRepository;
    private LoanUtilService loanUtilService;
    private LoanTransactionProcessingService transactionProcessing;
    private PaymentTypeReadService paymentTypes;
    private MicropayLoanReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        moneyHelper = org.mockito.Mockito.mockStatic(MoneyHelper.class);
        moneyHelper.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        moneyHelper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE);
        ThreadLocalContextUtil.setBusinessDates(businessDates);

        loanRepository = mock(LoanRepositoryWrapper.class);
        loanUtilService = mock(LoanUtilService.class);
        transactionProcessing = mock(LoanTransactionProcessingService.class);
        paymentTypes = mock(PaymentTypeReadService.class);
        when(paymentTypes.retrieveActivePaymentTypes()).thenReturn(List.of());
        final ApplicationCurrencyRepositoryWrapper currencies = mock(ApplicationCurrencyRepositoryWrapper.class);
        final ApplicationCurrency applicationCurrency = mock(ApplicationCurrency.class);
        when(applicationCurrency.toData()).thenReturn(USD.toData());
        when(currencies.findOneWithNotFoundDetection(any(MonetaryCurrency.class))).thenReturn(applicationCurrency);
        final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
        service = new MicropayLoanReadPlatformServiceImpl(null, context, loanRepository, currencies, null, null, null, null, null, null,
                null, null, null, null, paymentTypes, null, loanUtilService, null, null, null, null, null, null, null, null, null, null,
                transactionProcessing, null, null, null, null, null, null, null,
                new PeriodicAccrualPayoffAssembler(new LoanSummaryProviderDelegate(List.of(new CumulativeLoanSummaryDataProvider()))));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
        moneyHelper.close();
    }

    @Test
    void prepayTemplateKeepsTheFullScheduleAmountWhenTheBreakdownShowsASmallerEarnedPayoff() {
        final Loan loan = loan(false);
        final LoanRepaymentScheduleInstallment current = installment("280.00", "140.00");
        final OutstandingAmountsDTO fullSchedule = prepay("280.00");
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(current));
        when(loanRepository.findOneWithNotFoundDetection(LOAN_ID, true)).thenReturn(loan);
        when(transactionProcessing.fetchPrepaymentDetail(any(), eq(BUSINESS_DATE), eq(loan))).thenReturn(fullSchedule);

        final LoanTransactionData template = service.retrieveLoanPrePaymentTemplate(LoanTransactionType.REPAYMENT, LOAN_ID, BUSINESS_DATE);

        assertThat(template.getDate()).isEqualTo(BUSINESS_DATE);
        assertThat(template.getAmount()).isEqualByComparingTo("280.00");
        assertThat(template.getPrincipalPortion()).isEqualByComparingTo("0.00");
        assertThat(template.getInterestPortion()).isEqualByComparingTo("280.00");
        assertThat(template.getFeeChargesPortion()).isEqualByComparingTo("0.00");
        assertThat(template.getPenaltyChargesPortion()).isEqualByComparingTo("0.00");
        assertThat(template.getPeriodicAccrualPayoff().getContractPayoff()).isEqualByComparingTo("280.00");
        assertThat(template.getPeriodicAccrualPayoff().getEarnedPayoff()).isEqualByComparingTo("140.00");
        assertThat(template.getPeriodicAccrualPayoff().getInterestNotYetInBooks()).isEqualByComparingTo("0.00");
        assertThat(template.getAmount()).isEqualByComparingTo(template.getPeriodicAccrualPayoff().getContractPayoff());
    }

    @Test
    void interestRecalculationKeepsThePrepayAmountAndTheStoredContractInterest() {
        final Loan loan = loan(true);
        final LoanRepaymentScheduleInstallment current = installment("280.00", "140.00");
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(current));
        when(loanRepository.findOneWithNotFoundDetection(LOAN_ID, true)).thenReturn(loan);
        when(loanUtilService.buildScheduleGeneratorDTO(eq(loan), eq(null))).thenReturn(mock(ScheduleGeneratorDTO.class));
        final Money rebuiltInterest = money("1.00");
        final OutstandingAmountsDTO rebuiltPrepay = prepay("90.00");
        when(transactionProcessing.fetchPrepaymentDetail(any(), eq(LocalDate.of(2026, 2, 10)), eq(loan))).thenAnswer(invocation -> {
            when(current.getInterestOutstanding(USD)).thenReturn(rebuiltInterest);
            return rebuiltPrepay;
        });

        final LoanTransactionData template = service.retrieveLoanPrePaymentTemplate(LoanTransactionType.REPAYMENT, LOAN_ID,
                LocalDate.of(2026, 2, 10));

        assertThat(template.getDate()).isEqualTo(BUSINESS_DATE);
        assertThat(template.getAmount()).isEqualByComparingTo("90.00");
        assertThat(template.getInterestPortion()).isEqualByComparingTo("90.00");
        assertThat(template.getPeriodicAccrualPayoff().getAsOfDate()).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(template.getPeriodicAccrualPayoff().getInterestEarnedOutstanding()).isEqualByComparingTo("90.00");
        assertThat(template.getPeriodicAccrualPayoff().getInterestOnContractOutstanding()).isEqualByComparingTo("280.00");
    }

    @Test
    void cashAndUpfrontAccrualTemplatesOmitTheBreakdownAndKeepThePrepayAmount() {
        assertThat(quote(false).getPeriodicAccrualPayoff()).isNull();
        assertThat(quote(false).getAmount()).isEqualByComparingTo("280.00");
        final LoanTransactionData upfront = quote(false);
        assertThat(upfront.getPeriodicAccrualPayoff()).isNull();
        assertThat(upfront.getInterestPortion()).isEqualByComparingTo("280.00");
    }

    private LoanTransactionData quote(final boolean periodic) {
        final Loan loan = loan(false);
        final LoanRepaymentScheduleInstallment current = installment("280.00", "140.00");
        final OutstandingAmountsDTO fullSchedule = prepay("280.00");
        when(loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()).thenReturn(periodic);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(current));
        when(loanRepository.findOneWithNotFoundDetection(LOAN_ID, true)).thenReturn(loan);
        when(transactionProcessing.fetchPrepaymentDetail(any(), eq(BUSINESS_DATE), eq(loan))).thenReturn(fullSchedule);
        return service.retrieveLoanPrePaymentTemplate(LoanTransactionType.REPAYMENT, LOAN_ID, BUSINESS_DATE);
    }

    private Loan loan(final boolean interestRecalculation) {
        final Loan loan = mock(Loan.class);
        when(loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()).thenReturn(true);
        when(loan.getCurrency()).thenReturn(USD);
        when(loan.transactionProcessingStrategy()).thenReturn("mifos-standard-strategy");
        when(loan.isInterestBearingAndInterestRecalculationEnabled()).thenReturn(interestRecalculation);
        when(loan.isChargeOffOnDate(any())).thenReturn(false);
        when(loan.isContractTermination()).thenReturn(false);
        when(loan.isNpa()).thenReturn(false);
        when(loan.getAccruedTill()).thenReturn(BUSINESS_DATE);
        when(loan.getNetDisbursalAmount()).thenReturn(BigDecimal.ZERO);
        return loan;
    }

    private LoanRepaymentScheduleInstallment installment(final String interest, final String accrued) {
        final Money interestMoney = money(interest);
        final Money accruedMoney = money(accrued);
        final Money zero = Money.zero(USD);
        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        when(installment.getInstallmentNumber()).thenReturn(1);
        when(installment.getFromDate()).thenReturn(PERIOD_FROM);
        when(installment.getDueDate()).thenReturn(PERIOD_DUE);
        when(installment.isDownPayment()).thenReturn(false);
        when(installment.getPrincipalOutstanding(USD)).thenReturn(zero);
        when(installment.getInterestCharged(USD)).thenReturn(interestMoney);
        when(installment.getInterestOutstanding(USD)).thenReturn(interestMoney);
        when(installment.getInterestPaid(USD)).thenReturn(zero);
        when(installment.getInterestWaived(USD)).thenReturn(zero);
        when(installment.getInterestWrittenOff(USD)).thenReturn(zero);
        when(installment.getInterestAccrued(USD)).thenReturn(accruedMoney);
        when(installment.getFeeChargesOutstanding(USD)).thenReturn(zero);
        when(installment.getPenaltyChargesOutstanding(USD)).thenReturn(zero);
        return installment;
    }

    private OutstandingAmountsDTO prepay(final String interest) {
        return new OutstandingAmountsDTO(USD).principal(Money.zero(USD)).interest(money(interest)).feeCharges(Money.zero(USD))
                .penaltyCharges(Money.zero(USD));
    }

    private static Money money(final String amount) {
        return Money.of(USD, new BigDecimal(amount));
    }
}
