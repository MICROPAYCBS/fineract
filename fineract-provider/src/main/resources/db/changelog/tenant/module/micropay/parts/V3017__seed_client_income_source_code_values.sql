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

-- MICROPAY CBS: income source lookup codes (MySQL / MariaDB / PostgreSQL)

INSERT INTO m_code (code_name, is_system_defined)
SELECT 'SourceOfFunds', 0
WHERE NOT EXISTS (SELECT 1 FROM m_code WHERE code_name = 'SourceOfFunds');

INSERT INTO m_code (code_name, is_system_defined)
SELECT 'IncomeSourceType', 0
WHERE NOT EXISTS (SELECT 1 FROM m_code WHERE code_name = 'IncomeSourceType');

INSERT INTO m_code (code_name, is_system_defined)
SELECT 'IncomeFrequency', 0
WHERE NOT EXISTS (SELECT 1 FROM m_code WHERE code_name = 'IncomeFrequency');

INSERT INTO m_code (code_name, is_system_defined)
SELECT 'IncomeVerificationStatus', 0
WHERE NOT EXISTS (SELECT 1 FROM m_code WHERE code_name = 'IncomeVerificationStatus');

-- SourceOfFunds (AML: origin of funds)
INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Salary / Wages', 'Income from employment salary or wages', 1, 1, 0
FROM m_code c
WHERE c.code_name = 'SourceOfFunds'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Salary / Wages');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Business income', 'Income from business operations or self-employment', 2, 1, 0
FROM m_code c
WHERE c.code_name = 'SourceOfFunds'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Business income');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Rental income', 'Income from rental property', 3, 1, 0
FROM m_code c
WHERE c.code_name = 'SourceOfFunds'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Rental income');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Pension / Retirement', 'Pension or retirement benefits', 4, 1, 0
FROM m_code c
WHERE c.code_name = 'SourceOfFunds'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Pension / Retirement');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Investment returns', 'Dividends, interest, or capital gains', 5, 1, 0
FROM m_code c
WHERE c.code_name = 'SourceOfFunds'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Investment returns');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Agriculture / Farming', 'Income from agricultural activity', 6, 1, 0
FROM m_code c
WHERE c.code_name = 'SourceOfFunds'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Agriculture / Farming');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Remittances', 'Funds received from abroad or third parties', 7, 1, 0
FROM m_code c
WHERE c.code_name = 'SourceOfFunds'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Remittances');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Inheritance / Gift', 'Inherited wealth or gifts received', 8, 1, 0
FROM m_code c
WHERE c.code_name = 'SourceOfFunds'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Inheritance / Gift');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Loan proceeds', 'Funds originating from a loan facility', 9, 1, 0
FROM m_code c
WHERE c.code_name = 'SourceOfFunds'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Loan proceeds');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Other', 'Other source of funds', 10, 1, 0
FROM m_code c
WHERE c.code_name = 'SourceOfFunds'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Other');

-- IncomeSourceType (how the customer earns)
INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Employment', 'Employed by an organisation', 1, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeSourceType'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Employment');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Self-employment / Business', 'Self-employed or business owner', 2, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeSourceType'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Self-employment / Business');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Rental property', 'Income from rental property', 3, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeSourceType'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Rental property');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Pension / Retirement', 'Pension or retirement income', 4, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeSourceType'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Pension / Retirement');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Investments', 'Investment-related income', 5, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeSourceType'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Investments');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Agriculture / Farming', 'Agricultural income', 6, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeSourceType'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Agriculture / Farming');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Remittances', 'Income from remittances', 7, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeSourceType'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Remittances');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Other', 'Other income source type', 8, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeSourceType'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Other');

-- IncomeFrequency
INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Weekly', 'Paid weekly', 1, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeFrequency'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Weekly');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Bi-weekly', 'Paid every two weeks', 2, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeFrequency'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Bi-weekly');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Monthly', 'Paid monthly', 3, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeFrequency'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Monthly');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Quarterly', 'Paid quarterly', 4, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeFrequency'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Quarterly');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Annually', 'Paid annually', 5, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeFrequency'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Annually');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Irregular', 'Irregular or variable payment schedule', 6, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeFrequency'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Irregular');

-- IncomeVerificationStatus
INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Pending', 'Verification not yet completed', 1, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeVerificationStatus'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Pending');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Verified', 'Income source verified', 2, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeVerificationStatus'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Verified');

INSERT INTO m_code_value (code_id, code_value, code_description, order_position, is_active, is_mandatory)
SELECT c.id, 'Rejected', 'Income source verification rejected', 3, 1, 0
FROM m_code c
WHERE c.code_name = 'IncomeVerificationStatus'
  AND NOT EXISTS (SELECT 1 FROM m_code_value cv WHERE cv.code_id = c.id AND cv.code_value = 'Rejected');
