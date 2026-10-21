--
-- MICROPAY CBS: filter journal entries by department on Income Statement and Trial Balance table reports (PostgreSQL)
--

UPDATE stretchy_report
SET report_sql = REPLACE(
    report_sql,
    'and ( acc_gl_journal_entry.currency_code = ''${currencyId}'' or ''-1'' = ''${currencyId}'' )',
    'and ( acc_gl_journal_entry.currency_code = ''${currencyId}'' or ''-1'' = ''${currencyId}'' ) and ( acc_gl_journal_entry.department_id = ${departmentId} or ''-1'' = ''${departmentId}'' )'
)
WHERE report_name IN ('Trial Balance Table', 'Income Statement Table')
  AND report_sql NOT LIKE '%acc_gl_journal_entry.department_id%';

UPDATE stretchy_report
SET report_sql = REPLACE(
    report_sql,
    'and (acc_gl_journal_entry.currency_code = ''${currencyId}'' or ''-1'' = ''${currencyId}'')',
    'and (acc_gl_journal_entry.currency_code = ''${currencyId}'' or ''-1'' = ''${currencyId}'') and (acc_gl_journal_entry.department_id = ${departmentId} or ''-1'' = ''${departmentId}'' )'
)
WHERE report_name IN ('Trial Balance Table', 'Income Statement Table')
  AND report_sql NOT LIKE '%acc_gl_journal_entry.department_id%';

UPDATE stretchy_report
SET report_sql = REPLACE(
    report_sql,
    'and ( acc_gl_journal_entry.office_id = ''${officeId}'' or ''${officeId}'' = 1 )',
    'and ( acc_gl_journal_entry.office_id = ''${officeId}'' or ''${officeId}'' = 1 ) and ( acc_gl_journal_entry.department_id = ${departmentId} or ''-1'' = ''${departmentId}'' )'
)
WHERE report_name IN ('Trial Balance Table', 'Income Statement Table')
  AND report_sql NOT LIKE '%acc_gl_journal_entry.department_id%'
  AND report_sql NOT LIKE '%acc_gl_journal_entry.currency_code%';

UPDATE stretchy_report
SET report_sql = REPLACE(
    report_sql,
    'and ( acc_gl_journal_entry.office_id = ${officeId} or ${officeId} = 1 )',
    'and ( acc_gl_journal_entry.office_id = ${officeId} or ${officeId} = 1 ) and ( acc_gl_journal_entry.department_id = ${departmentId} or ''-1'' = ''${departmentId}'' )'
)
WHERE report_name IN ('Trial Balance Table', 'Income Statement Table')
  AND report_sql NOT LIKE '%acc_gl_journal_entry.department_id%'
  AND report_sql NOT LIKE '%acc_gl_journal_entry.currency_code%';

UPDATE stretchy_report
SET report_sql = REPLACE(
    report_sql,
    'and (acc_gl_journal_entry.office_id= ${officeId} or ${officeId}=1)',
    'and (acc_gl_journal_entry.office_id= ${officeId} or ${officeId}=1) and (acc_gl_journal_entry.department_id = ${departmentId} or ''-1'' = ''${departmentId}'' )'
)
WHERE report_name IN ('Trial Balance Table', 'Income Statement Table')
  AND report_sql NOT LIKE '%acc_gl_journal_entry.department_id%'
  AND report_sql NOT LIKE '%acc_gl_journal_entry.currency_code%';
