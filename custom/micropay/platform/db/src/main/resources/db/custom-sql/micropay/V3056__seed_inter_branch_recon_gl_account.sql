--
-- MICROPAY CBS: default inter-branch reconciliation GL account, financial activity mapping, and default rule (PostgreSQL)
-- classification_enum: 2=Liability; financial_activity_type 203=interBranchRecon

INSERT INTO acc_gl_account (name, parent_id, hierarchy, gl_code, disabled, manual_journal_entries_allowed, account_usage, classification_enum, description)
SELECT 'Inter-Branch Reconciliation', NULL, NULL, 'MP-20010', false, true, 1, 2,
       'Clearing account for cross-branch servicing settlements (financial activity interBranchRecon 203); default m_inter_branch_gl_rule.'
WHERE NOT EXISTS (SELECT 1 FROM acc_gl_account a WHERE a.gl_code = 'MP-20010');

INSERT INTO acc_gl_financial_activity_account (gl_account_id, financial_activity_type)
SELECT a.id, 203
FROM acc_gl_account a
WHERE a.gl_code = 'MP-20010'
  AND NOT EXISTS (SELECT 1 FROM acc_gl_financial_activity_account f WHERE f.financial_activity_type = 203);

INSERT INTO m_inter_branch_gl_rule (left_office_id, right_office_id, gl_account_id, currency_code, status, created_by, created_on_utc, last_modified_by, last_modified_on_utc, version)
SELECT NULL, NULL, a.id, NULL, 'ACTIVE', 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 1
FROM acc_gl_account a
WHERE a.gl_code = 'MP-20010'
  AND NOT EXISTS (SELECT 1 FROM m_inter_branch_gl_rule r WHERE r.left_office_id IS NULL AND r.right_office_id IS NULL);
