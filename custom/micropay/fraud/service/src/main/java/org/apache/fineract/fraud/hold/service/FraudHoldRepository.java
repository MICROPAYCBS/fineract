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
package org.apache.fineract.fraud.hold.service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.fraud.hold.data.FraudHoldData;
import org.apache.fineract.fraud.hold.data.FraudHoldEffectData;
import org.apache.fineract.fraud.hold.data.FraudHoldEffectType;
import org.apache.fineract.fraud.hold.data.FraudHoldEntityType;
import org.apache.fineract.fraud.hold.data.FraudHoldSource;
import org.apache.fineract.fraud.hold.data.FraudHoldStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC storage for fraud holds. JDBC keeps the custom module free of JPA entity scanning and static weaving.
 */
@Repository
@RequiredArgsConstructor
public class FraudHoldRepository {

    private static final String HOLD_COLUMNS = "id, entity_type, entity_id, status, source, reason, external_reference, created_by, "
            + "created_on_utc, released_by, released_on_utc, release_note";
    private static final RowMapper<FraudHoldData> HOLD_MAPPER = FraudHoldRepository::mapHold;
    private static final RowMapper<FraudHoldEffectData> EFFECT_MAPPER = (rs, rowNum) -> new FraudHoldEffectData(rs.getLong("id"),
            rs.getLong("hold_id"), FraudHoldEffectType.valueOf(rs.getString("effect_type")), rs.getLong("target_id"));

    private final JdbcTemplate jdbcTemplate;

    public Long insertHold(FraudHoldEntityType entityType, Long entityId, FraudHoldSource source, String reason, String externalReference,
            Long createdBy, LocalDateTime createdOnUtc) {
        final String sql = "INSERT INTO m_fraud_hold (entity_type, entity_id, status, source, reason, external_reference, "
                + "created_by, created_on_utc) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            final PreparedStatement ps = con.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, entityType.name());
            ps.setLong(2, entityId);
            ps.setString(3, FraudHoldStatus.ACTIVE.name());
            ps.setString(4, source.name());
            ps.setString(5, reason);
            ps.setString(6, externalReference);
            ps.setLong(7, createdBy);
            ps.setTimestamp(8, Timestamp.valueOf(createdOnUtc));
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void markReleased(Long holdId, Long releasedBy, LocalDateTime releasedOnUtc, String releaseNote) {
        jdbcTemplate.update("UPDATE m_fraud_hold SET status = ?, released_by = ?, released_on_utc = ?, release_note = ? WHERE id = ?",
                FraudHoldStatus.RELEASED.name(), releasedBy, Timestamp.valueOf(releasedOnUtc), releaseNote, holdId);
    }

    public Optional<FraudHoldData> findById(Long holdId) {
        final List<FraudHoldData> holds = jdbcTemplate.query("SELECT " + HOLD_COLUMNS + " FROM m_fraud_hold WHERE id = ?", HOLD_MAPPER,
                holdId);
        return holds.stream().findFirst();
    }

    public List<FraudHoldData> find(FraudHoldEntityType entityType, Long entityId, FraudHoldStatus status, int limit) {
        final StringBuilder sql = new StringBuilder("SELECT ").append(HOLD_COLUMNS).append(" FROM m_fraud_hold WHERE 1 = 1");
        final List<Object> params = new ArrayList<>();
        if (entityType != null) {
            sql.append(" AND entity_type = ?");
            params.add(entityType.name());
        }
        if (entityId != null) {
            sql.append(" AND entity_id = ?");
            params.add(entityId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        params.add(limit);
        return jdbcTemplate.query(sql.toString(), HOLD_MAPPER, params.toArray());
    }

    public boolean hasActiveHold(FraudHoldEntityType entityType, Long entityId) {
        final Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM m_fraud_hold WHERE entity_type = ? AND entity_id = ? AND status = ?", Integer.class,
                entityType.name(), entityId, FraudHoldStatus.ACTIVE.name());
        return count != null && count > 0;
    }

    /**
     * Finds another active hold that also keeps the given savings account's debits blocked: a hold on the account
     * itself or on the client who owns it.
     */
    public Optional<Long> findOtherActiveHoldCoveringSavings(Long savingsId, Long excludedHoldId) {
        final List<Long> ids = jdbcTemplate.queryForList(
                "SELECT h.id FROM m_fraud_hold h WHERE h.status = ? AND h.id <> ? AND "
                        + "((h.entity_type = ? AND h.entity_id = ?) OR (h.entity_type = ? AND h.entity_id = "
                        + "(SELECT sa.client_id FROM m_savings_account sa WHERE sa.id = ?))) ORDER BY h.id",
                Long.class, FraudHoldStatus.ACTIVE.name(), excludedHoldId, FraudHoldEntityType.SAVINGS_ACCOUNT.name(), savingsId,
                FraudHoldEntityType.CLIENT.name(), savingsId);
        return ids.stream().findFirst();
    }

    public Optional<Long> findOtherActiveHoldCoveringLoan(Long loanId, Long excludedHoldId) {
        final List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM m_fraud_hold WHERE status = ? AND id <> ? AND entity_type = ? AND entity_id = ? ORDER BY id", Long.class,
                FraudHoldStatus.ACTIVE.name(), excludedHoldId, FraudHoldEntityType.LOAN.name(), loanId);
        return ids.stream().findFirst();
    }

    public void insertEffect(Long holdId, FraudHoldEffectType effectType, Long targetId) {
        jdbcTemplate.update("INSERT INTO m_fraud_hold_effect (hold_id, effect_type, target_id) VALUES (?, ?, ?)", holdId, effectType.name(),
                targetId);
    }

    public List<FraudHoldEffectData> findEffects(Long holdId) {
        return jdbcTemplate.query("SELECT id, hold_id, effect_type, target_id FROM m_fraud_hold_effect WHERE hold_id = ? ORDER BY id",
                EFFECT_MAPPER, holdId);
    }

    public void reassignEffect(Long effectId, Long newHoldId) {
        jdbcTemplate.update("UPDATE m_fraud_hold_effect SET hold_id = ? WHERE id = ?", newHoldId, effectId);
    }

    public void deleteEffect(Long effectId) {
        jdbcTemplate.update("DELETE FROM m_fraud_hold_effect WHERE id = ?", effectId);
    }

    private static FraudHoldData mapHold(ResultSet rs, int rowNum) throws SQLException {
        return new FraudHoldData(rs.getLong("id"), FraudHoldEntityType.valueOf(rs.getString("entity_type")), rs.getLong("entity_id"),
                FraudHoldStatus.valueOf(rs.getString("status")), FraudHoldSource.valueOf(rs.getString("source")), rs.getString("reason"),
                rs.getString("external_reference"), rs.getLong("created_by"), toLocalDateTime(rs.getTimestamp("created_on_utc")),
                rs.getObject("released_by", Long.class), toLocalDateTime(rs.getTimestamp("released_on_utc")), rs.getString("release_note"),
                List.of());
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
