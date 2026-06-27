--
-- MICROPAY CBS: seed identity type rows for existing Customer Identifier code values (MySQL / MariaDB)
--

INSERT INTO m_identity_type (code_value_id, example, format_description, validation_message, validation_regex, display_order, status,
    created_by, created_on_utc, last_modified_by, last_modified_on_utc, version)
SELECT cv.id, NULL, NULL, NULL, NULL, cv.order_position, 'ACTIVE', 1, NOW(6), 1, NOW(6), 1
FROM m_code_value cv
INNER JOIN m_code c ON cv.code_id = c.id
WHERE c.code_name = 'Customer Identifier'
  AND NOT EXISTS (SELECT 1 FROM m_identity_type it WHERE it.code_value_id = cv.id);

UPDATE m_identity_type it
INNER JOIN m_code_value cv ON it.code_value_id = cv.id
SET it.example = 'AB1234567',
    it.format_description = '6 to 9 alphanumeric characters',
    it.validation_message = 'Passport number must be 6 to 9 letters and digits.',
    it.validation_regex = '^[A-Z0-9]{6,9}$'
WHERE cv.code_value = 'Passport'
  AND it.validation_regex IS NULL;

UPDATE m_identity_type it
INNER JOIN m_code_value cv ON it.code_value_id = cv.id
SET it.example = 'CM1234567890ABCD',
    it.format_description = 'Two letters followed by 14 letters or digits',
    it.validation_message = 'National ID must start with two letters followed by 14 letters or digits.',
    it.validation_regex = '^[A-Z]{2}[A-Z0-9]{14}$'
WHERE cv.code_value = 'National ID'
  AND it.validation_regex IS NULL;

UPDATE m_identity_type it
INNER JOIN m_code_value cv ON it.code_value_id = cv.id
SET it.example = 'DL-12345678',
    it.format_description = 'Two letters, a hyphen, then 6 to 12 letters or digits',
    it.validation_message = 'Driver''s license must match the format XX-12345678.',
    it.validation_regex = '^[A-Z]{2}-[A-Z0-9]{6,12}$'
WHERE cv.code_value IN ('Drivers License', 'Driver''s License')
  AND it.validation_regex IS NULL;
