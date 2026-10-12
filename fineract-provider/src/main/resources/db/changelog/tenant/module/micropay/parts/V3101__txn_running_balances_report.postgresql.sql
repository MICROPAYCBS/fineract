--
-- MICROPAY CBS: TxnRunningBalances -- PostgreSQL port of the MySQL report.
-- The opening row sums every loan in the office before the start date and does not apply the
-- loan-officer filter. Detail rows are the officer-filtered transactions inside the date window,
-- and their running totals continue from that opening balance (same order: transaction date, id).
--
UPDATE stretchy_report
SET report_sql = 'WITH params AS (
    SELECT CAST(''${startDate}'' AS DATE) AS start_date,
           CAST(''${endDate}'' AS DATE) AS end_date
),
opening AS (
    SELECT COALESCE(ROUND(CAST(SUM(CASE WHEN txn.transaction_type_enum = 1 THEN COALESCE(txn.amount, 0)
                                        ELSE -COALESCE(txn.principal_portion_derived, 0) END) AS NUMERIC), 2), 0) AS outstanding_principal,
           COALESCE(ROUND(CAST(SUM(CASE WHEN txn.transaction_type_enum IN (2, 5, 8) THEN COALESCE(txn.interest_portion_derived, 0)
                                        ELSE 0 END) AS NUMERIC), 2), 0) AS interest_income,
           COALESCE(ROUND(CAST(SUM(CASE WHEN txn.transaction_type_enum = 6 THEN COALESCE(txn.principal_portion_derived, 0)
                                        ELSE 0 END) AS NUMERIC), 2), 0) AS principal_write_off
    FROM m_office o
    JOIN m_office ounder ON ounder.hierarchy LIKE CONCAT(o.hierarchy, ''%'')
                        AND ounder.hierarchy LIKE CONCAT(''${currentUserHierarchy}'', ''%'')
    JOIN m_client c ON c.office_id = ounder.id
    JOIN m_loan l ON l.client_id = c.id
    JOIN m_product_loan lp ON lp.id = l.product_id
    JOIN m_loan_transaction txn ON txn.loan_id = l.id
    CROSS JOIN params p
    WHERE o.id = ${officeId}
      AND txn.is_reversed = false
      AND txn.transaction_type_enum NOT IN (10, 11)
      AND txn.transaction_date < p.start_date
),
detail AS (
    SELECT txn.id AS txn_id,
           txn.transaction_date AS txn_date,
           txn.transaction_type_enum AS txn_type,
           COALESCE(re.enum_message_property, CONCAT(''Unknown Transaction Type Value: '', txn.transaction_type_enum)) AS txn_type_name,
           ounder.name AS office_name,
           lo.display_name AS loan_officer_name,
           l.account_no AS loan_account_no,
           lp.name AS loan_product_name,
           COALESCE(cur.display_symbol, l.currency_code) AS currency,
           c.account_no AS client_account_no,
           c.display_name AS client_name,
           COALESCE(txn.amount, 0) AS amount,
           COALESCE(txn.principal_portion_derived, 0) AS principal,
           COALESCE(txn.interest_portion_derived, 0) AS interest
    FROM m_office o
    JOIN m_office ounder ON ounder.hierarchy LIKE CONCAT(o.hierarchy, ''%'')
                        AND ounder.hierarchy LIKE CONCAT(''${currentUserHierarchy}'', ''%'')
    JOIN m_client c ON c.office_id = ounder.id
    JOIN m_loan l ON l.client_id = c.id
    LEFT JOIN m_staff lo ON lo.id = l.loan_officer_id
    JOIN m_product_loan lp ON lp.id = l.product_id
    JOIN m_loan_transaction txn ON txn.loan_id = l.id
    LEFT JOIN m_currency cur ON cur.code = l.currency_code
    LEFT JOIN r_enum_value re ON re.enum_name = ''transaction_type_enum'' AND re.enum_id = txn.transaction_type_enum
    CROSS JOIN params p
    WHERE o.id = ${officeId}
      AND txn.is_reversed = false
      AND txn.transaction_type_enum NOT IN (10, 11)
      AND (COALESCE(l.loan_officer_id, -10) = ${loanOfficerId} OR -1 = ${loanOfficerId})
      AND txn.transaction_date >= p.start_date
      AND txn.transaction_date <= p.end_date
),
running AS (
    SELECT d.*,
           SUM(CASE WHEN d.txn_type = 1 THEN d.amount ELSE -d.principal END)
               OVER (ORDER BY d.txn_date, d.txn_id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS delta_principal,
           SUM(CASE WHEN d.txn_type IN (2, 5, 8) THEN d.interest ELSE 0 END)
               OVER (ORDER BY d.txn_date, d.txn_id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS delta_interest,
           SUM(CASE WHEN d.txn_type = 6 THEN d.principal ELSE 0 END)
               OVER (ORDER BY d.txn_date, d.txn_id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS delta_write_off
    FROM detail d
)
SELECT x.txn_date AS "Transaction Date",
       x.txn_type_name AS "Transaction Type",
       x.office_name AS "Office",
       x.loan_officer_name AS "Loan Officer",
       x.loan_account_no AS "Loan Account No",
       x.loan_product_name AS "Loan Product",
       x.currency AS "Currency",
       x.client_account_no AS "Client Account No",
       x.client_name AS "Client",
       x.amount AS "Amount",
       x.principal AS "Principal",
       x.interest AS "Interest",
       ROUND(CAST(x.outstanding_principal AS NUMERIC), 2) AS "Outstanding Principal",
       ROUND(CAST(x.interest_income AS NUMERIC), 2) AS "Interest Income",
       ROUND(CAST(x.principal_write_off AS NUMERIC), 2) AS "Principal Write Off"
FROM (
    SELECT 0 AS row_group, 0 AS txn_id, p.start_date AS txn_date, ''Opening Balance'' AS txn_type_name,
           NULL AS office_name, NULL AS loan_officer_name, NULL AS loan_account_no, NULL AS loan_product_name,
           NULL AS currency, NULL AS client_account_no, NULL AS client_name,
           NULL AS amount, NULL AS principal, NULL AS interest,
           o.outstanding_principal, o.interest_income, o.principal_write_off
    FROM params p
    CROSS JOIN opening o
    UNION ALL
    SELECT 1, r.txn_id, r.txn_date, r.txn_type_name,
           r.office_name, r.loan_officer_name, r.loan_account_no, r.loan_product_name,
           r.currency, r.client_account_no, r.client_name,
           r.amount, r.principal, r.interest,
           o.outstanding_principal + r.delta_principal,
           o.interest_income + r.delta_interest,
           o.principal_write_off + r.delta_write_off
    FROM running r
    CROSS JOIN opening o
) x
ORDER BY x.row_group, x.txn_date, x.txn_id'
WHERE report_name = 'TxnRunningBalances';
