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

-- MICROPAY CBS: client title lookup (Mr, Mrs, Ms, Miss, Other) — MySQL / MariaDB

INSERT INTO m_code (code_name, is_system_defined)
SELECT 'ClientTitle', 0
WHERE NOT EXISTS (SELECT 1 FROM m_code WHERE code_name = 'ClientTitle');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Mr', 'Mr', 1, 1, 0
FROM m_code c
WHERE c.code_name = 'ClientTitle'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Mr');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Mrs', 'Mrs', 2, 1, 0
FROM m_code c
WHERE c.code_name = 'ClientTitle'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Mrs');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Ms', 'Ms', 3, 1, 0
FROM m_code c
WHERE c.code_name = 'ClientTitle'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Ms');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Miss', 'Miss', 4, 1, 0
FROM m_code c
WHERE c.code_name = 'ClientTitle'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Miss');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Other', 'Other', 5, 1, 0
FROM m_code c
WHERE c.code_name = 'ClientTitle'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Other');
