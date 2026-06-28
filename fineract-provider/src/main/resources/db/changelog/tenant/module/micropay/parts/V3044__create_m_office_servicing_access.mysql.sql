--
-- MICROPAY CBS: office servicing access matrix (MySQL / MariaDB)
--

CREATE TABLE IF NOT EXISTS m_office_servicing_access (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    servicing_office_id BIGINT NOT NULL,
    book_office_id BIGINT NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    created_by BIGINT NOT NULL,
    created_on_utc DATETIME(6) NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc DATETIME(6) NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL,
    CONSTRAINT fk_osa_servicing_office FOREIGN KEY (servicing_office_id) REFERENCES m_office (id),
    CONSTRAINT fk_osa_book_office FOREIGN KEY (book_office_id) REFERENCES m_office (id),
    CONSTRAINT uk_osa_servicing_book_from UNIQUE (servicing_office_id, book_office_id, effective_from)
);

CREATE INDEX idx_osa_servicing_office ON m_office_servicing_access (servicing_office_id);
CREATE INDEX idx_osa_book_office ON m_office_servicing_access (book_office_id);
CREATE INDEX idx_osa_status ON m_office_servicing_access (status);
