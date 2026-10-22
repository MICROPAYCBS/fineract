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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.treasury.domain.TreasuryBreach;
import org.apache.fineract.treasury.domain.TreasuryBreachComment;
import org.apache.fineract.treasury.domain.TreasuryBreachCommentRepository;
import org.apache.fineract.treasury.domain.TreasuryBreachRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.limits.BreachStatus;
import org.apache.fineract.treasury.serialization.TreasuryLimitDataValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Acknowledge, comment, resolve: the resolution needs a comment and is final. */
class TreasuryBreachServiceTest {

    private final TreasuryActor actor = mock(TreasuryActor.class);
    private final TreasuryBreachRepository breaches = mock(TreasuryBreachRepository.class);
    private final TreasuryBreachCommentRepository comments = mock(TreasuryBreachCommentRepository.class);
    private final TreasuryBreachService service = new TreasuryBreachService(new TreasuryLimitDataValidator(new FromJsonHelper()),
            this.actor, this.breaches, this.comments, mock(NamedParameterJdbcTemplate.class));
    private final TreasuryBreach breach = new TreasuryBreach();
    private final List<TreasuryBreachComment> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", mock(FineractPlatformTenantConnection.class)));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 10, 16))));
        this.breach.setId(100L);
        this.breach.setStatus(BreachStatus.OPEN);
        when(this.breaches.findById(100L)).thenReturn(Optional.of(this.breach));
        lenient().when(this.actor.userId(any())).thenReturn(7L);
        lenient().when(this.comments.saveAndFlush(any())).thenAnswer(call -> {
            final TreasuryBreachComment comment = call.getArgument(0);
            comment.setId(10L + this.saved.size());
            this.saved.add(comment);
            return comment;
        });
        lenient().when(this.comments.findByBreachIdOrderByIdAsc(100L)).thenAnswer(call -> this.saved);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void acknowledgeThenCommentThenResolve() {
        assertEquals("ACKNOWLEDGED", this.service.acknowledge(100L, JsonCommand.from("{}")).getChanges().get("status"));
        assertEquals(7L, this.breach.getAcknowledgedBy());
        assertEquals(0, this.saved.size(), "no comment sent");
        assertCode("not.open", () -> this.service.acknowledge(100L, JsonCommand.from("{}")));
        assertEquals(1, this.service.comment(100L, JsonCommand.from("{\"comment\": \"Funding line drawn\"}")).getChanges().get("comments"));
        assertEquals("USER", this.saved.getFirst().getKind());
        when(this.actor.userId(any())).thenReturn(8L);
        this.service.resolve(100L, JsonCommand.from("{\"comment\": \"Loans sold down; ratio back below 80%\"}"));
        assertEquals(BreachStatus.RESOLVED, this.breach.getStatus());
        assertEquals(8L, this.breach.getResolvedBy());
        assertEquals(LocalDate.of(2026, 10, 16), this.breach.getResolvedOnDate());
        assertEquals("Loans sold down; ratio back below 80%", this.breach.getResolutionComment());
        assertEquals("RESOLVE", this.saved.getLast().getKind());
        assertCode("already.resolved", () -> this.service.resolve(100L, JsonCommand.from("{\"comment\": \"again\"}")));
        this.service.comment(100L, JsonCommand.from("{\"comment\": \"Note after resolution\"}"));
        assertEquals(3, this.saved.size());
    }

    @Test
    void anOpenBreachCanBeResolvedByTheAcknowledgerButNotWithoutAComment() {
        assertThrows(PlatformApiDataValidationException.class, () -> this.service.resolve(100L, JsonCommand.from("{}")));
        assertThrows(PlatformApiDataValidationException.class, () -> this.service.resolve(100L, JsonCommand.from("{\"comment\": \"  \"}")));
        assertThrows(PlatformApiDataValidationException.class, () -> this.service.comment(100L, JsonCommand.from("{}")));
        this.service.acknowledge(100L, JsonCommand.from("{\"comment\": \"Looking into it\"}"));
        assertEquals("ACKNOWLEDGE", this.saved.getFirst().getKind());
        this.service.resolve(100L, JsonCommand.from("{\"comment\": \"Done\"}"));
        assertEquals(this.breach.getAcknowledgedBy(), this.breach.getResolvedBy());
    }

    @Test
    void anUnknownBreach() {
        when(this.breaches.findById(5L)).thenReturn(Optional.empty());
        assertCode("not.found", () -> this.service.acknowledge(5L, JsonCommand.from("{}")));
    }

    private static void assertCode(final String suffix, final org.junit.jupiter.api.function.Executable call) {
        assertEquals("error.msg.treasury.breach." + suffix,
                assertThrows(TreasuryWorkflowException.class, call).getGlobalisationMessageCode());
    }
}
