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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.service.ApprovalWorkflowDecision;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.workflow.WorkflowTestFixtures;
import org.apache.fineract.workflow.domain.WorkflowApprovalAction;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowDefinitionStatus;
import org.apache.fineract.workflow.domain.WorkflowInstance;
import org.apache.fineract.workflow.domain.WorkflowInstanceActionRepository;
import org.apache.fineract.workflow.domain.WorkflowInstanceRepository;
import org.apache.fineract.workflow.domain.WorkflowInstanceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowInstanceProcessServiceImplTest {

    @Mock
    private WorkflowTenantConfiguration tenantConfiguration;

    @Mock
    private WorkflowInstanceRepository workflowInstanceRepository;

    @Mock
    private WorkflowInstanceActionRepository workflowInstanceActionRepository;

    @Mock
    private WorkflowStageAuthorizationService stageAuthorizationService;

    @InjectMocks
    private WorkflowInstanceProcessServiceImpl service;

    private CommandSource commandSource;
    private AppUser checker;
    private WorkflowInstance instance;
    private WorkflowDefinition definition;

    @BeforeEach
    void setUp() {
        commandSource = CommandSource.builder().actionName("APPROVE").entityName("LOAN").build();
        commandSource.setId(42L);
        checker = org.mockito.Mockito.mock(AppUser.class);
        when(checker.getId()).thenReturn(99L);
        when(checker.isCheckerSuperUser()).thenReturn(false);

        definition = WorkflowTestFixtures.linearTwoStageDefinitionForApproveLoan();
        definition.setId(7L);
        definition.setStatus(WorkflowDefinitionStatus.ACTIVE);
        instance = WorkflowInstance.create(42L, definition, "BRANCH_MANAGER", null, null);
        instance.setId(1L);
    }

    @Test
    void returnsNotApplicableWhenEngineDisabled() {
        when(this.tenantConfiguration.isApprovalWorkflowsEnabled()).thenReturn(false);

        assertEquals(ApprovalWorkflowDecision.NOT_APPLICABLE, this.service.processCheckerApprove(commandSource, checker));
    }

    @Test
    void superUserBypassesStagesOnApprove() {
        when(this.tenantConfiguration.isApprovalWorkflowsEnabled()).thenReturn(true);
        when(checker.isCheckerSuperUser()).thenReturn(true);

        assertEquals(ApprovalWorkflowDecision.PROCEED_TO_EXECUTE, this.service.processCheckerApprove(commandSource, checker));
        verify(this.workflowInstanceRepository, never()).findByCommandSourceIdWithDefinitionGraph(any());
    }

    @Test
    void intermediateStageApprovalAdvancesInstance() {
        when(this.tenantConfiguration.isApprovalWorkflowsEnabled()).thenReturn(true);
        when(this.workflowInstanceRepository.findByCommandSourceIdWithDefinitionGraph(42L)).thenReturn(Optional.of(instance));
        doNothing().when(this.stageAuthorizationService).validateActorMayApproveAtStage(eq(commandSource), eq(instance), eq(checker), any());
        when(this.workflowInstanceActionRepository.existsByWorkflowInstanceIdAndStageCodeAndActionAndCreatedBy(1L, "BRANCH_MANAGER",
                WorkflowApprovalAction.APPROVE, 99L)).thenReturn(false);
        when(this.workflowInstanceActionRepository.countDistinctActorsByInstanceAndStageAndAction(1L, "BRANCH_MANAGER",
                WorkflowApprovalAction.APPROVE)).thenReturn(1L);
        when(this.workflowInstanceRepository.saveAndFlush(any(WorkflowInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final ApprovalWorkflowDecision decision = this.service.processCheckerApprove(commandSource, checker);

        assertEquals(ApprovalWorkflowDecision.STAGE_RECORDED, decision);
        assertEquals("HEAD_OFFICE", instance.getCurrentStageCode());
        verify(this.workflowInstanceRepository).saveAndFlush(instance);
    }

    @Test
    void terminalStageApprovalCompletesWorkflowAwaitingSystemChecker() {
        instance.setCurrentStageCode("HEAD_OFFICE");
        when(this.tenantConfiguration.isApprovalWorkflowsEnabled()).thenReturn(true);
        when(this.workflowInstanceRepository.findByCommandSourceIdWithDefinitionGraph(42L)).thenReturn(Optional.of(instance));
        doNothing().when(this.stageAuthorizationService).validateActorMayApproveAtStage(eq(commandSource), eq(instance), eq(checker), any());
        when(this.workflowInstanceActionRepository.existsByWorkflowInstanceIdAndStageCodeAndActionAndCreatedBy(1L, "HEAD_OFFICE",
                WorkflowApprovalAction.APPROVE, 99L)).thenReturn(false);
        when(this.workflowInstanceActionRepository.countDistinctActorsByInstanceAndStageAndAction(1L, "HEAD_OFFICE",
                WorkflowApprovalAction.APPROVE)).thenReturn(1L);
        when(this.workflowInstanceRepository.saveAndFlush(any(WorkflowInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final ApprovalWorkflowDecision decision = this.service.processCheckerApprove(commandSource, checker);

        assertEquals(ApprovalWorkflowDecision.STAGE_RECORDED, decision);
        assertEquals(WorkflowInstanceStatus.COMPLETED, instance.getStatus());
    }

    @Test
    void rejectWithAnyPolicyRejectsWorkflow() {
        when(this.tenantConfiguration.isApprovalWorkflowsEnabled()).thenReturn(true);
        when(this.workflowInstanceRepository.findByCommandSourceIdWithDefinitionGraph(42L)).thenReturn(Optional.of(instance));
        doNothing().when(this.stageAuthorizationService).validateActorMayRejectAtStage(eq(commandSource), eq(instance), eq(checker), any());
        when(this.workflowInstanceActionRepository.countByWorkflowInstanceIdAndStageCodeAndAction(1L, "BRANCH_MANAGER",
                WorkflowApprovalAction.REJECT)).thenReturn(1L);
        when(this.workflowInstanceRepository.saveAndFlush(any(WorkflowInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final ApprovalWorkflowDecision decision = this.service.processCheckerReject(commandSource, checker);

        assertEquals(ApprovalWorkflowDecision.WORKFLOW_REJECTED, decision);
        assertEquals(WorkflowInstanceStatus.REJECTED, instance.getStatus());
    }
}
