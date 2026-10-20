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
package org.apache.fineract.workflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.workflow.WorkflowTestFixtures;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowStageRouterTest {

    private WorkflowDefinition definition;
    private CommandSource commandSource;

    @BeforeEach
    void setUp() {
        definition = WorkflowTestFixtures.definition("APPROVE_LOAN", "Loan approval", 0);
        for (final String code : List.of("CREDIT_OFFICER", "BRANCH_MANAGER", "HEAD_OF_CREDIT", "CREDIT_COMMITTEE")) {
            definition.addStage(WorkflowTestFixtures.stage(code));
        }
        // added out of order on purpose: the chain follows transitions, not insertion order
        WorkflowTestFixtures.connect(definition, "HEAD_OF_CREDIT", "CREDIT_COMMITTEE", 1);
        WorkflowTestFixtures.connect(definition, "CREDIT_OFFICER", "BRANCH_MANAGER", 1);
        WorkflowTestFixtures.connect(definition, "BRANCH_MANAGER", "HEAD_OF_CREDIT", 1);
        commandSource = CommandSource.builder().actionName("APPROVE").entityName("LOAN").loanId(5L).build();
        commandSource.setId(42L);
    }

    @Test
    void withoutApplicabilityEveryStageApplies() {
        final WorkflowStageRouter router = WorkflowStageRouter.withoutSkips();

        assertEquals(List.of("CREDIT_OFFICER", "BRANCH_MANAGER", "HEAD_OF_CREDIT", "CREDIT_COMMITTEE"), router.chain(definition));
        assertEquals(Optional.of("CREDIT_OFFICER"), router.firstStage(definition, commandSource));
        assertEquals(Optional.of("BRANCH_MANAGER"), router.nextStage(definition, "CREDIT_OFFICER", commandSource));
        assertEquals(Optional.empty(), router.nextStage(definition, "CREDIT_COMMITTEE", commandSource));
    }

    @Test
    void skippedStagesArePassedOver() {
        final WorkflowStageRouter router = router((source, def) -> Set.of("BRANCH_MANAGER", "HEAD_OF_CREDIT"));

        assertEquals(Optional.of("CREDIT_COMMITTEE"), router.nextStage(definition, "CREDIT_OFFICER", commandSource));
    }

    @Test
    void skippedEntryStageStartsAtFirstStageThatApplies() {
        final WorkflowStageRouter router = router((source, def) -> Set.of("CREDIT_OFFICER"));

        assertEquals(Optional.of("BRANCH_MANAGER"), router.firstStage(definition, commandSource));
    }

    @Test
    void lastStageThatAppliesIsTerminal() {
        final WorkflowStageRouter router = router((source, def) -> Set.of("HEAD_OF_CREDIT", "CREDIT_COMMITTEE"));

        assertEquals(Optional.empty(), router.nextStage(definition, "BRANCH_MANAGER", commandSource));
    }

    @Test
    void entryStageIsKeptWhenEveryStageIsSkipped() {
        final WorkflowStageRouter router = router(
                (source, def) -> Set.of("CREDIT_OFFICER", "BRANCH_MANAGER", "HEAD_OF_CREDIT", "CREDIT_COMMITTEE"));

        assertEquals(Optional.of("CREDIT_OFFICER"), router.firstStage(definition, commandSource));
        assertEquals(Optional.empty(), router.nextStage(definition, "CREDIT_OFFICER", commandSource));
    }

    @Test
    void skipsFromSeveralApplicabilitiesAreCombined() {
        final WorkflowStageRouter router = new WorkflowStageRouter(
                () -> List.of((source, def) -> Set.of("BRANCH_MANAGER"), (source, def) -> Set.of("HEAD_OF_CREDIT")));

        assertEquals(Optional.of("CREDIT_COMMITTEE"), router.nextStage(definition, "CREDIT_OFFICER", commandSource));
    }

    @Test
    void failingApplicabilityKeepsEveryStage() {
        final WorkflowStageRouter router = new WorkflowStageRouter(() -> List.of((source, def) -> Set.of("BRANCH_MANAGER"),
                (source, def) -> {
                    throw new IllegalStateException("authority rules unavailable");
                }));

        assertEquals(Optional.of("BRANCH_MANAGER"), router.nextStage(definition, "CREDIT_OFFICER", commandSource));
    }

    private static WorkflowStageRouter router(final WorkflowStageApplicability applicability) {
        return new WorkflowStageRouter(() -> List.of(applicability));
    }
}
