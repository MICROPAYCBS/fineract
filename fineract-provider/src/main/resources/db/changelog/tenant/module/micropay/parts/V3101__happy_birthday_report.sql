--
-- MICROPAY CBS: Happy Birthday SMS report -- match day and month, not the full date; works on PostgreSQL and MySQL
--
UPDATE stretchy_report
SET report_sql = 'SELECT c.id AS "id",
       c.firstname AS "firstName",
       c.middlename AS "middleName",
       c.lastname AS "lastName",
       c.display_name AS "fullName",
       c.mobile_no AS "mobileNo",
       CONCAT(REPEAT(''..'', (LENGTH(ounder.hierarchy) - LENGTH(REPLACE(ounder.hierarchy, ''.'', '''')) - 1)), ounder.name) AS "officeName",
       o.id AS "officeNumber",
       c.date_of_birth AS "dateOfBirth",
       EXTRACT(YEAR FROM CURRENT_DATE) - EXTRACT(YEAR FROM c.date_of_birth) AS "age"
FROM m_office o
JOIN m_office ounder ON ounder.hierarchy LIKE CONCAT(o.hierarchy, ''%'')
JOIN m_client c ON c.office_id = ounder.id
WHERE o.id = ${officeId}
  AND c.status_enum = 300
  AND (COALESCE(c.staff_id, -10) = ${loanOfficerId} OR -1 = ${loanOfficerId})
  AND c.date_of_birth IS NOT NULL
  AND EXTRACT(MONTH FROM c.date_of_birth) = EXTRACT(MONTH FROM CURRENT_DATE)
  AND EXTRACT(DAY FROM c.date_of_birth) = EXTRACT(DAY FROM CURRENT_DATE)
ORDER BY ounder.hierarchy, c.account_no'
WHERE report_name = 'Happy Birthday';
