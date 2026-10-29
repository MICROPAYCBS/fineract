/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.CROSS_CURRENCY_MESSAGE;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.glaccount.exception.GLAccountNotFoundException;
import org.apache.fineract.organisation.monetary.domain.OrganisationCurrencyRepository;
import org.apache.fineract.portfolio.savings.DepositAccountType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.apache.fineract.portfolio.savings.exception.SavingsAccountNotFoundException;
import org.apache.fineract.transactionbatch.domain.TransactionBatchSide;
import org.apache.fineract.transactionbatch.exception.TransactionBatchRuleException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchAccountRules {

    private final GLAccountRepository glAccountRepository;
    private final OrganisationCurrencyRepository currencyRepository;
    private final SavingsAccountRepository savingsAccountRepository;

    public GLAccount requirePostableGl(final Long glAccountId) {
        if (glAccountId == null) {
            throw new TransactionBatchRuleException("gl.required", "A GL account is required");
        }
        final GLAccount glAccount = this.glAccountRepository.findById(glAccountId)
                .orElseThrow(() -> new GLAccountNotFoundException(glAccountId));
        if (Boolean.TRUE.equals(glAccount.isDisabled()) || glAccount.isHeaderAccount() || !glAccount.isManualEntriesAllowed()) {
            throw new TransactionBatchRuleException("gl.not.postable",
                    "GL account " + glAccount.getGlCode() + " must be an enabled detail account that allows manual entries", glAccountId);
        }
        return glAccount;
    }

    public void requireOrganisationCurrency(final String currencyCode) {
        if (currencyCode == null || this.currencyRepository.findOneByCode(currencyCode) == null) {
            throw new TransactionBatchRuleException("currency.not.organisation", currencyCode + " is not an organisation currency",
                    currencyCode);
        }
    }

    public SavingsAccount requireSavings(final Long savingsAccountId, final TransactionBatchSide side, final String currencyCode) {
        if (savingsAccountId == null) {
            throw new TransactionBatchRuleException("savings.required", "A customer account is required");
        }
        final SavingsAccount account = this.savingsAccountRepository.findById(savingsAccountId)
                .orElseThrow(() -> new SavingsAccountNotFoundException(savingsAccountId));
        if (!account.isActive()) {
            throw new TransactionBatchRuleException("savings.not.active", "The savings account is not active", savingsAccountId);
        }
        if (account.depositAccountType() != DepositAccountType.SAVINGS_DEPOSIT) {
            throw new TransactionBatchRuleException("savings.not.deposit",
                    "Customer lines accept savings deposit accounts only. Loans and term deposits are not allowed", savingsAccountId);
        }
        if (!account.savingsProduct().isCashBasedAccountingEnabled() && !account.savingsProduct().isAccrualBasedAccountingEnabled()) {
            throw new TransactionBatchRuleException("savings.accounting",
                    "GL to savings and savings to GL require cash or accrual accounting on the savings product", savingsAccountId);
        }
        account.validateForAccountBlock();
        if (side.isDebit()) {
            account.validateForDebitBlock();
        } else {
            account.validateForCreditBlock();
        }
        if (!account.getCurrency().getCode().equals(currencyCode)) {
            throw new TransactionBatchRuleException("cross.currency", CROSS_CURRENCY_MESSAGE, currencyCode,
                    account.getCurrency().getCode());
        }
        return account;
    }
}
