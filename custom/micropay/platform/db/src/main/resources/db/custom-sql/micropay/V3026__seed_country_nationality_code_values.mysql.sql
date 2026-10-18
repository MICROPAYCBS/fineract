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

-- MICROPAY CBS: nationality / country lookup (COUNTRY code) — MySQL / MariaDB

INSERT INTO m_code (code_name, is_system_defined)
SELECT 'COUNTRY', 1
WHERE NOT EXISTS (SELECT 1 FROM m_code WHERE code_name = 'COUNTRY');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Uganda', 'Uganda', 1, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Uganda');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Kenya', 'Kenya', 2, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Kenya');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Tanzania', 'Tanzania', 3, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Tanzania');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Rwanda', 'Rwanda', 4, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Rwanda');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Burundi', 'Burundi', 5, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Burundi');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'South Sudan', 'South Sudan', 6, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'South Sudan');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Democratic Republic of Congo', 'Democratic Republic of Congo', 7, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Democratic Republic of Congo');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Ethiopia', 'Ethiopia', 8, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Ethiopia');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Somalia', 'Somalia', 9, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Somalia');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Sudan', 'Sudan', 10, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Sudan');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Egypt', 'Egypt', 11, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Egypt');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'South Africa', 'South Africa', 12, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'South Africa');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Nigeria', 'Nigeria', 13, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Nigeria');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Ghana', 'Ghana', 14, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Ghana');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'India', 'India', 15, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'India');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'China', 'China', 16, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'China');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'United Kingdom', 'United Kingdom', 17, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'United Kingdom');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'United States', 'United States', 18, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'United States');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'United Arab Emirates', 'United Arab Emirates', 19, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'United Arab Emirates');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Pakistan', 'Pakistan', 20, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Pakistan');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Other', 'Other nationality', 99, 1, 0 FROM m_code c
WHERE c.code_name = 'COUNTRY' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Other');
