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

-- MICROPAY CBS: primary customer address flag (PostgreSQL)

ALTER TABLE m_client_address
    ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_m_client_address_primary ON m_client_address (client_id, is_primary);

CREATE UNIQUE INDEX uk_m_client_address_primary ON m_client_address (client_id)
    WHERE is_primary = TRUE;

UPDATE m_client_address ca
SET is_primary = TRUE
FROM (
    SELECT client_id, MIN(id) AS min_id
    FROM m_client_address
    GROUP BY client_id
) first_addr
WHERE ca.client_id = first_addr.client_id
  AND ca.id = first_addr.min_id
  AND NOT EXISTS (
      SELECT 1
      FROM m_client_address ca2
      WHERE ca2.client_id = ca.client_id
        AND ca2.is_primary = TRUE
  );
