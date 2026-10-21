--
-- MICROPAY CBS: seed identity type rows for existing Customer Identifier code values (PostgreSQL)
--

INSERT INTO m_identity_type (code_value_id, example, format_description, validation_message, validation_regex, display_order, status,
    created_by, created_on_utc, last_modified_by, last_modified_on_utc, version)
SELECT cv.id, NULL, NULL, NULL, NULL, cv.order_position, 'ACTIVE', 1, NOW(), 1, NOW(), 1
FROM m_code_value cv
INNER JOIN m_code c ON cv.code_id = c.id
WHERE c.code_name = 'Customer Identifier'
  AND NOT EXISTS (SELECT 1 FROM m_identity_type it WHERE it.code_value_id = cv.id);

-- Optional starter rules for common document types when present
UPDATE m_identity_type it
SET example = 'AB1234567',
    format_description = '6 to 9 alphanumeric characters',
    validation_message = 'Passport number must be 6 to 9 letters and digits.',
    validation_regex = '^[A-Z0-9]{6,9}$'
FROM m_code_value cv
WHERE it.code_value_id = cv.id
  AND cv.code_value = 'Passport'
  AND it.validation_regex IS NULL;

UPDATE m_identity_type it
SET example = 'CM1234567890ABCD',
    format_description = 'Two letters followed by 14 letters or digits',
    validation_message = 'National ID must start with two letters followed by 14 letters or digits.',
    validation_regex = '^[A-Z]{2}[A-Z0-9]{14}$'
FROM m_code_value cv
WHERE it.code_value_id = cv.id
  AND cv.code_value = 'National ID'
  AND it.validation_regex IS NULL;

UPDATE m_identity_type it
SET example = 'DL-12345678',
    format_description = 'Two letters, a hyphen, then 6 to 12 letters or digits',
    validation_message = 'Driver''s license must match the format XX-12345678.',
    validation_regex = '^[A-Z]{2}-[A-Z0-9]{6,12}$'
FROM m_code_value cv
WHERE it.code_value_id = cv.id
  AND cv.code_value IN ('Drivers License', 'Driver''s License')
  AND it.validation_regex IS NULL;
