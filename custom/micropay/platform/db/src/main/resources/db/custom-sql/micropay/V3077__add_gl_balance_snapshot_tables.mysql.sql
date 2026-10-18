--
-- MICROPAY CBS: GL balance snapshot tables (MariaDB / MySQL)
--

CREATE TABLE m_gl_balance_snapshot (
    snapshot_date DATE NOT NULL,
    snapshot_granularity VARCHAR(10) NOT NULL,
    office_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL DEFAULT 0,
    gl_account_id BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    closing_balance_base DECIMAL(19, 6) NOT NULL,
    closing_balance_foreign DECIMAL(19, 6) NOT NULL,
    is_sealed TINYINT(1) NOT NULL DEFAULT 0,
    created_on_utc DATETIME(6) NOT NULL,
    PRIMARY KEY (snapshot_date, snapshot_granularity, office_id, department_id, gl_account_id, currency_code),
    CONSTRAINT fk_m_gl_balance_snapshot_office FOREIGN KEY (office_id) REFERENCES m_office (id),
    CONSTRAINT fk_m_gl_balance_snapshot_gl_account FOREIGN KEY (gl_account_id) REFERENCES acc_gl_account (id)
);

CREATE INDEX idx_m_gl_balance_snapshot_report ON m_gl_balance_snapshot (snapshot_date, office_id);
CREATE INDEX idx_m_gl_balance_snapshot_account_lookup ON m_gl_balance_snapshot (office_id, snapshot_date, gl_account_id);
CREATE INDEX idx_m_gl_balance_snapshot_dept_report ON m_gl_balance_snapshot (snapshot_date, office_id, department_id);

CREATE TABLE m_gl_balance_snapshot_tracking (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    snapshot_granularity VARCHAR(10) NOT NULL,
    snapshot_date_from DATE NOT NULL,
    snapshot_date_to DATE NOT NULL,
    job_execution_id BIGINT NOT NULL,
    created_on_utc DATETIME(6) NOT NULL,
    CONSTRAINT fk_m_gl_balance_snapshot_tracking_job FOREIGN KEY (job_execution_id) REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);

CREATE INDEX idx_m_gl_balance_snapshot_tracking_to ON m_gl_balance_snapshot_tracking (snapshot_granularity, snapshot_date_to);
