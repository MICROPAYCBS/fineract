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

-- MICROPAY CBS: Customer Class, Prospect & Blacklist Tables (PostgreSQL)

CREATE TABLE m_customer_class (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    class_code VARCHAR(20) UNIQUE NOT NULL,
    class_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    customer_type VARCHAR(20),
    risk_level VARCHAR(20),
    kyc_level VARCHAR(20),
    loan_eligible CHAR(1) DEFAULT 'Y',
    restriction_id BIGINT,
    overdraft_allowed CHAR(1) DEFAULT 'N',
    enhanced_due_diligence CHAR(1) DEFAULT 'N',
    reclassification_allowed CHAR(1) DEFAULT 'Y',
    min_age INT,
    max_age INT,
    enforce_cust_photo CHAR(1) DEFAULT 'Y',
    enforce_cust_signature CHAR(1) DEFAULT 'Y',
    enforce_cust_document CHAR(1) DEFAULT 'Y',
    auto_create_account CHAR(1) DEFAULT 'N',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

CREATE TABLE m_customer_class_account (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_class_id BIGINT,
    product_type CHAR(2),
    product_id BIGINT,
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE m_prospect (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    middle_name VARCHAR(50),
    last_name VARCHAR(50) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender CHAR(1) CHECK (gender IN ('M', 'F')),
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(50),
    residency CHAR(1) DEFAULT 'Y',
    campaign_id BIGINT,
    lead_source VARCHAR(20) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'MANAGED', 'CONVERTED', 'REJECTED', 'EXPIRED')),
    managedby_id BIGINT,
    approvedby_id BIGINT,
    approval_date DATE,
    rejected_reason VARCHAR(100),
    rejected_date DATE,
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

CREATE TABLE m_client_blacklist (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id BIGINT NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    blacklist_reason_id INT,
    blacklist_category_id BIGINT,
    source_type VARCHAR(30) NOT NULL,
    reference VARCHAR(200),
    date_blacklisted DATE NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    review_date DATE,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED', 'PENDING_REVIEW')),
    risk_id BIGINT,
    block_account_opening CHAR(1) DEFAULT 'N',
    block_loan_application CHAR(1) DEFAULT 'N',
    block_deposits CHAR(1) DEFAULT 'N',
    block_withdrawals CHAR(1) DEFAULT 'N',
    block_transfers CHAR(1) DEFAULT 'N',
    block_digital_channels CHAR(1) DEFAULT 'N',
    block_card_issuance CHAR(1) DEFAULT 'N',
    sanctions_match_flag CHAR(1) DEFAULT 'N',
    pep_flag CHAR(1) DEFAULT 'N',
    fraud_flag CHAR(1) DEFAULT 'N',
    credit_bureau_flag CHAR(1) DEFAULT 'N',
    comments VARCHAR(300),
    approvedby_id BIGINT,
    approval_date DATE,
    created_by BIGINT NOT NULL,
    created_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by BIGINT NOT NULL,
    last_modified_on_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL
);

ALTER TABLE m_customer_class
    ADD CONSTRAINT FK_m_customer_class_restriction_id FOREIGN KEY (restriction_id) REFERENCES m_restriction (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_customer_class_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_customer_class_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE m_customer_class_account
    ADD CONSTRAINT FK_m_customer_class_account_customer_class_id FOREIGN KEY (customer_class_id) REFERENCES m_customer_class (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_customer_class_account_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE m_prospect
    ADD CONSTRAINT FK_m_prospect_campaign_id FOREIGN KEY (campaign_id) REFERENCES m_campaign (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_prospect_managedby_id FOREIGN KEY (managedby_id) REFERENCES m_staff (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_prospect_approvedby_id FOREIGN KEY (approvedby_id) REFERENCES m_staff (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_prospect_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_prospect_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE m_client_blacklist
    ADD CONSTRAINT FK_m_client_blacklist_client_id FOREIGN KEY (client_id) REFERENCES m_client (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_blacklist_blacklist_reason_id FOREIGN KEY (blacklist_reason_id) REFERENCES m_code_value (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_blacklist_blacklist_category_id FOREIGN KEY (blacklist_category_id) REFERENCES m_blacklist_category (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_blacklist_risk_id FOREIGN KEY (risk_id) REFERENCES m_risk (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_blacklist_approvedby_id FOREIGN KEY (approvedby_id) REFERENCES m_staff (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_blacklist_created_by FOREIGN KEY (created_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT FK_m_client_blacklist_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES m_appuser (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
