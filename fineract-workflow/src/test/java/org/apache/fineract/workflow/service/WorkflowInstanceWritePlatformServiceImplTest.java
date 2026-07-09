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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.workflow.WorkflowTestFixtures;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowDefinitionStatus;
import org.apache.fineract.workflow.domain.WorkflowInstance;
import org.apache.fineract.workflow.domain.WorkflowInstanceRepository;
import org.apache.fineract.workflow.domain.WorkflowInstanceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowInstanceWritePlatformServiceImplTest {

    @Mock
    private WorkflowTenantConfiguration tenantConfiguration;

    @Mock
    private WorkflowSelectionService workflowSelectionService;

    @Mock
    private WorkflowCommandAmountExtractor amountExtractor;

    @Mock
    private WorkflowInstanceRepository workflowInstanceRepository;

    @InjectMocks
    private WorkflowInstanceWritePlatformServiceImpl service;

    private CommandSource commandSource;
    private JsonCommand jsonCommand;

    @BeforeEach
    void setUp() {
        commandSource = CommandSource.builder().actionName("APPROVE").entityName("LOAN").build();
        commandSource.setId(42L);
        jsonCommand = JsonCommand.from("{\"transactionAmount\":1000,\"currencyCode\":\"UGX\"}");
    }

    @Test
    void returnsEmptyWhenTenantWorkflowEngineDisabled() {
        when(this.tenantConfiguration.isApprovalWorkflowsEnabled()).thenReturn(false);

        final Optional<WorkflowInstance> result = this.service.createInstanceForHeldCommand(commandSource, jsonCommand);

        assertTrue(result.isEmpty());
        verify(this.workflowSelectionService, never()).selectWorkflow(any(), any(), any());
    }

    @Test
    void createsInstanceAtEntryStageWhenDefinitionMatches() {
        when(this.tenantConfiguration.isApprovalWorkflowsEnabled()).thenReturn(true);
        when(this.workflowInstanceRepository.existsByCommandSourceId(42L)).thenReturn(false);
        when(this.amountExtractor.extract(commandSource, jsonCommand))
                .thenReturn(new WorkflowCommandAmountContext(new BigDecimal("1000"), "UGX"));

        final WorkflowDefinition definition = WorkflowTestFixtures.linearTwoStageDefinitionForApproveLoan();
        definition.setId(7L);
        definition.setStatus(WorkflowDefinitionStatus.ACTIVE);
        when(this.workflowSelectionService.selectWorkflow(eq("APPROVE_LOAN"), eq(new BigDecimal("1000")), eq("UGX")))
                .thenReturn(Optional.of(definition));
        when(this.workflowInstanceRepository.saveAndFlush(any(WorkflowInstance.class))).thenAnswer(invocation -> {
            final WorkflowInstance instance = invocation.getArgument(0);
            instance.setId(99L);
            return instance;
        });

        final Optional<WorkflowInstance> result = this.service.createInstanceForHeldCommand(commandSource, jsonCommand);

        assertTrue(result.isPresent());
        assertEquals(99L, result.get().getId());
        assertEquals(42L, result.get().getCommandSourceId());
        assertEquals("BRANCH_MANAGER", result.get().getCurrentStageCode());
        assertEquals(WorkflowInstanceStatus.IN_PROGRESS, result.get().getStatus());
        assertEquals("APPROVE_LOAN", result.get().getTaskPermissionCode());

        final ArgumentCaptor<WorkflowInstance> captor = ArgumentCaptor.forClass(WorkflowInstance.class);
        verify(this.workflowInstanceRepository).saveAndFlush(captor.capture());
        assertEquals(new BigDecimal("1000"), captor.getValue().getTransactionAmount());
        assertEquals("UGX", captor.getValue().getCurrencyCode());
    }

    @Test
    void returnsExistingInstanceWhenAlreadyCreated() {
        when(this.tenantConfiguration.isApprovalWorkflowsEnabled()).thenReturn(true);
        final WorkflowInstance existing = new WorkflowInstance();
        existing.setId(5L);
        when(this.workflowInstanceRepository.existsByCommandSourceId(42L)).thenReturn(true);
        when(this.workflowInstanceRepository.findByCommandSourceId(42L)).thenReturn(Optional.of(existing));

        final Optional<WorkflowInstance> result = this.service.createInstanceForHeldCommand(commandSource, jsonCommand);

        assertTrue(result.isPresent());
        assertEquals(5L, result.get().getId());
        verify(this.workflowSelectionService, never()).selectWorkflow(any(), any(), any());
    }
}
