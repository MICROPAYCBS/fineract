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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.service.ApprovalWorkflowDecision;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowApprovalHookImplTest {

    @Mock
    private WorkflowInstanceWritePlatformService workflowInstanceWritePlatformService;

    @Mock
    private WorkflowInstanceProcessService workflowInstanceProcessService;

    @InjectMocks
    private WorkflowApprovalHookImpl hook;

    @Test
    void delegatesHeldCommandToInstanceWriteService() {
        final CommandSource commandSource = CommandSource.builder().actionName("DISBURSE").entityName("LOAN").build();
        commandSource.setId(10L);
        final JsonCommand command = JsonCommand.from("{\"transactionAmount\":500}");

        this.hook.onCommandAwaitingApproval(commandSource, command);

        verify(this.workflowInstanceWritePlatformService).createInstanceForHeldCommand(commandSource, command);
    }

    @Test
    void delegatesCheckerApproveToProcessService() {
        final CommandSource commandSource = CommandSource.builder().actionName("APPROVE").entityName("LOAN").build();
        final AppUser checker = org.mockito.Mockito.mock(AppUser.class);
        when(this.workflowInstanceProcessService.processCheckerApprove(commandSource, checker))
                .thenReturn(ApprovalWorkflowDecision.STAGE_RECORDED);

        assertEquals(ApprovalWorkflowDecision.STAGE_RECORDED, this.hook.onCheckerApprove(commandSource, checker));
    }
}
