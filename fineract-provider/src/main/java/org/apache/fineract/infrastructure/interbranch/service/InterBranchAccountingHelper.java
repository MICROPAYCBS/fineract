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
package org.apache.fineract.infrastructure.interbranch.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.closure.domain.GLClosure;
import org.apache.fineract.accounting.common.AccountingConstants.FinancialActivity;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.data.ClientChargePaymentDTO;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterBranchAccountingHelper {

    private final InterBranchGlAccountReadService interBranchGlAccountReadService;
    private final AccountingProcessorHelper accountingProcessorHelper;

    public boolean isCrossBranch(final Long homeOfficeId, final Long transactionOfficeId) {
        return transactionOfficeId != null && homeOfficeId != null && !transactionOfficeId.equals(homeOfficeId);
    }

    public void validateBranchClosures(final Long servicingOfficeId, final Long homeOfficeId, final LocalDate transactionDate) {
        final GLClosure servicingClosure = this.accountingProcessorHelper.getLatestClosureByBranch(servicingOfficeId);
        final GLClosure homeClosure = this.accountingProcessorHelper.getLatestClosureByBranch(homeOfficeId);
        this.accountingProcessorHelper.checkForBranchClosures(servicingClosure, transactionDate);
        this.accountingProcessorHelper.checkForBranchClosures(homeClosure, transactionDate);
    }

    public void createCrossBranchCashBasedJournalEntriesForSavings(final Office servicingOffice, final Office homeOffice,
            final String currencyCode, final int debitAccountType, final int creditAccountType, final Long savingsProductId,
            final Long paymentTypeId, final Long savingsId, final String transactionId, final LocalDate transactionDate,
            final BigDecimal amount, final boolean reversed) {

        final GLAccount debitAccount = this.accountingProcessorHelper.getLinkedGLAccountForSavingsProduct(savingsProductId,
                debitAccountType, paymentTypeId);
        final GLAccount creditAccount = this.accountingProcessorHelper.getLinkedGLAccountForSavingsProduct(savingsProductId,
                creditAccountType, paymentTypeId);
        final GLAccount clearingAccount = this.interBranchGlAccountReadService.resolveClearingAccount(servicingOffice.getId(),
                homeOffice.getId(), currencyCode);

        if (reversed) {
            this.accountingProcessorHelper.createDebitJournalEntryForSavings(homeOffice, currencyCode, creditAccount, savingsId,
                    transactionId, transactionDate, amount);
            this.accountingProcessorHelper.createCreditJournalEntryForSavings(homeOffice, currencyCode, clearingAccount, savingsId,
                    transactionId, transactionDate, amount);
            this.accountingProcessorHelper.createDebitJournalEntryForSavings(servicingOffice, currencyCode, clearingAccount, savingsId,
                    transactionId, transactionDate, amount);
            this.accountingProcessorHelper.createCreditJournalEntryForSavings(servicingOffice, currencyCode, debitAccount, savingsId,
                    transactionId, transactionDate, amount);
        } else {
            this.accountingProcessorHelper.createDebitJournalEntryForSavings(servicingOffice, currencyCode, debitAccount, savingsId,
                    transactionId, transactionDate, amount);
            this.accountingProcessorHelper.createCreditJournalEntryForSavings(servicingOffice, currencyCode, clearingAccount, savingsId,
                    transactionId, transactionDate, amount);
            this.accountingProcessorHelper.createDebitJournalEntryForSavings(homeOffice, currencyCode, clearingAccount, savingsId,
                    transactionId, transactionDate, amount);
            this.accountingProcessorHelper.createCreditJournalEntryForSavings(homeOffice, currencyCode, creditAccount, savingsId,
                    transactionId, transactionDate, amount);
        }
    }

    public void postLoanClearingBridge(final Office servicingOffice, final Office homeOffice, final String currencyCode,
            final Long loanId, final String transactionId, final LocalDate transactionDate, final BigDecimal amount,
            final boolean reversed) {

        final GLAccount clearingAccount = this.interBranchGlAccountReadService.resolveClearingAccount(servicingOffice.getId(),
                homeOffice.getId(), currencyCode);

        if (reversed) {
            this.accountingProcessorHelper.createDebitJournalEntryForLoan(homeOffice, currencyCode, clearingAccount, loanId, transactionId,
                    transactionDate, amount);
            this.accountingProcessorHelper.createCreditJournalEntryForLoan(servicingOffice, currencyCode, loanId, transactionId,
                    transactionDate, amount, clearingAccount);
        } else {
            this.accountingProcessorHelper.createCreditJournalEntryForLoan(servicingOffice, currencyCode, loanId, transactionId,
                    transactionDate, amount, clearingAccount);
            this.accountingProcessorHelper.createDebitJournalEntryForLoan(homeOffice, currencyCode, clearingAccount, loanId,
                    transactionId, transactionDate, amount);
        }
    }

    public void createCrossBranchRecoveryRepayment(final Office servicingOffice, final Office homeOffice, final String currencyCode,
            final int fundSourceAccountType, final int incomeAccountType, final Long loanProductId, final Long paymentTypeId,
            final Long loanId, final String transactionId, final LocalDate transactionDate, final BigDecimal amount) {

        this.accountingProcessorHelper.createCreditJournalEntryForLoan(homeOffice, currencyCode, incomeAccountType, loanProductId,
                paymentTypeId, loanId, transactionId, transactionDate, amount);
        this.accountingProcessorHelper.createDebitJournalEntryForLoan(servicingOffice, currencyCode, fundSourceAccountType, loanProductId,
                paymentTypeId, loanId, transactionId, transactionDate, amount);
        postLoanClearingBridge(servicingOffice, homeOffice, currencyCode, loanId, transactionId, transactionDate, amount, false);
    }

    public BigDecimal createCrossBranchClientChargePaymentCredits(final Office homeOffice, final String currencyCode, final Long clientId,
            final Long transactionId, final LocalDate transactionDate, final boolean isReversal,
            final java.util.List<ClientChargePaymentDTO> clientChargePaymentDTOs) {
        return this.accountingProcessorHelper.createCreditJournalEntryOrReversalForClientPayments(homeOffice, currencyCode, clientId,
                transactionId, transactionDate, isReversal, clientChargePaymentDTOs);
    }

    public void createCrossBranchClientChargePaymentFundSourceDebit(final Office servicingOffice, final String currencyCode,
            final Long clientId, final Long transactionId, final LocalDate transactionDate, final BigDecimal amount,
            final boolean isReversal) {
        this.accountingProcessorHelper.createDebitJournalEntryOrReversalForClientChargePayments(servicingOffice, currencyCode, clientId,
                transactionId, transactionDate, amount, isReversal);
    }

    public void postClientClearingBridge(final Office servicingOffice, final Office homeOffice, final String currencyCode,
            final Long clientId, final Long transactionId, final LocalDate transactionDate, final BigDecimal amount,
            final boolean reversed) {

        final GLAccount clearingAccount = this.interBranchGlAccountReadService.resolveClearingAccount(servicingOffice.getId(),
                homeOffice.getId(), currencyCode);

        if (reversed) {
            this.accountingProcessorHelper.createDebitJournalEntryForClientPayments(homeOffice, currencyCode, clearingAccount, clientId,
                    transactionId, transactionDate, amount);
            this.accountingProcessorHelper.createCreditJournalEntryForClientPayments(servicingOffice, currencyCode, clearingAccount,
                    clientId, transactionId, transactionDate, amount);
        } else {
            this.accountingProcessorHelper.createCreditJournalEntryForClientPayments(servicingOffice, currencyCode, clearingAccount,
                    clientId, transactionId, transactionDate, amount);
            this.accountingProcessorHelper.createDebitJournalEntryForClientPayments(homeOffice, currencyCode, clearingAccount, clientId,
                    transactionId, transactionDate, amount);
        }
    }

    public int liabilityTransferAccountType() {
        return FinancialActivity.LIABILITY_TRANSFER.getValue();
    }
}
