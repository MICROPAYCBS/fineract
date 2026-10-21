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

import static org.apache.fineract.workflow.api.WorkflowApiConstants.MODULE_ENABLED_PROPERTY;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.service.ApprovalWorkflowDecision;
import org.apache.fineract.commands.service.ApprovalWorkflowHook;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class WorkflowApprovalHookImpl implements ApprovalWorkflowHook {

    private final WorkflowInstanceWritePlatformService workflowInstanceWritePlatformService;
    private final WorkflowInstanceProcessService workflowInstanceProcessService;

    @Override
    public void onCommandAwaitingApproval(final CommandSource commandSource, final JsonCommand command) {
        this.workflowInstanceWritePlatformService.createInstanceForHeldCommand(commandSource, command);
    }

    @Override
    public ApprovalWorkflowDecision onCheckerApprove(final CommandSource commandSource, final AppUser checker) {
        return this.workflowInstanceProcessService.processCheckerApprove(commandSource, checker);
    }

    @Override
    public ApprovalWorkflowDecision onCheckerReject(final CommandSource commandSource, final AppUser checker) {
        return this.workflowInstanceProcessService.processCheckerReject(commandSource, checker);
    }
}
