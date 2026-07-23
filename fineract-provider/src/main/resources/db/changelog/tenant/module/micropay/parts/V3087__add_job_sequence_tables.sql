--
-- MICROPAY CBS: configurable job sequences (PostgreSQL)
--

CREATE TABLE m_job_sequence (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT true,
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_m_job_sequence_name UNIQUE (name),
    CONSTRAINT fk_m_job_sequence_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id),
    CONSTRAINT fk_m_job_sequence_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id)
);

CREATE TABLE m_job_sequence_step (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sequence_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    step_type VARCHAR(30) NOT NULL,
    job_short_name VARCHAR(50),
    operation_code VARCHAR(50),
    enabled BOOLEAN NOT NULL DEFAULT true,
    stop_on_failure BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT fk_m_job_sequence_step_sequence FOREIGN KEY (sequence_id) REFERENCES m_job_sequence (id) ON DELETE CASCADE,
    CONSTRAINT uq_m_job_sequence_step_order UNIQUE (sequence_id, step_order)
);

CREATE INDEX ix_m_job_sequence_step_sequence ON m_job_sequence_step (sequence_id);

CREATE TABLE m_job_sequence_run (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sequence_id BIGINT NOT NULL,
    triggered_by_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    error_message VARCHAR(2000),
    CONSTRAINT fk_m_job_sequence_run_sequence FOREIGN KEY (sequence_id) REFERENCES m_job_sequence (id) ON DELETE CASCADE,
    CONSTRAINT fk_m_job_sequence_run_user FOREIGN KEY (triggered_by_user_id) REFERENCES m_appuser (id)
);

CREATE INDEX ix_m_job_sequence_run_sequence ON m_job_sequence_run (sequence_id);
CREATE INDEX ix_m_job_sequence_run_status ON m_job_sequence_run (sequence_id, status);

CREATE TABLE m_job_sequence_run_step (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id BIGINT NOT NULL,
    sequence_step_id BIGINT,
    step_order INT NOT NULL,
    step_type VARCHAR(30) NOT NULL,
    job_short_name VARCHAR(50),
    operation_code VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    scheduler_job_id BIGINT,
    error_message VARCHAR(2000),
    CONSTRAINT fk_m_job_sequence_run_step_run FOREIGN KEY (run_id) REFERENCES m_job_sequence_run (id) ON DELETE CASCADE
);

CREATE INDEX ix_m_job_sequence_run_step_run ON m_job_sequence_run_step (run_id);
