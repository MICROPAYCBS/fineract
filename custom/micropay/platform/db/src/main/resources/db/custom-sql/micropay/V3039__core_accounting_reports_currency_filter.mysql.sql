--
-- MICROPAY CBS: filter journal entries by currency on core accounting table reports (MySQL / MariaDB)
--

UPDATE stretchy_report
SET report_sql = REPLACE(
    report_sql,
    'and (acc_gl_journal_entry.office_id= ${officeId} or ${officeId}=1)',
    'and (acc_gl_journal_entry.office_id= ${officeId} or ${officeId}=1) and (acc_gl_journal_entry.currency_code = ''${currencyId}'' or ''-1'' = ''${currencyId}'')'
)
WHERE report_name IN ('Trial Balance Table', 'Balance Sheet Table', 'Income Statement Table')
  AND report_sql NOT LIKE '%acc_gl_journal_entry.currency_code%';

UPDATE stretchy_report
SET report_sql = REPLACE(
    report_sql,
    'and ( acc_gl_journal_entry.office_id = ${officeId} or ${officeId} = 1 )',
    'and ( acc_gl_journal_entry.office_id = ${officeId} or ${officeId} = 1 ) and ( acc_gl_journal_entry.currency_code = ''${currencyId}'' or ''-1'' = ''${currencyId}'' )'
)
WHERE report_name IN ('Trial Balance Table', 'Balance Sheet Table', 'Income Statement Table')
  AND report_sql NOT LIKE '%acc_gl_journal_entry.currency_code%';

UPDATE stretchy_report
SET report_sql = REPLACE(
    report_sql,
    'and (acc_gl_journal_entry.office_id=${officeId} or ${officeId}=1)',
    'and (acc_gl_journal_entry.office_id=${officeId} or ${officeId}=1) and (acc_gl_journal_entry.currency_code = ''${currencyId}'' or ''-1'' = ''${currencyId}'')'
)
WHERE report_name IN ('Trial Balance Table', 'Balance Sheet Table', 'Income Statement Table')
  AND report_sql NOT LIKE '%acc_gl_journal_entry.currency_code%';
