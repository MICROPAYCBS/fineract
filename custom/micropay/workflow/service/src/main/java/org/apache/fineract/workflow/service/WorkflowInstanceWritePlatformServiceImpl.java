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

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowInstance;
import org.apache.fineract.workflow.domain.WorkflowInstanceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class WorkflowInstanceWritePlatformServiceImpl implements WorkflowInstanceWritePlatformService {

    private final WorkflowTenantConfiguration tenantConfiguration;
    private final WorkflowSelectionService workflowSelectionService;
    private final WorkflowInstanceRepository workflowInstanceRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<WorkflowInstance> createInstanceForHeldCommand(final CommandSource commandSource, final JsonCommand command) {
        if (!this.tenantConfiguration.isApprovalWorkflowsEnabled()) {
            return Optional.empty();
        }

        final Long commandSourceId = commandSource.getId();
        if (commandSourceId == null) {
            log.warn("Skipping workflow instance creation because command source id is not yet assigned");
            return Optional.empty();
        }

        if (this.workflowInstanceRepository.existsByCommandSourceId(commandSourceId)) {
            return this.workflowInstanceRepository.findByCommandSourceId(commandSourceId);
        }

        final String taskPermissionCode = commandSource.getPermissionCode();
        final Optional<WorkflowDefinition> selectedDefinition = this.workflowSelectionService.selectWorkflow(taskPermissionCode);
        if (selectedDefinition.isEmpty()) {
            return Optional.empty();
        }

        final WorkflowDefinition definition = selectedDefinition.get();
        final Optional<String> entryStageCode = definition.findEntryStageCode();
        if (entryStageCode.isEmpty()) {
            log.warn("Active workflow definition {} has no entry stage; skipping instance for command {}", definition.getId(),
                    commandSourceId);
            return Optional.empty();
        }

        final WorkflowInstance instance = WorkflowInstance.create(commandSourceId, definition, entryStageCode.get());
        return Optional.of(this.workflowInstanceRepository.saveAndFlush(instance));
    }
}
