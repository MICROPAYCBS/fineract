--
-- MICROPAY CBS: Loan payments received (Active Loans) -- remove stray 9 before ${fromX}
--
UPDATE stretchy_report
SET report_sql = REPLACE(report_sql, 'BETWEEN 9${fromX}', 'BETWEEN ${fromX}')
WHERE report_name = 'Loan payments received (Active Loans)';
