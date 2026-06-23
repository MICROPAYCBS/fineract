--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements. See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership. The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License. You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations
-- under the License.
--

-- MICROPAY CBS: product accounting GL accounts and financial activity mappings — PostgreSQL
-- classification_enum: 1=Asset, 2=Liability, 3=Equity, 4=Income, 5=Expense
-- account_usage: 1=Detail

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT v.name, NULL, NULL, v.gl_code, false, true, 1, v.classification_enum, v.description
FROM (
    VALUES
        ('Fund Receivables', 'MP-10001', 1, 'Default fund source for loan disbursements and repayments; payment-channel fund source mappings; financial activity fundSource (103).'),
        ('Loans Receivable', 'MP-10002', 1, 'Outstanding principal balance of active loans (loan product loanPortfolioAccountId).'),
        ('Interest Receivable', 'MP-10003', 1, 'Accrued loan interest not yet collected (loan product receivableInterestAccountId; accrual accounting).'),
        ('Fees Receivable', 'MP-10004', 1, 'Accrued loan fees not yet collected (loan product receivableFeeAccountId; savings accrual feesReceivableAccountId).'),
        ('Penalties Receivable', 'MP-10005', 1, 'Accrued loan penalties not yet collected (loan product receivablePenaltyAccountId; savings accrual penaltiesReceivableAccountId).'),
        ('Loan Transfers in Suspense', 'MP-10006', 1, 'Holds loan account balances during portfolio transfers until reconciliation (loan product transfersInSuspenseAccountId).'),
        ('Savings Reference', 'MP-10007', 1, 'Contra asset for savings, fixed deposit, and recurring deposit portfolio balances (savingsReferenceAccountId).'),
        ('Overdraft Portfolio Control', 'MP-10008', 1, 'Tracks overdrawn savings balances when overdraft is enabled (overdraftPortfolioControlId).'),
        ('Asset Transfer', 'MP-10009', 1, 'Inter-branch and portfolio asset transfer clearing (financial activity assetTransfer 100).'),
        ('Cash at Main Vault', 'MP-10010', 1, 'Physical cash held in the institution main vault (financial activity cashAtMainVault 101).'),
        ('Cash at Teller', 'MP-10011', 1, 'Cash assigned to teller drawers and points of service (financial activity cashAtTeller 102).'),
        ('Shares Reference', 'MP-10012', 1, 'Contra asset for issued share capital balances (share product shareReferenceId).'),

        ('Savings Control', 'MP-20001', 2, 'Customer deposit liability for savings, fixed deposit, and recurring deposit products (savingsControlAccountId).'),
        ('Savings Transfers in Suspense', 'MP-20002', 2, 'Holds savings balances during portfolio transfers until reconciliation (savings transfersInSuspenseAccountId).'),
        ('Loan Overpayment', 'MP-20003', 2, 'Customer loan overpayments awaiting refund or reapplication (loan product overpaymentLiabilityAccountId).'),
        ('Liability Transfer Suspense', 'MP-20004', 2, 'Inter-branch and portfolio liability transfer clearing (financial activity liabilityTransfer 200).'),
        ('Interest Payable', 'MP-20005', 2, 'Accrued savings interest owed to customers but not yet posted (savings accrual interestPayableAccountId).'),
        ('Escheat Liability', 'MP-20006', 2, 'Unclaimed dormant savings balances transferred to the state (escheatLiabilityId when dormancy tracking is enabled).'),
        ('Shares Suspense', 'MP-20007', 2, 'Temporary liability for share subscriptions pending issuance (share product shareSuspenseId).'),
        ('Payable Dividends', 'MP-20008', 2, 'Declared share dividends owed to shareholders but not yet paid (financial activity payableDividends 201).'),
        ('Deferred Capitalized Income', 'MP-20009', 2, 'Deferred loan income for progressive loan capitalization features (deferredIncomeLiabilityAccountId).'),

        ('Share Capital', 'MP-30001', 3, 'Issued share equity and opening-balance migration contra (share product shareEquityId; financial activity openingBalancesTransferContra 300).'),

        ('Interest Income', 'MP-40001', 4, 'Interest earned on loans (loan product interestOnLoanAccountId).'),
        ('Fee Income', 'MP-40002', 4, 'Fee income from loans, savings, fixed deposits, recurring deposits, and shares (incomeFromFeeAccountId).'),
        ('Penalty Income', 'MP-40003', 4, 'Penalty income from loans and savings products (incomeFromPenaltyAccountId).'),
        ('Recoveries', 'MP-40004', 4, 'Amounts recovered on previously written-off loans (loan product incomeFromRecoveryAccountId).'),
        ('Income from Overdraft Interest', 'MP-40005', 4, 'Interest income on overdrawn savings accounts (savings incomeFromInterestId).'),
        ('Interest Income Charge Off', 'MP-40006', 4, 'Interest recognized when charged-off loans are recovered or reclassified (incomeFromChargeOffInterestAccountId).'),
        ('Fee Income Charge Off', 'MP-40007', 4, 'Fees recognized when charged-off loans are recovered or reclassified (incomeFromChargeOffFeesAccountId).'),
        ('Income from Buy Down', 'MP-40008', 4, 'Income from loan buy-down fee arrangements (incomeFromBuyDownAccountId).'),

        ('Savings Interest Expense', 'MP-50001', 5, 'Interest expense accrued and paid on savings, fixed deposit, and recurring deposit products (interestOnSavingsAccountId).'),
        ('Written Off', 'MP-50002', 5, 'Loan and savings principal written off as uncollectible (writeOffAccountId).'),
        ('Credit Loss / Bad Debt', 'MP-50003', 5, 'Expense for loan charge-offs and expected credit losses (chargeOffExpenseAccountId).'),
        ('Credit Loss / Bad Debt Fraud', 'MP-50004', 5, 'Expense for fraud-related loan charge-offs (chargeOffFraudExpenseAccountId).'),
        ('Goodwill Expense', 'MP-50005', 5, 'Goodwill credit adjustments on loan accounts (goodwillCreditAccountId).'),
        ('Buy Down Expense', 'MP-50006', 5, 'Expense for loan buy-down fee arrangements (buyDownExpenseAccountId).')
) AS v(name, gl_code, classification_enum, description)
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account a WHERE a.gl_code = v.gl_code);

INSERT INTO acc_gl_financial_activity_account (gl_account_id, financial_activity_type)
SELECT a.id, m.financial_activity_type
FROM (
    VALUES
        ('MP-10009', 100),
        ('MP-20004', 200),
        ('MP-10010', 101),
        ('MP-10011', 102),
        ('MP-10001', 103),
        ('MP-20008', 201),
        ('MP-30001', 300)
) AS m(gl_code, financial_activity_type)
JOIN acc_gl_account a ON a.gl_code = m.gl_code
WHERE NOT EXISTS (
    SELECT 1 FROM acc_gl_financial_activity_account f WHERE f.financial_activity_type = m.financial_activity_type
);
