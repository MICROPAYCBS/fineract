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

-- MICROPAY CBS: nationality / country lookup (COUNTRY code) — PostgreSQL

INSERT INTO m_code (code_name, is_system_defined)
SELECT 'COUNTRY', true
WHERE NOT EXISTS (SELECT 1 FROM m_code WHERE code_name = 'COUNTRY');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, v.code_value, v.code_description, v.order_position, true, false
FROM m_code c
CROSS JOIN (
    VALUES
        ('Uganda', 'Uganda', 1),
        ('Kenya', 'Kenya', 2),
        ('Tanzania', 'Tanzania', 3),
        ('Rwanda', 'Rwanda', 4),
        ('Burundi', 'Burundi', 5),
        ('South Sudan', 'South Sudan', 6),
        ('Democratic Republic of Congo', 'Democratic Republic of Congo', 7),
        ('Ethiopia', 'Ethiopia', 8),
        ('Somalia', 'Somalia', 9),
        ('Sudan', 'Sudan', 10),
        ('Egypt', 'Egypt', 11),
        ('South Africa', 'South Africa', 12),
        ('Nigeria', 'Nigeria', 13),
        ('Ghana', 'Ghana', 14),
        ('India', 'India', 15),
        ('China', 'China', 16),
        ('United Kingdom', 'United Kingdom', 17),
        ('United States', 'United States', 18),
        ('United Arab Emirates', 'United Arab Emirates', 19),
        ('Pakistan', 'Pakistan', 20),
        ('Other', 'Other nationality', 99)
) AS v(code_value, code_description, order_position)
WHERE c.code_name = 'COUNTRY'
  AND NOT EXISTS (
      SELECT 1 FROM m_code_value cv
      WHERE cv.code_id = c.id AND cv.code_value = v.code_value
  );
