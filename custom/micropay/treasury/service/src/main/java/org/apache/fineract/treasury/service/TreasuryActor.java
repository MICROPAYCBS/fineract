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

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Who took a workflow step. Under maker-checker a command runs when the checker approves it, as the checker, so the
 * step belongs to the maker of the held command; otherwise it belongs to the current user.
 */
@Component
@RequiredArgsConstructor
public class TreasuryActor {

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    public Long userId(final JsonCommand command) {
        if (command.commandId() != null) {
            final List<Long> maker = this.jdbcTemplate.queryForList("SELECT maker_id FROM m_portfolio_command_source WHERE id = ?",
                    Long.class, command.commandId());
            if (!maker.isEmpty() && maker.getFirst() != null) {
                return maker.getFirst();
            }
        }
        return this.context.authenticatedUser().getId();
    }
}
