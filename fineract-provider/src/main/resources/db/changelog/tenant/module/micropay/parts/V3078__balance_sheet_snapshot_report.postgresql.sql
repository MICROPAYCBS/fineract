--
-- MICROPAY CBS: Balance Sheet Table -- hybrid snapshot + delta (PostgreSQL)
--

UPDATE stretchy_report
SET report_sql = '
WITH params AS (
    SELECT CAST(''${endDate}'' AS DATE) AS end_date,
           CAST(''${officeId}'' AS BIGINT) AS office_id,
           ''${currencyId}'' AS currency_id
),
snapshot_watermark AS (
    SELECT COALESCE(daily_baseline, monthly_baseline) AS baseline_date,
           CASE WHEN daily_baseline IS NOT NULL THEN ''DAILY'' ELSE ''MONTHLY'' END AS baseline_granularity
    FROM (
        SELECT
            (SELECT MAX(s.snapshot_date)
             FROM m_gl_balance_snapshot s, params p
             WHERE s.snapshot_granularity = ''DAILY''
               AND s.snapshot_date <= p.end_date
               AND s.snapshot_date >= p.end_date - INTERVAL ''90 days''
               AND (s.office_id = p.office_id OR p.office_id = 1)
               AND (s.currency_code = p.currency_id OR p.currency_id = ''-1'')) AS daily_baseline,
            (SELECT MAX(s.snapshot_date)
             FROM m_gl_balance_snapshot s, params p
             WHERE s.snapshot_granularity = ''MONTHLY''
               AND s.snapshot_date <= p.end_date
               AND (s.office_id = p.office_id OR p.office_id = 1)
               AND (s.currency_code = p.currency_id OR p.currency_id = ''-1'')) AS monthly_baseline
    ) baselines
),
balances AS (
    SELECT aga.gl_code AS glcode,
           aga.name AS name,
           aga.classification_enum AS classification,
           COALESCE(snap.net_balance, 0) + COALESCE(delta.net_balance, 0) AS signed_net
    FROM acc_gl_account aga
    LEFT JOIN (
        SELECT s.gl_account_id, SUM(s.closing_balance_foreign) AS net_balance
        FROM m_gl_balance_snapshot s
        CROSS JOIN snapshot_watermark w
        CROSS JOIN params p
        WHERE w.baseline_date IS NOT NULL
          AND s.snapshot_date = w.baseline_date
          AND s.snapshot_granularity = w.baseline_granularity
          AND (s.office_id = p.office_id OR p.office_id = 1)
          AND (s.currency_code = p.currency_id OR p.currency_id = ''-1'')
        GROUP BY s.gl_account_id
    ) snap ON snap.gl_account_id = aga.id
    LEFT JOIN (
        SELECT je.account_id AS gl_account_id,
               SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END) AS net_balance
        FROM acc_gl_journal_entry je
        CROSS JOIN snapshot_watermark w
        CROSS JOIN params p
        WHERE je.entry_date <= p.end_date
          AND (w.baseline_date IS NULL OR je.entry_date > w.baseline_date)
          AND (je.office_id = p.office_id OR p.office_id = 1)
          AND (je.currency_code = p.currency_id OR p.currency_id = ''-1'')
        GROUP BY je.account_id
    ) delta ON delta.gl_account_id = aga.id
    WHERE aga.classification_enum IN (1, 2, 3)
      AND COALESCE(snap.net_balance, 0) + COALESCE(delta.net_balance, 0) <> 0
)
SELECT glcode,
       name,
       CASE
           WHEN classification = 1 THEN ''Assets''
           WHEN classification = 2 THEN ''Liability''
           ELSE ''Equity''
       END AS BalanceType,
       CASE WHEN classification IN (2, 3) THEN -signed_net ELSE signed_net END AS balance
FROM balances
ORDER BY glcode
'
WHERE report_name = 'Balance Sheet Table';
