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

-- MICROPAY CBS: product accounting GL accounts and financial activity mappings — MySQL / MariaDB
-- classification_enum: 1=Asset, 2=Liability, 3=Equity, 4=Income, 5=Expense
-- account_usage: 1=Detail

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Fund Receivables', NULL, NULL, 'MP-10001', 0, 1, 1, 1, 'Default fund source for loan disbursements and repayments; payment-channel fund source mappings; financial activity fundSource (103).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-10001');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Loans Receivable', NULL, NULL, 'MP-10002', 0, 1, 1, 1, 'Outstanding principal balance of active loans (loan product loanPortfolioAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-10002');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Interest Receivable', NULL, NULL, 'MP-10003', 0, 1, 1, 1, 'Accrued loan interest not yet collected (loan product receivableInterestAccountId; accrual accounting).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-10003');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Fees Receivable', NULL, NULL, 'MP-10004', 0, 1, 1, 1, 'Accrued loan fees not yet collected (loan product receivableFeeAccountId; savings accrual feesReceivableAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-10004');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Penalties Receivable', NULL, NULL, 'MP-10005', 0, 1, 1, 1, 'Accrued loan penalties not yet collected (loan product receivablePenaltyAccountId; savings accrual penaltiesReceivableAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-10005');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Loan Transfers in Suspense', NULL, NULL, 'MP-10006', 0, 1, 1, 1, 'Holds loan account balances during portfolio transfers until reconciliation (loan product transfersInSuspenseAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-10006');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Savings Reference', NULL, NULL, 'MP-10007', 0, 1, 1, 1, 'Contra asset for savings, fixed deposit, and recurring deposit portfolio balances (savingsReferenceAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-10007');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Overdraft Portfolio Control', NULL, NULL, 'MP-10008', 0, 1, 1, 1, 'Tracks overdrawn savings balances when overdraft is enabled (overdraftPortfolioControlId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-10008');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Asset Transfer', NULL, NULL, 'MP-10009', 0, 1, 1, 1, 'Inter-branch and portfolio asset transfer clearing (financial activity assetTransfer 100).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-10009');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Cash at Main Vault', NULL, NULL, 'MP-10010', 0, 1, 1, 1, 'Physical cash held in the institution main vault (financial activity cashAtMainVault 101).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-10010');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Cash at Teller', NULL, NULL, 'MP-10011', 0, 1, 1, 1, 'Cash assigned to teller drawers and points of service (financial activity cashAtTeller 102).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-10011');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Shares Reference', NULL, NULL, 'MP-10012', 0, 1, 1, 1, 'Contra asset for issued share capital balances (share product shareReferenceId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-10012');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Savings Control', NULL, NULL, 'MP-20001', 0, 1, 1, 2, 'Customer deposit liability for savings, fixed deposit, and recurring deposit products (savingsControlAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-20001');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Savings Transfers in Suspense', NULL, NULL, 'MP-20002', 0, 1, 1, 2, 'Holds savings balances during portfolio transfers until reconciliation (savings transfersInSuspenseAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-20002');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Loan Overpayment', NULL, NULL, 'MP-20003', 0, 1, 1, 2, 'Customer loan overpayments awaiting refund or reapplication (loan product overpaymentLiabilityAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-20003');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Liability Transfer Suspense', NULL, NULL, 'MP-20004', 0, 1, 1, 2, 'Inter-branch and portfolio liability transfer clearing (financial activity liabilityTransfer 200).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-20004');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Interest Payable', NULL, NULL, 'MP-20005', 0, 1, 1, 2, 'Accrued savings interest owed to customers but not yet posted (savings accrual interestPayableAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-20005');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Escheat Liability', NULL, NULL, 'MP-20006', 0, 1, 1, 2, 'Unclaimed dormant savings balances transferred to the state (escheatLiabilityId when dormancy tracking is enabled).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-20006');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Shares Suspense', NULL, NULL, 'MP-20007', 0, 1, 1, 2, 'Temporary liability for share subscriptions pending issuance (share product shareSuspenseId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-20007');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Payable Dividends', NULL, NULL, 'MP-20008', 0, 1, 1, 2, 'Declared share dividends owed to shareholders but not yet paid (financial activity payableDividends 201).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-20008');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Deferred Capitalized Income', NULL, NULL, 'MP-20009', 0, 1, 1, 2, 'Deferred loan income for progressive loan capitalization features (deferredIncomeLiabilityAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-20009');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Share Capital', NULL, NULL, 'MP-30001', 0, 1, 1, 3, 'Issued share equity and opening-balance migration contra (share product shareEquityId; financial activity openingBalancesTransferContra 300).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-30001');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Interest Income', NULL, NULL, 'MP-40001', 0, 1, 1, 4, 'Interest earned on loans (loan product interestOnLoanAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-40001');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Fee Income', NULL, NULL, 'MP-40002', 0, 1, 1, 4, 'Fee income from loans, savings, fixed deposits, recurring deposits, and shares (incomeFromFeeAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-40002');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Penalty Income', NULL, NULL, 'MP-40003', 0, 1, 1, 4, 'Penalty income from loans and savings products (incomeFromPenaltyAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-40003');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Recoveries', NULL, NULL, 'MP-40004', 0, 1, 1, 4, 'Amounts recovered on previously written-off loans (loan product incomeFromRecoveryAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-40004');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Income from Overdraft Interest', NULL, NULL, 'MP-40005', 0, 1, 1, 4, 'Interest income on overdrawn savings accounts (savings incomeFromInterestId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-40005');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Interest Income Charge Off', NULL, NULL, 'MP-40006', 0, 1, 1, 4, 'Interest recognized when charged-off loans are recovered or reclassified (incomeFromChargeOffInterestAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-40006');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Fee Income Charge Off', NULL, NULL, 'MP-40007', 0, 1, 1, 4, 'Fees recognized when charged-off loans are recovered or reclassified (incomeFromChargeOffFeesAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-40007');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Income from Buy Down', NULL, NULL, 'MP-40008', 0, 1, 1, 4, 'Income from loan buy-down fee arrangements (incomeFromBuyDownAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-40008');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Savings Interest Expense', NULL, NULL, 'MP-50001', 0, 1, 1, 5, 'Interest expense accrued and paid on savings, fixed deposit, and recurring deposit products (interestOnSavingsAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-50001');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Written Off', NULL, NULL, 'MP-50002', 0, 1, 1, 5, 'Loan and savings principal written off as uncollectible (writeOffAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-50002');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Credit Loss / Bad Debt', NULL, NULL, 'MP-50003', 0, 1, 1, 5, 'Expense for loan charge-offs and expected credit losses (chargeOffExpenseAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-50003');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Credit Loss / Bad Debt Fraud', NULL, NULL, 'MP-50004', 0, 1, 1, 5, 'Expense for fraud-related loan charge-offs (chargeOffFraudExpenseAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-50004');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Goodwill Expense', NULL, NULL, 'MP-50005', 0, 1, 1, 5, 'Goodwill credit adjustments on loan accounts (goodwillCreditAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-50005');

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Buy Down Expense', NULL, NULL, 'MP-50006', 0, 1, 1, 5, 'Expense for loan buy-down fee arrangements (buyDownExpenseAccountId).'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account WHERE gl_code = 'MP-50006');

INSERT INTO acc_gl_financial_activity_account (gl_account_id, financial_activity_type)
SELECT a.id, 100 FROM acc_gl_account a WHERE a.gl_code = 'MP-10009'
AND NOT EXISTS (SELECT 1 FROM acc_gl_financial_activity_account f WHERE f.financial_activity_type = 100);

INSERT INTO acc_gl_financial_activity_account (gl_account_id, financial_activity_type)
SELECT a.id, 200 FROM acc_gl_account a WHERE a.gl_code = 'MP-20004'
AND NOT EXISTS (SELECT 1 FROM acc_gl_financial_activity_account f WHERE f.financial_activity_type = 200);

INSERT INTO acc_gl_financial_activity_account (gl_account_id, financial_activity_type)
SELECT a.id, 101 FROM acc_gl_account a WHERE a.gl_code = 'MP-10010'
AND NOT EXISTS (SELECT 1 FROM acc_gl_financial_activity_account f WHERE f.financial_activity_type = 101);

INSERT INTO acc_gl_financial_activity_account (gl_account_id, financial_activity_type)
SELECT a.id, 102 FROM acc_gl_account a WHERE a.gl_code = 'MP-10011'
AND NOT EXISTS (SELECT 1 FROM acc_gl_financial_activity_account f WHERE f.financial_activity_type = 102);

INSERT INTO acc_gl_financial_activity_account (gl_account_id, financial_activity_type)
SELECT a.id, 103 FROM acc_gl_account a WHERE a.gl_code = 'MP-10001'
AND NOT EXISTS (SELECT 1 FROM acc_gl_financial_activity_account f WHERE f.financial_activity_type = 103);

INSERT INTO acc_gl_financial_activity_account (gl_account_id, financial_activity_type)
SELECT a.id, 201 FROM acc_gl_account a WHERE a.gl_code = 'MP-20008'
AND NOT EXISTS (SELECT 1 FROM acc_gl_financial_activity_account f WHERE f.financial_activity_type = 201);

INSERT INTO acc_gl_financial_activity_account (gl_account_id, financial_activity_type)
SELECT a.id, 300 FROM acc_gl_account a WHERE a.gl_code = 'MP-30001'
AND NOT EXISTS (SELECT 1 FROM acc_gl_financial_activity_account f WHERE f.financial_activity_type = 300);
