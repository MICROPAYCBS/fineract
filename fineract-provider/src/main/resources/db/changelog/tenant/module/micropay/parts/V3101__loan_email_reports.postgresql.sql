--
-- MICROPAY CBS: loan email reports -- remove the backslash-escaped empty string that PostgreSQL rejects
--
UPDATE stretchy_report
SET report_sql = REPLACE(report_sql, '(\''\'')', '''''')
WHERE report_name IN ('Loan Approved - Email', 'Loan Rejected - Email', 'Loan Repayment - Email');
