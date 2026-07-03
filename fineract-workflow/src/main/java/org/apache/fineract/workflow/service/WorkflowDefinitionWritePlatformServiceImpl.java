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
import org.apache.fineract.workflow.data.WorkflowDefinitionRequest;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowDefinitionRepository;
import org.apache.fineract.workflow.domain.WorkflowDefinitionStatus;
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
    private final WorkflowDefinitionDataValidator dataValidator;
    private final WorkflowDefinitionAssembler assembler;
    private final WorkflowDefinitionStructureValidator structureValidator;

    @Override
    public CommandProcessingResult create(final JsonCommand command) {
        final WorkflowDefinitionRequest request = this.dataValidator.validateAndParse(command.json());

        if (this.workflowDefinitionRepository.existsByModuleNameAndNameIgnoreCase(request.getModuleName(), request.getName())) {
            throw new WorkflowConfigurationException("duplicate.name",
                    "A workflow named " + request.getName() + " already exists for module " + request.getModuleName(), request.getName(),
                    request.getModuleName());
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
        if (!definition.isDraft()) {
            throw new WorkflowDefinitionStateException("updated", definitionId, definition.getStatus().name());
        }

        final WorkflowDefinitionRequest request = this.dataValidator.validateAndParse(command.json());
        this.assembler.assembleUpdate(definition, request);
        this.workflowDefinitionRepository.saveAndFlush(definition);

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(definition.getId()) //
                .build();
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
        final List<WorkflowDefinition> activeDefinitions = this.workflowDefinitionRepository
                .findByModuleNameAndStatus(definition.getModuleName(), WorkflowDefinitionStatus.ACTIVE);
        this.structureValidator.validateNoAmbiguousSelection(definition, activeDefinitions);

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
}
