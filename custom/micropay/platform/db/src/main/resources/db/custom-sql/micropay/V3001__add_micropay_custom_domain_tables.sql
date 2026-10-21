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

-- MICROPAY CBS: Risk & Compliance Master Tables (PostgreSQL)

CREATE TABLE m_risk (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    risk_code VARCHAR(30) UNIQUE NOT NULL,
    risk_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    risk_category VARCHAR(50),
    provision_percentage NUMERIC(5,2),
    risk_level VARCHAR(20),
    regulatory_code VARCHAR(30),
    override_allowed CHAR(1) DEFAULT 'N',
    approval_required CHAR(1) DEFAULT 'Y',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

CREATE TABLE m_blacklist_category (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_code VARCHAR(30) UNIQUE NOT NULL,
    description VARCHAR(100),
    risk_level VARCHAR(20),
    regulatory_flag CHAR(1) DEFAULT 'N',
    aml_relevant CHAR(1) DEFAULT 'N',
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

CREATE TABLE m_restriction (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    restriction_code VARCHAR(20) UNIQUE NOT NULL,
    restriction_name VARCHAR(50) NOT NULL,
    restriction_type VARCHAR(20),
    restriction_level VARCHAR(20),
    restriction_action VARCHAR(200),
    start_date DATE,
    end_date DATE,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    override_allowed CHAR(1) DEFAULT 'N',
    remarks VARCHAR(255),
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

ALTER TABLE m_risk
    ADD CONSTRAINT FK_m_risk_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_risk_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE m_blacklist_category
    ADD CONSTRAINT FK_m_blacklist_category_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_blacklist_category_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE m_restriction
    ADD CONSTRAINT FK_m_restriction_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_restriction_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
