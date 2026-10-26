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
import com.micropay.fraud.engine.event.PartyType;
import com.micropay.fraud.engine.watchlist.ScreeningSubject;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * What the engine knows about each client: activation date for the rules, and name, date of birth and party type for
 * watchlist screening.
 */
@Repository
@RequiredArgsConstructor
public class ClientProfileRepository {

    private static final int MAX_NAME = 300;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Stores the client. Fields the event leaves empty keep their stored value.
     */
    public void save(ClientEvent client, Instant at) {
        final Date activation = client.activationDate() == null ? null : Date.valueOf(client.activationDate());
        final Date birth = client.dateOfBirth() == null ? null : Date.valueOf(client.dateOfBirth());
        final String party = client.partyType() == null || client.partyType() == PartyType.UNKNOWN ? null : client.partyType().name();
        final String fullName = client.fullName() == null || client.fullName().length() <= MAX_NAME ? client.fullName()
                : client.fullName().substring(0, MAX_NAME);
        final int updated = jdbcTemplate.update(
                "UPDATE fe_client_profile SET activation_date = COALESCE(?, activation_date), office_id = COALESCE(?, office_id), "
                        + "display_name = COALESCE(?, display_name), full_name = COALESCE(?, full_name), "
                        + "date_of_birth = COALESCE(?, date_of_birth), party_type = COALESCE(?, party_type), updated_at = ? "
                        + "WHERE tenant_id = ? AND client_id = ?",
                activation, client.officeId(), client.displayName(), fullName, birth, party, Timestamp.from(at), client.tenantId(),
                client.clientId());
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO fe_client_profile (tenant_id, client_id, activation_date, office_id, display_name, full_name, date_of_birth, "
                            + "party_type, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    client.tenantId(), client.clientId(), activation, client.officeId(), client.displayName(), fullName, birth, party,
                    Timestamp.from(at));
        }
    }

    public Optional<LocalDate> findActivationDate(String tenantId, Long clientId) {
        return jdbcTemplate.queryForList("SELECT activation_date FROM fe_client_profile WHERE tenant_id = ? AND client_id = ?", Date.class,
                tenantId, clientId).stream().filter(Objects::nonNull).map(Date::toLocalDate).findFirst();
    }

    public Optional<ScreeningSubject> findSubject(String tenantId, Long clientId) {
        return jdbcTemplate.query(
                "SELECT tenant_id, client_id, full_name, date_of_birth, party_type FROM fe_client_profile "
                        + "WHERE tenant_id = ? AND client_id = ? AND full_name IS NOT NULL",
                (rs, rowNum) -> subject(rs), tenantId, clientId).stream().findFirst();
    }

    /**
     * The next clients, in key order after the given one, not yet screened against {@code version} or changed since
     * their last screening.
     */
    public List<ScreeningSubject> findDueForScreening(String version, String afterTenant, Long afterClient, int limit) {
        return jdbcTemplate.query(
                "SELECT tenant_id, client_id, full_name, date_of_birth, party_type FROM fe_client_profile "
                        + "WHERE full_name IS NOT NULL AND (screened_version IS NULL OR screened_version <> ? OR updated_at > screened_at) "
                        + "AND (tenant_id > ? OR (tenant_id = ? AND client_id > ?)) ORDER BY tenant_id, client_id LIMIT ?",
                (rs, rowNum) -> subject(rs), version, afterTenant, afterTenant, afterClient, limit);
    }

    public void markScreened(String tenantId, Long clientId, String version, Instant at) {
        jdbcTemplate.update("UPDATE fe_client_profile SET screened_version = ?, screened_at = ? WHERE tenant_id = ? AND client_id = ?",
                version, Timestamp.from(at), tenantId, clientId);
    }

    private static ScreeningSubject subject(ResultSet rs) throws SQLException {
        final Date birth = rs.getDate("date_of_birth");
        final String party = rs.getString("party_type");
        return new ScreeningSubject(rs.getString("tenant_id"), rs.getLong("client_id"), rs.getString("full_name"),
                birth == null ? null : birth.toLocalDate(), party == null ? PartyType.UNKNOWN : PartyType.valueOf(party));
    }
}
