--
-- MICROPAY CBS: remove legacy office servicing access matrix (PostgreSQL)
--

DELETE FROM m_role_permission
WHERE permission_id IN (SELECT id FROM m_permission WHERE entity_name = 'SERVICINGACCESS');

DELETE FROM m_permission WHERE entity_name = 'SERVICINGACCESS';

DROP TABLE IF EXISTS m_office_servicing_access;
