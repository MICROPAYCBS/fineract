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

-- MICROPAY CBS: Campaign & Channel Master Tables (MariaDB / MySQL)

CREATE TABLE m_campaign (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    campaign_code VARCHAR(30) UNIQUE NOT NULL,
    campaign_name VARCHAR(150) NOT NULL,
    description VARCHAR(255),
    campaign_type VARCHAR(50),
    target_segment VARCHAR(50),
    office_id BIGINT,
    start_date DATE,
    end_date DATE,
    budget_amount DECIMAL(18,2),
    target_amount DECIMAL(18,2),
    responsible_officer_id BIGINT,
    approval_status VARCHAR(20),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    created_on_utc DATETIME(6) NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc DATETIME(6) NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

CREATE TABLE m_channel (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    channel_code VARCHAR(30) UNIQUE NOT NULL,
    channel_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    channel_type VARCHAR(30),
    transaction_limit DECIMAL(18,2),
    daily_limit DECIMAL(18,2),
    currency_code VARCHAR(10),
    charge_id BIGINT,
    security_level VARCHAR(30),
    requires_otp CHAR(1) DEFAULT 'N',
    available_24hrs CHAR(1) DEFAULT 'Y',
    max_transaction_count INT,
    aml_risk_level VARCHAR(20),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    created_on_utc DATETIME(6) NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc DATETIME(6) NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

ALTER TABLE m_campaign
    ADD CONSTRAINT FK_m_campaign_office_id FOREIGN KEY (office_id) REFERENCES m_office (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_campaign_responsible_officer_id FOREIGN KEY (responsible_officer_id) REFERENCES m_staff (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_campaign_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_campaign_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE m_channel
    ADD CONSTRAINT FK_m_channel_charge_id FOREIGN KEY (charge_id) REFERENCES m_charge (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_channel_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_channel_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
