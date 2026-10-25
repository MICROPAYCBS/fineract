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
package org.apache.fineract.treasury.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandProcessingResultType;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryContextData;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Form defaults, and the signed-in user's own treasury commands awaiting a checker. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TreasuryContextService {

    /** At most this many pending commands are listed. */
    static final int PENDING_LIMIT = 200;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TreasuryFxConfig fxConfig;

    public TreasuryContextData.Template template() {
        return new TreasuryContextData.Template(DateUtils.getBusinessLocalDate(), this.fxConfig.baseCurrency());
    }

    /**
     * The treasury commands the user made that await a checker, newest first; optionally of one entity (e.g.
     * TREASURYDEAL) and resource. Only the user's own: the maker-checker inbox lists what a user may approve, not what
     * they asked for.
     */
    public List<TreasuryContextData.PendingCommand> pendingCommands(final Long userId, final String entityName, final Long resourceId) {
        final StringBuilder sql = new StringBuilder("SELECT id, action_name, entity_name, resource_id, subresource_id, api_get_url,"
                + " made_on_date, command_as_json FROM m_portfolio_command_source"
                + " WHERE maker_id = :userId AND status = :status AND entity_name LIKE 'TREASURY%'");
        final MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId).addValue("status",
                CommandProcessingResultType.AWAITING_APPROVAL.getValue());
        if (entityName != null && !entityName.isBlank()) {
            sql.append(" AND entity_name = :entityName");
            params.addValue("entityName", entityName.trim().toUpperCase(Locale.ROOT));
        }
        if (resourceId != null) {
            sql.append(" AND resource_id = :resourceId");
            params.addValue("resourceId", resourceId);
        }
        sql.append(" ORDER BY id DESC LIMIT ").append(PENDING_LIMIT);
        return this.jdbcTemplate.query(sql.toString(), params,
                (rs, rowNum) -> new TreasuryContextData.PendingCommand(rs.getLong("id"), rs.getString("action_name"),
                        rs.getString("entity_name"), rs.getObject("resource_id", Long.class), rs.getObject("subresource_id", Long.class),
                        rs.getString("api_get_url"), rs.getObject("made_on_date", LocalDateTime.class), rs.getString("command_as_json")));
    }
}
