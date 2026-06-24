--
-- MICROPAY CBS: client title master table (PostgreSQL)
--

CREATE TABLE IF NOT EXISTS m_client_title (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title_code VARCHAR(20) UNIQUE NOT NULL,
    title_name VARCHAR(100) NOT NULL,
    gender_enum INT,
    display_order INT,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);
