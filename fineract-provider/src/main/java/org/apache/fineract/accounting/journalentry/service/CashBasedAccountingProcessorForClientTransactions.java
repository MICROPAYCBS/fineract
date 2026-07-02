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
package org.apache.fineract.accounting.journalentry.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.closure.domain.GLClosure;
import org.apache.fineract.accounting.journalentry.data.ClientTransactionDTO;
import org.apache.fineract.infrastructure.interbranch.service.InterBranchAccountingHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CashBasedAccountingProcessorForClientTransactions implements AccountingProcessorForClientTransactions {

    private final AccountingProcessorHelper helper;
    private final InterBranchAccountingHelper interBranchAccountingHelper;

    @Override
    public void createJournalEntriesForClientTransaction(ClientTransactionDTO clientTransactionDTO) {
        if (clientTransactionDTO.isAccountingEnabled()) {
            final LocalDate transactionDate = clientTransactionDTO.getTransactionDate();
            final Long homeOfficeId = clientTransactionDTO.getOfficeId();
            final Long servicingOfficeId = clientTransactionDTO.getTransactionOfficeId();
            if (this.interBranchAccountingHelper.isCrossBranch(homeOfficeId, servicingOfficeId)) {
                this.interBranchAccountingHelper.validateBranchClosures(servicingOfficeId, homeOfficeId, transactionDate);
            } else {
                final GLClosure latestGLClosure = this.helper.getLatestClosureByBranch(homeOfficeId);
                this.helper.checkForBranchClosures(latestGLClosure, transactionDate);
            }

            /** Handle client payments **/
            if (clientTransactionDTO.isChargePayment()) {
                createJournalEntriesForChargePayments(clientTransactionDTO);
            }
        }
    }

    /**
     * Create a single debit to fund source and multiple credits for the income account mapped with each charge this
     * payment pays off
     *
     * In case the loan transaction is a reversal, all debits are turned into credits and vice versa
     */
    private void createJournalEntriesForChargePayments(final ClientTransactionDTO clientTransactionDTO) {
        // client properties
        final Long clientId = clientTransactionDTO.getClientId();
        final Long homeOfficeId = clientTransactionDTO.getOfficeId();
        final Long servicingOfficeId = clientTransactionDTO.getTransactionOfficeId();
        final boolean crossBranch = this.interBranchAccountingHelper.isCrossBranch(homeOfficeId, servicingOfficeId);
        final Office homeOffice = this.helper.getOfficeById(homeOfficeId);
        final Office servicingOffice = crossBranch ? this.helper.getOfficeById(servicingOfficeId) : homeOffice;

        // transaction properties
        final String currencyCode = clientTransactionDTO.getCurrencyCode();
        final Long transactionId = clientTransactionDTO.getTransactionId();
        final LocalDate transactionDate = clientTransactionDTO.getTransactionDate();
        final BigDecimal amount = clientTransactionDTO.getAmount();
        final boolean isReversal = clientTransactionDTO.isReversed();

        if (amount != null && !(amount.compareTo(BigDecimal.ZERO) == 0)) {
            if (crossBranch) {
                final BigDecimal totalCreditedAmount = this.interBranchAccountingHelper.createCrossBranchClientChargePaymentCredits(homeOffice,
                        currencyCode, clientId, transactionId, transactionDate, isReversal, clientTransactionDTO.getChargePayments());
                this.interBranchAccountingHelper.createCrossBranchClientChargePaymentFundSourceDebit(servicingOffice, currencyCode, clientId,
                        transactionId, transactionDate, totalCreditedAmount, isReversal);
                this.interBranchAccountingHelper.postClientClearingBridge(servicingOffice, homeOffice, currencyCode, clientId, transactionId,
                        transactionDate, totalCreditedAmount, isReversal);
            } else {
                BigDecimal totalCreditedAmount = this.helper.createCreditJournalEntryOrReversalForClientPayments(homeOffice, currencyCode,
                        clientId, transactionId, transactionDate, isReversal, clientTransactionDTO.getChargePayments());

                this.helper.createDebitJournalEntryOrReversalForClientChargePayments(homeOffice, currencyCode, clientId, transactionId,
                        transactionDate, totalCreditedAmount, isReversal);
            }
        }

    }

}
