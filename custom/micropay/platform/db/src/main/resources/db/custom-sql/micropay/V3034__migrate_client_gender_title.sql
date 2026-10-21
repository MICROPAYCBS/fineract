--
-- MICROPAY CBS: migrate client gender/title to enum + m_client_title (PostgreSQL)
--

UPDATE m_client c
SET gender_enum = 1
FROM m_code_value cv
JOIN m_code cd ON cd.id = cv.code_id AND cd.code_name = 'Gender'
WHERE c.gender_cv_id = cv.id
  AND UPPER(cv.code_value) IN ('MALE', 'M');

UPDATE m_client c
SET gender_enum = 2
FROM m_code_value cv
JOIN m_code cd ON cd.id = cv.code_id AND cd.code_name = 'Gender'
WHERE c.gender_cv_id = cv.id
  AND UPPER(cv.code_value) IN ('FEMALE', 'F');

UPDATE m_client c
SET title_id = ct.id
FROM m_code_value cv
JOIN m_code cd ON cd.id = cv.code_id AND cd.code_name = 'ClientTitle'
JOIN m_client_title ct ON UPPER(ct.title_name) = UPPER(cv.code_value)
WHERE c.title_cv_id = cv.id;
