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

-- MICROPAY CBS: next-of-kin relationship lookup (RELATIONSHIP code) — MySQL / MariaDB

INSERT INTO m_code (code_name, is_system_defined)
SELECT 'RELATIONSHIP', 1
WHERE NOT EXISTS (SELECT 1 FROM m_code WHERE code_name = 'RELATIONSHIP');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Spouse', 'Spouse', 1, 1, 0 FROM m_code c
WHERE c.code_name = 'RELATIONSHIP' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Spouse');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Father', 'Father', 2, 1, 0 FROM m_code c
WHERE c.code_name = 'RELATIONSHIP' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Father');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Mother', 'Mother', 3, 1, 0 FROM m_code c
WHERE c.code_name = 'RELATIONSHIP' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Mother');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Son', 'Son', 4, 1, 0 FROM m_code c
WHERE c.code_name = 'RELATIONSHIP' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Son');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Daughter', 'Daughter', 5, 1, 0 FROM m_code c
WHERE c.code_name = 'RELATIONSHIP' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Daughter');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Brother', 'Brother', 6, 1, 0 FROM m_code c
WHERE c.code_name = 'RELATIONSHIP' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Brother');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Sister', 'Sister', 7, 1, 0 FROM m_code c
WHERE c.code_name = 'RELATIONSHIP' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Sister');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Child', 'Child', 8, 1, 0 FROM m_code c
WHERE c.code_name = 'RELATIONSHIP' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Child');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Guardian', 'Guardian', 9, 1, 0 FROM m_code c
WHERE c.code_name = 'RELATIONSHIP' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Guardian');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Friend', 'Friend', 10, 1, 0 FROM m_code c
WHERE c.code_name = 'RELATIONSHIP' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Friend');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Other', 'Other relationship', 99, 1, 0 FROM m_code c
WHERE c.code_name = 'RELATIONSHIP' AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Other');
