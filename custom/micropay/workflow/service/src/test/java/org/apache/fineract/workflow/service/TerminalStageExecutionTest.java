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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.workflow.WorkflowTestFixtures;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowInstance;
import org.apache.fineract.workflow.domain.WorkflowInstanceRepository;
import org.apache.fineract.workflow.domain.WorkflowInstanceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerminalStageExecutionTest {

    @Mock
    private WorkflowInstanceRepository workflowInstanceRepository;

    @Test
    void completeIfArmedMarksInProgressInstanceCompleted() {
        final WorkflowInstance instance = inProgressInstance();
        when(this.workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(instance));
        when(this.workflowInstanceRepository.saveAndFlush(instance)).thenReturn(instance);
        final TerminalStageExecution execution = new TerminalStageExecution(this.workflowInstanceRepository);
        execution.arm(1L);

        execution.completeIfArmed();

        assertEquals(WorkflowInstanceStatus.COMPLETED, instance.getStatus());
        verify(this.workflowInstanceRepository).saveAndFlush(instance);
    }

    @Test
    void disarmLeavesInstanceInProgressWhenExecutionFails() {
        final WorkflowInstance instance = inProgressInstance();
        final TerminalStageExecution execution = new TerminalStageExecution(this.workflowInstanceRepository);
        execution.arm(1L);

        execution.disarm();
        execution.completeIfArmed();

        assertEquals(WorkflowInstanceStatus.IN_PROGRESS, instance.getStatus());
        verify(this.workflowInstanceRepository, never()).findById(1L);
    }

    private static WorkflowInstance inProgressInstance() {
        final WorkflowDefinition definition = WorkflowTestFixtures.linearTwoStageDefinitionForApproveLoan();
        final WorkflowInstance instance = WorkflowInstance.create(42L, definition, "HEAD_OFFICE");
        instance.setId(1L);
        return instance;
    }
}
