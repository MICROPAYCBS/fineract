--
-- MICROPAY CBS: Trial Balance Table — hybrid snapshot + delta (PostgreSQL)
--

UPDATE stretchy_report
SET report_sql = '
WITH params AS (
    SELECT CAST(''${startDate}'' AS DATE) AS start_date,
           CAST(''${endDate}'' AS DATE) AS end_date,
           CAST(''${officeId}'' AS BIGINT) AS office_id,
           CAST(''${departmentId}'' AS BIGINT) AS department_id,
           ''${currencyId}'' AS currency_id
),
snapshot_dates AS (
    SELECT end_date AS as_of_date FROM params
    UNION ALL
    SELECT (start_date - INTERVAL ''1 day'')::date FROM params
),
snapshot_watermarks AS (
    SELECT sd.as_of_date,
           COALESCE(daily_baseline, monthly_baseline) AS baseline_date,
           CASE WHEN daily_baseline IS NOT NULL THEN ''DAILY'' ELSE ''MONTHLY'' END AS baseline_granularity
    FROM snapshot_dates sd
    CROSS JOIN params p
    LEFT JOIN LATERAL (
        SELECT
            (SELECT MAX(s.snapshot_date)
             FROM m_gl_balance_snapshot s
             WHERE s.snapshot_granularity = ''DAILY''
               AND s.snapshot_date <= sd.as_of_date
               AND s.snapshot_date >= sd.as_of_date - INTERVAL ''90 days''
               AND (s.office_id = p.office_id OR p.office_id = 1)
               AND (s.department_id = p.department_id OR p.department_id = -1)
               AND (s.currency_code = p.currency_id OR p.currency_id = ''-1'')) AS daily_baseline,
            (SELECT MAX(s.snapshot_date)
             FROM m_gl_balance_snapshot s
             WHERE s.snapshot_granularity = ''MONTHLY''
               AND s.snapshot_date <= sd.as_of_date
               AND (s.office_id = p.office_id OR p.office_id = 1)
               AND (s.department_id = p.department_id OR p.department_id = -1)
               AND (s.currency_code = p.currency_id OR p.currency_id = ''-1'')) AS monthly_baseline
    ) baselines ON TRUE
),
balances AS (
    SELECT sw.as_of_date,
           aga.id AS gl_account_id,
           aga.gl_code AS glcode,
           aga.name AS name,
           aga.classification_enum AS classification,
           COALESCE(snap.net_balance, 0) + COALESCE(delta.net_balance, 0) AS signed_net
    FROM snapshot_watermarks sw
    JOIN (
        SELECT DISTINCT gl_account_id FROM m_gl_balance_snapshot
        UNION
        SELECT DISTINCT account_id FROM acc_gl_journal_entry
    ) accounts ON TRUE
    JOIN acc_gl_account aga ON aga.id = accounts.gl_account_id
    LEFT JOIN (
        SELECT sw2.as_of_date, s.gl_account_id, SUM(s.closing_balance_foreign) AS net_balance
        FROM snapshot_watermarks sw2
        JOIN m_gl_balance_snapshot s
          ON sw2.baseline_date IS NOT NULL
         AND s.snapshot_date = sw2.baseline_date
         AND s.snapshot_granularity = sw2.baseline_granularity
        CROSS JOIN params p
        WHERE (s.office_id = p.office_id OR p.office_id = 1)
          AND (s.department_id = p.department_id OR p.department_id = -1)
          AND (s.currency_code = p.currency_id OR p.currency_id = ''-1'')
        GROUP BY sw2.as_of_date, s.gl_account_id
    ) snap ON snap.as_of_date = sw.as_of_date AND snap.gl_account_id = aga.id
    LEFT JOIN (
        SELECT sw2.as_of_date, je.account_id AS gl_account_id,
               SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END) AS net_balance
        FROM snapshot_watermarks sw2
        JOIN acc_gl_journal_entry je
          ON je.entry_date <= sw2.as_of_date
         AND (sw2.baseline_date IS NULL OR je.entry_date > sw2.baseline_date)
        CROSS JOIN params p
        WHERE (je.office_id = p.office_id OR p.office_id = 1)
          AND (COALESCE(je.department_id, 0) = p.department_id OR p.department_id = -1)
          AND (je.currency_code = p.currency_id OR p.currency_id = ''-1'')
        GROUP BY sw2.as_of_date, je.account_id
    ) delta ON delta.as_of_date = sw.as_of_date AND delta.gl_account_id = aga.id
),
period AS (
    SELECT e.glcode, e.name, e.classification,
           e.signed_net - COALESCE(s.signed_net, 0) AS signed_period_net
    FROM balances e
    JOIN params p ON e.as_of_date = p.end_date
    LEFT JOIN balances s
      ON s.gl_account_id = e.gl_account_id
     AND s.as_of_date = (p.start_date - INTERVAL ''1 day'')::date
    WHERE e.signed_net - COALESCE(s.signed_net, 0) <> 0
)
SELECT glcode, name,
       CASE WHEN classification IN (1, 5) THEN signed_period_net ELSE NULL END AS debit,
       CASE WHEN classification IN (2, 3, 4) THEN -signed_period_net ELSE NULL END AS credit
FROM period
ORDER BY glcode
'
WHERE report_name = 'Trial Balance Table';
