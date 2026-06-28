--
-- MICROPAY CBS: inter-branch GL settlement rules (MySQL / MariaDB)
--

CREATE TABLE IF NOT EXISTS m_inter_branch_gl_rule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    left_office_id BIGINT NULL,
    right_office_id BIGINT NULL,
    gl_account_id BIGINT NOT NULL,
    currency_code CHAR(3) NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    created_by BIGINT NOT NULL,
    created_on_utc DATETIME(6) NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc DATETIME(6) NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL,
    CONSTRAINT fk_ibt_left_office FOREIGN KEY (left_office_id) REFERENCES m_office (id),
    CONSTRAINT fk_ibt_right_office FOREIGN KEY (right_office_id) REFERENCES m_office (id),
    CONSTRAINT fk_ibt_gl_account FOREIGN KEY (gl_account_id) REFERENCES acc_gl_account (id)
);

CREATE INDEX idx_ibt_gl_rule_left_office ON m_inter_branch_gl_rule (left_office_id);
CREATE INDEX idx_ibt_gl_rule_right_office ON m_inter_branch_gl_rule (right_office_id);
CREATE INDEX idx_ibt_gl_rule_status ON m_inter_branch_gl_rule (status);
