--
-- MICROPAY CBS: migrate client gender/title to enum + m_client_title (MySQL / MariaDB)
--

UPDATE m_client c
JOIN m_code_value cv ON c.gender_cv_id = cv.id
JOIN m_code cd ON cd.id = cv.code_id AND cd.code_name = 'Gender'
SET c.gender_enum = 1
WHERE UPPER(cv.code_value) IN ('MALE', 'M');

UPDATE m_client c
JOIN m_code_value cv ON c.gender_cv_id = cv.id
JOIN m_code cd ON cd.id = cv.code_id AND cd.code_name = 'Gender'
SET c.gender_enum = 2
WHERE UPPER(cv.code_value) IN ('FEMALE', 'F');

UPDATE m_client c
JOIN m_code_value cv ON c.title_cv_id = cv.id
JOIN m_code cd ON cd.id = cv.code_id AND cd.code_name = 'ClientTitle'
JOIN m_client_title ct ON UPPER(ct.title_name) = UPPER(cv.code_value)
SET c.title_id = ct.id;
