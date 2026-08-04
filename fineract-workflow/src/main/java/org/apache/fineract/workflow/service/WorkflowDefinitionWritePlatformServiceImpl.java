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

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.useradministration.domain.Permission;
import org.apache.fineract.workflow.data.WorkflowDefinitionRequest;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowDefinitionRepository;
import org.apache.fineract.workflow.domain.WorkflowDefinitionStatus;
import org.apache.fineract.workflow.domain.WorkflowInstanceRepository;
import org.apache.fineract.workflow.domain.WorkflowInstanceStatus;
import org.apache.fineract.workflow.domain.WorkflowPermissionRepository;
import org.apache.fineract.workflow.exception.WorkflowConfigurationException;
import org.apache.fineract.workflow.exception.WorkflowDefinitionNotFoundException;
import org.apache.fineract.workflow.exception.WorkflowDefinitionStateException;
import org.apache.fineract.workflow.serialization.WorkflowDefinitionDataValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class WorkflowDefinitionWritePlatformServiceImpl implements WorkflowDefinitionWritePlatformService {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final WorkflowPermissionRepository permissionRepository;
    private final WorkflowDefinitionDataValidator dataValidator;
    private final WorkflowDefinitionAssembler assembler;
    private final WorkflowDefinitionStructureValidator structureValidator;

    @Override
    public CommandProcessingResult create(final JsonCommand command) {
        final WorkflowDefinitionRequest request = this.dataValidator.validateAndParse(command.json());
        validateTaskExists(request.getTaskPermissionCode());

        if (this.workflowDefinitionRepository.existsByTaskPermissionCodeAndNameIgnoreCase(request.getTaskPermissionCode(),
                request.getName())) {
            throw new WorkflowConfigurationException("duplicate.name",
                    "A workflow named " + request.getName() + " already exists for task " + request.getTaskPermissionCode(),
                    request.getName(), request.getTaskPermissionCode());
        }

        final WorkflowDefinition definition = this.assembler.assembleNew(request);
        this.workflowDefinitionRepository.saveAndFlush(definition);

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(definition.getId()) //
                .build();
    }

    @Override
    public CommandProcessingResult update(final Long definitionId, final JsonCommand command) {
        final WorkflowDefinition definition = findDefinition(definitionId);
        assertDefinitionMayBeUpdated(definition);

        final WorkflowDefinitionRequest request = this.dataValidator.validateAndParse(command.json());
        validateTaskExists(request.getTaskPermissionCode());
        this.assembler.assembleUpdate(definition, request);

        if (definition.isActive()) {
            this.structureValidator.validateForActivation(definition);
            validateSingleActivePerTask(definition);
        }

        this.workflowDefinitionRepository.saveAndFlush(definition);

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(definition.getId()) //
                .build();
    }

    /**
     * DRAFT may always be replaced. ACTIVE and INACTIVE may be replaced only when no IN_PROGRESS instances still
     * resolve stages live from this definition.
     */
    private void assertDefinitionMayBeUpdated(final WorkflowDefinition definition) {
        if (definition.isDraft()) {
            return;
        }
        final long inProgressCount = this.workflowInstanceRepository.countByWorkflowDefinitionIdAndStatus(definition.getId(),
                WorkflowInstanceStatus.IN_PROGRESS);
        if (inProgressCount > 0) {
            throw WorkflowDefinitionStateException.cannotUpdateWithInProgressInstances(definition.getId(), inProgressCount);
        }
    }

    @Override
    public CommandProcessingResult delete(final Long definitionId) {
        final WorkflowDefinition definition = findDefinition(definitionId);
        if (!definition.isDraft()) {
            throw new WorkflowDefinitionStateException("deleted", definitionId, definition.getStatus().name());
        }
        this.workflowDefinitionRepository.delete(definition);

        return new CommandProcessingResultBuilder() //
                .withEntityId(definitionId) //
                .build();
    }

    @Override
    public CommandProcessingResult activate(final Long definitionId) {
        final WorkflowDefinition definition = findDefinition(definitionId);
        if (definition.isActive()) {
            throw new WorkflowDefinitionStateException("activated", definitionId, definition.getStatus().name());
        }

        this.structureValidator.validateForActivation(definition);
        validateTaskIsMakerCheckerEnabled(definition.getTaskPermissionCode());
        validateSingleActivePerTask(definition);

        definition.setStatus(WorkflowDefinitionStatus.ACTIVE);
        this.workflowDefinitionRepository.saveAndFlush(definition);

        return new CommandProcessingResultBuilder() //
                .withEntityId(definitionId) //
                .build();
    }

    @Override
    public CommandProcessingResult deactivate(final Long definitionId) {
        final WorkflowDefinition definition = findDefinition(definitionId);
        if (!definition.isActive()) {
            throw new WorkflowDefinitionStateException("deactivated", definitionId, definition.getStatus().name());
        }

        definition.setStatus(WorkflowDefinitionStatus.INACTIVE);
        this.workflowDefinitionRepository.saveAndFlush(definition);

        return new CommandProcessingResultBuilder() //
                .withEntityId(definitionId) //
                .build();
    }

    private WorkflowDefinition findDefinition(final Long definitionId) {
        return this.workflowDefinitionRepository.findById(definitionId)
                .orElseThrow(() -> new WorkflowDefinitionNotFoundException(definitionId));
    }

    private Permission validateTaskExists(final String taskPermissionCode) {
        return this.permissionRepository.findOneByCode(taskPermissionCode)
                .orElseThrow(() -> new WorkflowConfigurationException("unknown.task",
                        "Task " + taskPermissionCode + " does not match any maker-checker task (permission code)", taskPermissionCode));
    }

    /**
     * A workflow only governs commands that the maker-checker pipeline holds, so activation requires maker-checker to
     * be enabled for the task; otherwise commands would execute immediately and the workflow would never run.
     */
    private void validateTaskIsMakerCheckerEnabled(final String taskPermissionCode) {
        final Permission permission = validateTaskExists(taskPermissionCode);
        if (!permission.hasMakerCheckerEnabled()) {
            throw new WorkflowConfigurationException("task.not.maker.checker.enabled", "Maker-checker is not enabled for task "
                    + taskPermissionCode + "; enable it before activating a workflow for this task", taskPermissionCode);
        }
    }

    private void validateSingleActivePerTask(final WorkflowDefinition definition) {
        final List<WorkflowDefinition> activeDefinitions = this.workflowDefinitionRepository
                .findByTaskPermissionCodeAndStatus(definition.getTaskPermissionCode(), WorkflowDefinitionStatus.ACTIVE);
        this.structureValidator.validateSingleActivePerTask(definition, activeDefinitions);
    }
}
