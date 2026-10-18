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

-- MICROPAY CBS: Client income sources (MariaDB / MySQL)

CREATE TABLE m_client_income_source (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    income_source_type_id INT,
    source_of_funds_id INT,
    employer_business_name VARCHAR(200),
    occupation VARCHAR(100),
    sub_industry_id BIGINT,
    monthly_income DECIMAL(18, 2),
    income_currency_code VARCHAR(3),
    income_frequency_id INT,
    start_date DATE,
    end_date DATE,
    is_primary_source CHAR(1) DEFAULT 'N' NOT NULL,
    verification_status_id INT,
    verified_by BIGINT,
    verified_on_utc DATETIME(6),
    supporting_document VARCHAR(255),
    remarks VARCHAR(500),
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    created_by BIGINT NOT NULL,
    created_on_utc DATETIME(6) NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc DATETIME(6) NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL,
    primary_client_id BIGINT GENERATED ALWAYS AS (IF(is_primary_source = 'Y', client_id, NULL)) STORED,
    CONSTRAINT chk_m_client_income_source_primary CHECK (is_primary_source IN ('Y', 'N')),
    CONSTRAINT chk_m_client_income_source_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_m_client_income_source_client_id ON m_client_income_source (client_id);
CREATE INDEX idx_m_client_income_source_source_of_funds ON m_client_income_source (source_of_funds_id);
CREATE INDEX idx_m_client_income_source_sub_industry ON m_client_income_source (sub_industry_id);
CREATE INDEX idx_m_client_income_source_primary ON m_client_income_source (client_id, is_primary_source);
CREATE INDEX idx_m_client_income_source_verification ON m_client_income_source (verification_status_id);

CREATE UNIQUE INDEX uk_m_client_income_source_primary ON m_client_income_source (primary_client_id);

ALTER TABLE m_client_income_source
    ADD CONSTRAINT FK_m_client_income_source_client_id FOREIGN KEY (client_id) REFERENCES m_client (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_income_source_income_source_type_id FOREIGN KEY (income_source_type_id) REFERENCES m_code_value (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_income_source_source_of_funds_id FOREIGN KEY (source_of_funds_id) REFERENCES m_code_value (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_income_source_sub_industry_id FOREIGN KEY (sub_industry_id) REFERENCES m_sub_industry (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_income_source_income_frequency_id FOREIGN KEY (income_frequency_id) REFERENCES m_code_value (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_income_source_verification_status_id FOREIGN KEY (verification_status_id) REFERENCES m_code_value (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_income_source_verified_by FOREIGN KEY (verified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_income_source_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_income_source_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
