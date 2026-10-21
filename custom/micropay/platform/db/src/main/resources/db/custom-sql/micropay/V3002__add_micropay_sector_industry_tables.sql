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

-- MICROPAY CBS: Sector & Industry Master Tables (PostgreSQL)

CREATE TABLE m_sector (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sector_code VARCHAR(20) UNIQUE NOT NULL,
    sector_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    parent_id BIGINT,
    risk_level VARCHAR(20),
    regulatory_code VARCHAR(20),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

CREATE TABLE m_industry (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    industry_code VARCHAR(20) UNIQUE NOT NULL,
    industry_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    sector_id BIGINT,
    regulatory_code VARCHAR(30),
    risk_level VARCHAR(20),
    aml_risk_level VARCHAR(20),
    credit_risk_level VARCHAR(20),
    priority_industry_flag CHAR(1) DEFAULT 'N',
    prohibited_industry_flag CHAR(1) DEFAULT 'N',
    requires_edd CHAR(1) DEFAULT 'N',
    exposure_limit NUMERIC(18,2),
    expected_turnover_min NUMERIC(18,2),
    expected_turnover_max NUMERIC(18,2),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

CREATE TABLE m_sub_industry (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sub_industry_code VARCHAR(20) UNIQUE NOT NULL,
    sub_industry_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    industry_id BIGINT,
    regulatory_code VARCHAR(30),
    risk_level VARCHAR(20),
    aml_risk_level VARCHAR(20),
    credit_risk_level VARCHAR(20),
    prohibited_flag CHAR(1) DEFAULT 'N',
    priority_flag CHAR(1) DEFAULT 'N',
    requires_edd CHAR(1) DEFAULT 'N',
    exposure_limit NUMERIC(18,2),
    expected_turnover_min NUMERIC(18,2),
    expected_turnover_max NUMERIC(18,2),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

ALTER TABLE m_sector
    ADD CONSTRAINT FK_m_sector_parent_id FOREIGN KEY (parent_id) REFERENCES m_sector (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_sector_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_sector_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE m_industry
    ADD CONSTRAINT FK_m_industry_sector_id FOREIGN KEY (sector_id) REFERENCES m_sector (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_industry_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_industry_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE m_sub_industry
    ADD CONSTRAINT FK_m_sub_industry_industry_id FOREIGN KEY (industry_id) REFERENCES m_industry (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_sub_industry_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_sub_industry_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
