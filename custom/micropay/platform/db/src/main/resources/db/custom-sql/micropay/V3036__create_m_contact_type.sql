--
-- MICROPAY CBS: contact type master table (PostgreSQL)
--

CREATE TABLE IF NOT EXISTS m_contact_type (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type_code VARCHAR(20) UNIQUE NOT NULL,
    type_name VARCHAR(100) NOT NULL,
    example VARCHAR(255),
    validation_regex VARCHAR(500),
    mandatory_ind CHAR(1) DEFAULT 'N' NOT NULL,
    display_order INT,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);
