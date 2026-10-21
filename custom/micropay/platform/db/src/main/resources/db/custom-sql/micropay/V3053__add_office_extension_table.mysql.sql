--
-- MICROPAY CBS: Branch profile extension for m_office (MySQL)
--

CREATE TABLE m_office_extension (
    office_id BIGINT PRIMARY KEY,
    office_code VARCHAR(10) UNIQUE,
    branch_type VARCHAR(30),
    region_code VARCHAR(20),
    address VARCHAR(255),
    city VARCHAR(50),
    country_code VARCHAR(10),
    phone_no VARCHAR(30),
    email_address VARCHAR(100),
    manager_staff_id BIGINT,
    swift_code VARCHAR(30),
    latitude VARCHAR(50),
    longitude VARCHAR(50),
    cash_limit DECIMAL(21, 6),
    working_hours VARCHAR(20),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

ALTER TABLE m_office_extension
    ADD CONSTRAINT FK_m_office_extension_office_id FOREIGN KEY (office_id) REFERENCES m_office (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_office_extension_manager_staff_id FOREIGN KEY (manager_staff_id) REFERENCES m_staff (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_office_extension_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_office_extension_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
