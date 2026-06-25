--
-- MICROPAY CBS: client contact table (PostgreSQL)
--

CREATE TABLE IF NOT EXISTS m_client_contact (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id BIGINT NOT NULL,
    contact_type_id BIGINT NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    is_primary CHAR(1) DEFAULT 'N' NOT NULL,
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL,
    CONSTRAINT fk_client_contact_client FOREIGN KEY (client_id) REFERENCES m_client (id),
    CONSTRAINT fk_client_contact_type FOREIGN KEY (contact_type_id) REFERENCES m_contact_type (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_client_contact_primary_per_type
    ON m_client_contact (client_id, contact_type_id)
    WHERE is_primary = 'Y';
