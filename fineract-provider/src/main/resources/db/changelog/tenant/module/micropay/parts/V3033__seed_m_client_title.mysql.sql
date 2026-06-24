--
-- MICROPAY CBS: client title master data (MySQL / MariaDB)
--

INSERT INTO m_client_title (title_code, title_name, gender_enum, display_order, status, created_by, created_on_utc, last_modified_by, last_modified_on_utc, version)
SELECT 'MR', 'Mr', 1, 1, 'ACTIVE', 1, UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6), 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM m_client_title WHERE title_code = 'MR');

INSERT INTO m_client_title (title_code, title_name, gender_enum, display_order, status, created_by, created_on_utc, last_modified_by, last_modified_on_utc, version)
SELECT 'MRS', 'Mrs', 2, 2, 'ACTIVE', 1, UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6), 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM m_client_title WHERE title_code = 'MRS');

INSERT INTO m_client_title (title_code, title_name, gender_enum, display_order, status, created_by, created_on_utc, last_modified_by, last_modified_on_utc, version)
SELECT 'MS', 'Ms', NULL, 3, 'ACTIVE', 1, UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6), 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM m_client_title WHERE title_code = 'MS');

INSERT INTO m_client_title (title_code, title_name, gender_enum, display_order, status, created_by, created_on_utc, last_modified_by, last_modified_on_utc, version)
SELECT 'MISS', 'Miss', 2, 4, 'ACTIVE', 1, UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6), 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM m_client_title WHERE title_code = 'MISS');

INSERT INTO m_client_title (title_code, title_name, gender_enum, display_order, status, created_by, created_on_utc, last_modified_by, last_modified_on_utc, version)
SELECT 'DR', 'Doctor', NULL, 5, 'ACTIVE', 1, UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6), 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM m_client_title WHERE title_code = 'DR');

INSERT INTO m_client_title (title_code, title_name, gender_enum, display_order, status, created_by, created_on_utc, last_modified_by, last_modified_on_utc, version)
SELECT 'PROF', 'Professor', NULL, 6, 'ACTIVE', 1, UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6), 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM m_client_title WHERE title_code = 'PROF');

INSERT INTO m_client_title (title_code, title_name, gender_enum, display_order, status, created_by, created_on_utc, last_modified_by, last_modified_on_utc, version)
SELECT 'OTHER', 'Other', NULL, 7, 'ACTIVE', 1, UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6), 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM m_client_title WHERE title_code = 'OTHER');
