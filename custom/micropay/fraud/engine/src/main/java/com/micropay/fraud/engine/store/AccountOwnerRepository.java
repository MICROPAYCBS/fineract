/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.micropay.fraud.engine.store;

import com.micropay.fraud.engine.event.AccountType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Which client owns each account. Fineract's transaction payloads do not carry the client, so the owner is learnt once
 * (from a transfer or from the Fineract API) and kept here.
 */
@Repository
@RequiredArgsConstructor
public class AccountOwnerRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<Long> findClientId(String tenantId, AccountType accountType, Long accountId) {
        return jdbcTemplate
                .queryForList("SELECT client_id FROM fe_account_owner WHERE tenant_id = ? AND account_type = ? AND account_id = ?",
                        Long.class, tenantId, accountType.name(), accountId)
                .stream().findFirst();
    }

    public void save(String tenantId, AccountType accountType, Long accountId, Long clientId, Instant at) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO fe_account_owner (tenant_id, account_type, account_id, client_id, resolved_at) VALUES (?, ?, ?, ?, ?)",
                    tenantId, accountType.name(), accountId, clientId, Timestamp.from(at));
        } catch (DuplicateKeyException e) {
            // Another record for the same account got there first; the owner does not change
        }
    }
}
