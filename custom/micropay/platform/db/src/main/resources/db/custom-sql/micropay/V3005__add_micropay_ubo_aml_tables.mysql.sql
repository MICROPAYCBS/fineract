--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements. See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership. The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License. You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations
-- under the License.
--

-- MICROPAY CBS: UBO & AML Tables (MariaDB / MySQL)

CREATE TABLE m_client_ubo (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    ubo_name VARCHAR(150) NOT NULL,
    ownership_percentage DECIMAL(5,2) NOT NULL,
    identification_type_id INT,
    identification_number VARCHAR(50),
    nationality_country_id INT,
    pep_flag CHAR(1) DEFAULT 'N',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    created_on_utc DATETIME(6) NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc DATETIME(6) NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

CREATE TABLE m_client_risk_score_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    calculated_score INT NOT NULL,
    risk_tier VARCHAR(20) NOT NULL,
    contributing_factors TEXT,
    calculation_date DATETIME(6) NOT NULL,
    created_by BIGINT NOT NULL,
    created_on_utc DATETIME(6) NOT NULL
);

CREATE TABLE m_aml_alert_queue (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    transaction_reference VARCHAR(50),
    rule_triggered VARCHAR(100) NOT NULL,
    alert_status VARCHAR(20) DEFAULT 'PENDING',
    risk_score_at_alert INT,
    investigator_id BIGINT,
    resolution_comments VARCHAR(500),
    created_by BIGINT NOT NULL,
    created_on_utc DATETIME(6) NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc DATETIME(6) NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

CREATE TABLE m_aml_regulatory_report (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    report_type VARCHAR(20) NOT NULL,
    client_id BIGINT NOT NULL,
    alert_id BIGINT,
    submission_status VARCHAR(20) DEFAULT 'DRAFT',
    fia_reference_number VARCHAR(50),
    report_data_payload TEXT,
    submission_date DATETIME(6),
    created_by BIGINT NOT NULL,
    created_on_utc DATETIME(6) NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc DATETIME(6) NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

ALTER TABLE m_client_ubo
    ADD CONSTRAINT FK_m_client_ubo_client_id FOREIGN KEY (client_id) REFERENCES m_client (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_ubo_identification_type_id FOREIGN KEY (identification_type_id) REFERENCES m_code_value (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_ubo_nationality_country_id FOREIGN KEY (nationality_country_id) REFERENCES m_code_value (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_ubo_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_ubo_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE m_client_risk_score_history
    ADD CONSTRAINT FK_m_client_risk_score_history_client_id FOREIGN KEY (client_id) REFERENCES m_client (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_risk_score_history_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE m_aml_alert_queue
    ADD CONSTRAINT FK_m_aml_alert_queue_client_id FOREIGN KEY (client_id) REFERENCES m_client (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_aml_alert_queue_investigator_id FOREIGN KEY (investigator_id) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_aml_alert_queue_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_aml_alert_queue_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE m_aml_regulatory_report
    ADD CONSTRAINT FK_m_aml_regulatory_report_client_id FOREIGN KEY (client_id) REFERENCES m_client (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_aml_regulatory_report_alert_id FOREIGN KEY (alert_id) REFERENCES m_aml_alert_queue (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_aml_regulatory_report_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_aml_regulatory_report_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
