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

import com.micropay.fraud.engine.event.ClientEvent;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ClientProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public void save(ClientEvent client, Instant at) {
        final Date activation = client.activationDate() == null ? null : Date.valueOf(client.activationDate());
        final int updated = jdbcTemplate.update(
                "UPDATE fe_client_profile SET activation_date = COALESCE(?, activation_date), office_id = ?, display_name = ?, updated_at = ? "
                        + "WHERE tenant_id = ? AND client_id = ?",
                activation, client.officeId(), client.displayName(), Timestamp.from(at), client.tenantId(), client.clientId());
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO fe_client_profile (tenant_id, client_id, activation_date, office_id, display_name, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    client.tenantId(), client.clientId(), activation, client.officeId(), client.displayName(), Timestamp.from(at));
        }
    }

    public Optional<LocalDate> findActivationDate(String tenantId, Long clientId) {
        return jdbcTemplate.queryForList("SELECT activation_date FROM fe_client_profile WHERE tenant_id = ? AND client_id = ?", Date.class,
                tenantId, clientId).stream().filter(java.util.Objects::nonNull).map(Date::toLocalDate).findFirst();
    }
}
