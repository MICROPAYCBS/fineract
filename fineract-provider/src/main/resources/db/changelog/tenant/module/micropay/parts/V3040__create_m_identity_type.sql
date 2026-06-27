--
-- MICROPAY CBS: identity type validation rules linked to Customer Identifier code values (PostgreSQL)
--

CREATE TABLE IF NOT EXISTS m_identity_type (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code_value_id BIGINT NOT NULL UNIQUE,
    example VARCHAR(255),
    format_description VARCHAR(500),
    validation_message VARCHAR(500),
    validation_regex VARCHAR(500),
    display_order INT,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL,
    CONSTRAINT fk_m_identity_type_code_value FOREIGN KEY (code_value_id) REFERENCES m_code_value (id)
);

CREATE INDEX IF NOT EXISTS idx_m_identity_type_status ON m_identity_type (status);
