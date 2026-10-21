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

-- MICROPAY CBS: next-of-kin relationship lookup (RELATIONSHIP code) — PostgreSQL

INSERT INTO m_code (code_name, is_system_defined)
SELECT 'RELATIONSHIP', true
WHERE NOT EXISTS (SELECT 1 FROM m_code WHERE code_name = 'RELATIONSHIP');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, v.code_value, v.code_description, v.order_position, true, false
FROM m_code c
CROSS JOIN (
    VALUES
        ('Spouse', 'Spouse', 1),
        ('Father', 'Father', 2),
        ('Mother', 'Mother', 3),
        ('Son', 'Son', 4),
        ('Daughter', 'Daughter', 5),
        ('Brother', 'Brother', 6),
        ('Sister', 'Sister', 7),
        ('Child', 'Child', 8),
        ('Guardian', 'Guardian', 9),
        ('Friend', 'Friend', 10),
        ('Other', 'Other relationship', 99)
) AS v(code_value, code_description, order_position)
WHERE c.code_name = 'RELATIONSHIP'
  AND NOT EXISTS (
      SELECT 1 FROM m_code_value cv
      WHERE cv.code_id = c.id AND cv.code_value = v.code_value
  );
