--
-- MICROPAY CBS: General Ledger table report -- Manual/System source + transaction_id (MariaDB / MySQL)
--

UPDATE stretchy_report
SET report_sql = '
WITH params AS (
    SELECT CAST(''${startDate}'' AS DATE) AS start_date,
           CAST(''${endDate}'' AS DATE) AS end_date,
           CAST(''${officeId}'' AS SIGNED) AS office_id,
           CAST(''${GLAccountNO}'' AS SIGNED) AS gl_account_id,
           ''${currencyId}'' AS currency_id,
           DATE_SUB(CAST(''${startDate}'' AS DATE), INTERVAL 1 DAY) AS as_of_date
),
snapshot_watermark AS (
    SELECT p.as_of_date,
           COALESCE(daily_baseline, monthly_baseline) AS baseline_date,
           CASE WHEN daily_baseline IS NOT NULL THEN ''DAILY'' ELSE ''MONTHLY'' END AS baseline_granularity
    FROM params p
    LEFT JOIN (
        SELECT p2.as_of_date,
            (SELECT MAX(s.snapshot_date)
             FROM m_gl_balance_snapshot s
             INNER JOIN m_office ounder ON s.office_id = ounder.id
             INNER JOIN m_office o ON o.id = p2.office_id AND ounder.hierarchy LIKE CONCAT(o.hierarchy, ''%'')
             WHERE s.snapshot_granularity = ''DAILY''
               AND s.snapshot_date <= p2.as_of_date
               AND s.snapshot_date >= DATE_SUB(p2.as_of_date, INTERVAL 90 DAY)
               AND s.gl_account_id = p2.gl_account_id
               AND (s.currency_code = p2.currency_id OR p2.currency_id = ''-1'')) AS daily_baseline,
            (SELECT MAX(s.snapshot_date)
             FROM m_gl_balance_snapshot s
             INNER JOIN m_office ounder ON s.office_id = ounder.id
             INNER JOIN m_office o ON o.id = p2.office_id AND ounder.hierarchy LIKE CONCAT(o.hierarchy, ''%'')
             WHERE s.snapshot_granularity = ''MONTHLY''
               AND s.snapshot_date <= p2.as_of_date
               AND s.gl_account_id = p2.gl_account_id
               AND (s.currency_code = p2.currency_id OR p2.currency_id = ''-1'')) AS monthly_baseline
        FROM params p2
    ) baselines ON baselines.as_of_date = p.as_of_date
),
opening_components AS (
    SELECT
        COALESCE(snap.net_balance, 0) AS snapshot_net,
        COALESCE(delta.net_balance, 0) AS delta_net
    FROM params p
    CROSS JOIN snapshot_watermark w
    LEFT JOIN LATERAL (
        SELECT SUM(s.closing_balance_foreign) AS net_balance
        FROM m_gl_balance_snapshot s
        INNER JOIN m_office ounder ON s.office_id = ounder.id
        INNER JOIN m_office o ON o.id = p.office_id AND ounder.hierarchy LIKE CONCAT(o.hierarchy, ''%'')
        WHERE w.baseline_date IS NOT NULL
          AND s.snapshot_date = w.baseline_date
          AND s.snapshot_granularity = w.baseline_granularity
          AND s.gl_account_id = p.gl_account_id
          AND (s.currency_code = p.currency_id OR p.currency_id = ''-1'')
    ) snap ON TRUE
    LEFT JOIN LATERAL (
        SELECT SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END) AS net_balance
        FROM acc_gl_journal_entry je
        INNER JOIN m_office ounder ON je.office_id = ounder.id
        INNER JOIN m_office o ON o.id = p.office_id AND ounder.hierarchy LIKE CONCAT(o.hierarchy, ''%'')
        WHERE je.account_id = p.gl_account_id
          AND je.entry_date <= p.as_of_date
          AND (w.baseline_date IS NULL OR je.entry_date > w.baseline_date)
          AND (je.currency_code = p.currency_id OR p.currency_id = ''-1'')
    ) delta ON TRUE
)
SELECT
  lines.entry_date,
  lines.debit_amount,
  lines.credit_amount,
  lines.description,
  COALESCE(ob.openingbalance, 0) AS openingbalance,
  IF(lines.manual_entry = 1, ''Manual'', ''System'') AS source,
  lines.transaction_id,
  COALESCE(ob.openingbalance, 0) + lines.running_movement AS cumulative_sum
FROM (
  SELECT
    je.entry_date AS entry_date,
    IF(je.type_enum = 1, je.amount, 0) AS debit_amount,
    IF(je.type_enum = 2, je.amount, 0) AS credit_amount,
    je.description AS description,
    je.manual_entry AS manual_entry,
    je.transaction_id AS transaction_id,
    je.id AS id,
    SUM(
      CASE
        WHEN gl.classification_enum IN (1, 5) THEN IF(je.type_enum = 1, je.amount, -je.amount)
        ELSE IF(je.type_enum = 2, je.amount, -je.amount)
      END
    ) OVER (ORDER BY je.entry_date, je.id ROWS UNBOUNDED PRECEDING) AS running_movement
  FROM acc_gl_journal_entry je
  INNER JOIN acc_gl_account gl ON gl.id = je.account_id
  INNER JOIN m_office ounder ON je.office_id = ounder.id
  INNER JOIN m_office o ON o.id = ${officeId} AND ounder.hierarchy LIKE CONCAT(o.hierarchy, ''%'')
  WHERE je.account_id = ${GLAccountNO}
    AND je.entry_date BETWEEN ''${startDate}'' AND ''${endDate}''
    AND (je.currency_code = ''${currencyId}'' OR ''${currencyId}'' = ''-1'')
) lines
LEFT JOIN (
  SELECT
    CASE
      WHEN gl.classification_enum IN (1, 5) THEN -(oc.snapshot_net + oc.delta_net)
      ELSE (oc.snapshot_net + oc.delta_net)
    END AS openingbalance
  FROM opening_components oc
  INNER JOIN acc_gl_account gl ON gl.id = (SELECT gl_account_id FROM params)
) ob ON 1 = 1
ORDER BY lines.entry_date, lines.id'
WHERE report_name = 'GeneralLedgerReport Table';
