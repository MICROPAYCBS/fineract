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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.util.List;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TreasuryActorTest {

    private static final long CHECKER = 13L;
    private static final long MAKER = 16L;

    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TreasuryActor actor = new TreasuryActor(this.context, this.jdbcTemplate);

    @BeforeEach
    void setUp() {
        final AppUser checker = mock(AppUser.class);
        when(checker.getId()).thenReturn(CHECKER);
        when(this.context.authenticatedUser()).thenReturn(checker);
    }

    @Test
    void approvedMakerCheckerCommandBelongsToItsMaker() {
        when(this.jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(1738L))).thenReturn(List.of(MAKER));

        assertEquals(MAKER, this.actor.userId(existingCommand(1738L)));
    }

    @Test
    void directCommandBelongsToTheCurrentUser() {
        assertEquals(CHECKER, this.actor.userId(JsonCommand.from("{}")));
        verify(this.jdbcTemplate, never()).queryForList(anyString(), eq(Long.class), eq(1738L));
    }

    @Test
    void unknownCommandFallsBackToTheCurrentUser() {
        when(this.jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(99L))).thenReturn(List.of());

        assertEquals(CHECKER, this.actor.userId(existingCommand(99L)));
    }

    private static JsonCommand existingCommand(final Long commandId) {
        return JsonCommand.fromExistingCommand(commandId, "{}", JsonParser.parseString("{}"), new FromJsonHelper(), "TREASURYTRANSFER",
                null, null, null, null, null, null, null, null);
    }
}
