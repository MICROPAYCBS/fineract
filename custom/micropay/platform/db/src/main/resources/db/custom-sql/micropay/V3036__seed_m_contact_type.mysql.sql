--
-- MICROPAY CBS: contact type seed data (MySQL / MariaDB)
--

INSERT INTO m_contact_type (type_code, type_name, example, validation_regex, mandatory_ind, display_order, status, created_by, created_on_utc, last_modified_by, last_modified_on_utc, version)
SELECT 'MOBILE', 'Mobile', '+256712345678', '^\\+?[0-9]{10,15}$', 'Y', 1, 'ACTIVE', 1, NOW(6), 1, NOW(6), 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM m_contact_type WHERE type_code = 'MOBILE');

INSERT INTO m_contact_type (type_code, type_name, example, validation_regex, mandatory_ind, display_order, status, created_by, created_on_utc, last_modified_by, last_modified_on_utc, version)
SELECT 'EMAIL', 'Email', 'customer@example.com', '^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$', 'Y', 2, 'ACTIVE', 1, NOW(6), 1, NOW(6), 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM m_contact_type WHERE type_code = 'EMAIL');

INSERT INTO m_contact_type (type_code, type_name, example, validation_regex, mandatory_ind, display_order, status, created_by, created_on_utc, last_modified_by, last_modified_on_utc, version)
SELECT 'WHATSAPP', 'WhatsApp', '+256712345678', '^\\+?[0-9]{10,15}$', 'N', 3, 'ACTIVE', 1, NOW(6), 1, NOW(6), 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM m_contact_type WHERE type_code = 'WHATSAPP');
