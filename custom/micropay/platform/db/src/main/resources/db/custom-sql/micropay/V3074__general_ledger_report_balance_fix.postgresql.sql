--
-- MICROPAY CBS: General Ledger table report — correct opening balance and running cumulative sum (PostgreSQL)
--

UPDATE stretchy_report
SET report_sql = 'SELECT
  lines.entry_date,
  lines.debit_amount,
  lines.credit_amount,
  lines.description,
  COALESCE(ob.openingbalance, 0) AS openingbalance,
  CASE WHEN lines.manual_entry THEN CAST(lines.id AS TEXT) ELSE ''system'' END AS transtype,
  COALESCE(ob.openingbalance, 0) + lines.running_movement AS cumulative_sum
FROM (
  SELECT
    je.entry_date AS entry_date,
    CASE WHEN je.type_enum = 1 THEN je.amount ELSE 0 END AS debit_amount,
    CASE WHEN je.type_enum = 2 THEN je.amount ELSE 0 END AS credit_amount,
    je.description AS description,
    je.manual_entry AS manual_entry,
    je.id AS id,
    SUM(
      CASE
        WHEN gl.classification_enum IN (1, 5) THEN CASE WHEN je.type_enum = 1 THEN je.amount ELSE -je.amount END
        ELSE CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END
      END
    ) OVER (ORDER BY je.entry_date, je.id ROWS UNBOUNDED PRECEDING) AS running_movement
  FROM acc_gl_journal_entry je
  INNER JOIN acc_gl_account gl ON gl.id = je.account_id
  INNER JOIN m_office ounder ON je.office_id = ounder.id
  INNER JOIN m_office o ON o.id = ${officeId} AND ounder.hierarchy LIKE CONCAT(o.hierarchy, ''%'')
  WHERE je.account_id = ${GLAccountNO}
    AND je.entry_date BETWEEN ''${startDate}'' AND ''${endDate}''
) lines
LEFT JOIN (
  SELECT
    CASE
      WHEN gl.classification_enum IN (1, 5) THEN
        SUM(CASE WHEN je.type_enum = 1 THEN je.amount ELSE 0 END) - SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE 0 END)
      ELSE
        SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE 0 END) - SUM(CASE WHEN je.type_enum = 1 THEN je.amount ELSE 0 END)
    END AS openingbalance
  FROM acc_gl_journal_entry je
  INNER JOIN acc_gl_account gl ON gl.id = je.account_id
  INNER JOIN m_office ounder ON je.office_id = ounder.id
  INNER JOIN m_office o ON o.id = ${officeId} AND ounder.hierarchy LIKE CONCAT(o.hierarchy, ''%'')
  WHERE je.account_id = ${GLAccountNO}
    AND je.entry_date < ''${startDate}''
  GROUP BY gl.classification_enum
) ob ON TRUE
ORDER BY lines.entry_date, lines.id'
WHERE report_name = 'GeneralLedgerReport Table';
