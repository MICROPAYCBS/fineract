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

-- MICROPAY CBS: customer risk profile lookup (bank use / AML tier) — PostgreSQL

INSERT INTO m_code (code_name, is_system_defined)
SELECT 'CustomerRiskProfile', false
WHERE NOT EXISTS (SELECT 1 FROM m_code WHERE code_name = 'CustomerRiskProfile');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Very High', 'Very High', 1, true, false
FROM m_code c
WHERE c.code_name = 'CustomerRiskProfile'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Very High');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'High', 'High', 2, true, false
FROM m_code c
WHERE c.code_name = 'CustomerRiskProfile'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'High');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Medium', 'Medium', 3, true, false
FROM m_code c
WHERE c.code_name = 'CustomerRiskProfile'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Medium');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Low', 'Low', 4, true, false
FROM m_code c
WHERE c.code_name = 'CustomerRiskProfile'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Low');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Very Low', 'Very Low', 5, true, false
FROM m_code c
WHERE c.code_name = 'CustomerRiskProfile'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Very Low');
