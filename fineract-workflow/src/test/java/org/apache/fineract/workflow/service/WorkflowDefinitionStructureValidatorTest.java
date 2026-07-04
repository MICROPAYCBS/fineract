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

import static org.apache.fineract.workflow.WorkflowTestFixtures.connect;
import static org.apache.fineract.workflow.WorkflowTestFixtures.definition;
import static org.apache.fineract.workflow.WorkflowTestFixtures.linearThreeStageDefinition;
import static org.apache.fineract.workflow.WorkflowTestFixtures.stage;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowExpiryPeriodUnit;
import org.apache.fineract.workflow.domain.WorkflowRejectionPolicy;
import org.apache.fineract.workflow.domain.WorkflowStage;
import org.apache.fineract.workflow.exception.WorkflowConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionStructureValidatorTest {

    private WorkflowDefinitionStructureValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WorkflowDefinitionStructureValidator();
    }

    @Test
    void validLinearWorkflowPassesValidation() {
        assertThatCode(() -> validator.validateForActivation(linearThreeStageDefinition())).doesNotThrowAnyException();
    }

    @Test
    void workflowWithoutStagesIsRejected() {
        final WorkflowDefinition definition = definition("CREATE_LOAN", "Empty", 0, null, null, null);

        assertThatThrownBy(() -> validator.validateForActivation(definition)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("at least one stage");
    }

    @Test
    void duplicateStageCodesAreRejected() {
        final WorkflowDefinition definition = definition("CREATE_LOAN", "Duplicate", 0, null, null, null);
        definition.addStage(stage("BRANCH_MANAGER"));
        definition.addStage(stage("BRANCH_MANAGER"));

        assertThatThrownBy(() -> validator.validateForActivation(definition)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("used more than once");
    }

    @Test
    void stageWithoutParticipantsIsRejected() {
        final WorkflowDefinition definition = definition("CREATE_LOAN", "No participants", 0, null, null, null);
        final WorkflowStage stage = stage("BRANCH_MANAGER");
        stage.getParticipants().clear();
        definition.addStage(stage);

        assertThatThrownBy(() -> validator.validateForActivation(definition)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("no participants");
    }

    @Test
    void stageWithoutApproveActionIsRejected() {
        final WorkflowDefinition definition = definition("CREATE_LOAN", "No approve", 0, null, null, null);
        final WorkflowStage stage = stage("BRANCH_MANAGER");
        stage.getActions().remove(0);
        definition.addStage(stage);

        assertThatThrownBy(() -> validator.validateForActivation(definition)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("APPROVE action");
    }

    @Test
    void circularTransitionsAreRejected() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        connect(definition, "HEAD_OFFICE", "BRANCH_MANAGER", 1);

        assertThatThrownBy(() -> validator.validateForActivation(definition)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("entry stage");
    }

    @Test
    void innerCycleIsDetected() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        // REGIONAL_MANAGER <-> HEAD_OFFICE cycle; BRANCH_MANAGER remains the entry stage
        connect(definition, "HEAD_OFFICE", "REGIONAL_MANAGER", 2);

        assertThatThrownBy(() -> validator.validateForActivation(definition)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("cycle");
    }

    @Test
    void selfTransitionIsRejected() {
        final WorkflowDefinition definition = definition("CREATE_LOAN", "Self", 0, null, null, null);
        definition.addStage(stage("BRANCH_MANAGER"));
        connect(definition, "BRANCH_MANAGER", "BRANCH_MANAGER", 1);

        assertThatThrownBy(() -> validator.validateForActivation(definition)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("cannot transition to itself");
    }

    @Test
    void multipleEntryStagesAreRejected() {
        final WorkflowDefinition definition = definition("CREATE_LOAN", "Two entries", 0, null, null, null);
        definition.addStage(stage("A"));
        definition.addStage(stage("B"));
        definition.addStage(stage("C"));
        connect(definition, "A", "C", 1);
        connect(definition, "B", "C", 1);

        assertThatThrownBy(() -> validator.validateForActivation(definition)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("multiple entry stages");
    }

    @Test
    void unreachableStageIsRejected() {
        final WorkflowDefinition definition = definition("CREATE_LOAN", "Unreachable", 0, null, null, null);
        definition.addStage(stage("A"));
        definition.addStage(stage("B"));
        definition.addStage(stage("C"));
        // A is the sole entry stage (B and C have incoming transitions), but B and C are not reachable from A.
        connect(definition, "B", "C", 1);
        connect(definition, "C", "B", 1);

        assertThatThrownBy(() -> validator.validateForActivation(definition)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("not reachable");
    }

    @Test
    void escalationWithoutTargetIsRejected() {
        final WorkflowDefinition definition = definition("CREATE_LOAN", "Escalation", 0, null, null, null);
        final WorkflowStage stage = stage("BRANCH_MANAGER");
        stage.setEscalationEnabled(true);
        stage.setExpiryPeriodUnit(WorkflowExpiryPeriodUnit.HOURS);
        stage.setExpiryPeriodValue(24);
        definition.addStage(stage);

        assertThatThrownBy(() -> validator.validateForActivation(definition)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("no escalation target");
    }

    @Test
    void escalationWithoutExpiryIsRejected() {
        final WorkflowDefinition definition = definition("CREATE_LOAN", "Escalation", 0, null, null, null);
        final WorkflowStage stage = stage("BRANCH_MANAGER");
        stage.setEscalationEnabled(true);
        stage.setEscalationTargetStageCode("REGIONAL_MANAGER");
        definition.addStage(stage);
        definition.addStage(stage("REGIONAL_MANAGER"));
        connect(definition, "BRANCH_MANAGER", "REGIONAL_MANAGER", 1);

        assertThatThrownBy(() -> validator.validateForActivation(definition)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("no expiry period");
    }

    @Test
    void escalationToUnknownStageIsRejected() {
        final WorkflowDefinition definition = definition("CREATE_LOAN", "Escalation", 0, null, null, null);
        final WorkflowStage stage = stage("BRANCH_MANAGER");
        stage.setEscalationEnabled(true);
        stage.setExpiryPeriodUnit(WorkflowExpiryPeriodUnit.HOURS);
        stage.setExpiryPeriodValue(24);
        stage.setEscalationTargetStageCode("NOT_A_STAGE");
        definition.addStage(stage);

        assertThatThrownBy(() -> validator.validateForActivation(definition)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("unknown stage");
    }

    @Test
    void thresholdPolicyRequiresThresholdValue() {
        final WorkflowDefinition definition = definition("CREATE_LOAN", "Threshold", 0, null, null, null);
        final WorkflowStage stage = stage("BRANCH_MANAGER");
        stage.setRejectionPolicy(WorkflowRejectionPolicy.THRESHOLD);
        definition.addStage(stage);

        assertThatThrownBy(() -> validator.validateForActivation(definition)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("rejection threshold");
    }

    @Test
    void overlappingCriteriaAtSamePriorityAreRejected() {
        final WorkflowDefinition candidate = definition("CREATE_LOAN", "Large loans", 10, "UGX", new BigDecimal("5000000"), null);
        candidate.setId(1L);
        final WorkflowDefinition existing = definition("CREATE_LOAN", "Medium loans", 10, "UGX", new BigDecimal("1000000"),
                new BigDecimal("10000000"));
        existing.setId(2L);

        assertThatThrownBy(() -> validator.validateNoAmbiguousSelection(candidate, List.of(existing))) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("overlaps");
    }

    @Test
    void disjointAmountBandsAreAllowed() {
        final WorkflowDefinition candidate = definition("CREATE_LOAN", "Large loans", 10, "UGX", new BigDecimal("5000000"), null);
        candidate.setId(1L);
        final WorkflowDefinition existing = definition("CREATE_LOAN", "Small loans", 10, "UGX", null, new BigDecimal("4999999"));
        existing.setId(2L);

        assertThatCode(() -> validator.validateNoAmbiguousSelection(candidate, List.of(existing))).doesNotThrowAnyException();
    }

    @Test
    void criteriaWorkflowDoesNotConflictWithDefaultWorkflow() {
        final WorkflowDefinition candidate = definition("CREATE_LOAN", "Large loans", 10, "UGX", new BigDecimal("5000000"), null);
        candidate.setId(1L);
        final WorkflowDefinition defaultWorkflow = definition("CREATE_LOAN", "Default", 10, null, null, null);
        defaultWorkflow.setId(2L);

        assertThatCode(() -> validator.validateNoAmbiguousSelection(candidate, List.of(defaultWorkflow))).doesNotThrowAnyException();
    }

    @Test
    void twoDefaultWorkflowsAtSamePriorityAreRejected() {
        final WorkflowDefinition candidate = definition("CREATE_LOAN", "Default A", 10, null, null, null);
        candidate.setId(1L);
        final WorkflowDefinition existing = definition("CREATE_LOAN", "Default B", 10, null, null, null);
        existing.setId(2L);

        assertThatThrownBy(() -> validator.validateNoAmbiguousSelection(candidate, List.of(existing))) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("overlaps");
    }

    @Test
    void differentCurrenciesDoNotOverlap() {
        final WorkflowDefinition candidate = definition("CREATE_LOAN", "UGX loans", 10, "UGX", new BigDecimal("5000000"), null);
        candidate.setId(1L);
        final WorkflowDefinition existing = definition("CREATE_LOAN", "USD loans", 10, "USD", new BigDecimal("1000"), null);
        existing.setId(2L);

        assertThatCode(() -> validator.validateNoAmbiguousSelection(candidate, List.of(existing))).doesNotThrowAnyException();
    }
}
