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

import static org.apache.fineract.workflow.WorkflowTestFixtures.linearThreeStageDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowDefinitionWritePlatformServiceImplTest {

    @Mock
    private WorkflowDefinitionRepository repository;

    @Mock
    private WorkflowInstanceRepository workflowInstanceRepository;

    @Mock
    private WorkflowPermissionRepository permissionRepository;

    @Mock
    private WorkflowDefinitionDataValidator dataValidator;

    @Mock
    private WorkflowDefinitionAssembler assembler;

    @Mock
    private WorkflowDefinitionStructureValidator structureValidator;

    @Mock
    private Permission permission;

    @InjectMocks
    private WorkflowDefinitionWritePlatformServiceImpl writeService;

    @Test
    void updateSucceedsForDraftDefinition() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        definition.setStatus(WorkflowDefinitionStatus.DRAFT);
        final WorkflowDefinitionRequest request = updateRequest(definition);
        final JsonCommand command = JsonCommand.from("{}");

        when(repository.findById(7L)).thenReturn(Optional.of(definition));
        when(dataValidator.validateAndParse(anyString())).thenReturn(request);
        when(permissionRepository.findOneByCode(request.getTaskPermissionCode())).thenReturn(Optional.of(permission));

        writeService.update(7L, command);

        verify(assembler).assembleUpdate(definition, request);
        verify(repository).saveAndFlush(definition);
        verify(workflowInstanceRepository, never()).countByWorkflowDefinitionIdAndStatus(any(), any());
        verify(structureValidator, never()).validateForActivation(any());
        assertThat(definition.getStatus()).isEqualTo(WorkflowDefinitionStatus.DRAFT);
    }

    @Test
    void updateSucceedsForActiveDefinitionWhenNoInProgressInstances() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        definition.setStatus(WorkflowDefinitionStatus.ACTIVE);
        final WorkflowDefinitionRequest request = updateRequest(definition);
        final JsonCommand command = JsonCommand.from("{}");

        when(repository.findById(7L)).thenReturn(Optional.of(definition));
        when(workflowInstanceRepository.countByWorkflowDefinitionIdAndStatus(7L, WorkflowInstanceStatus.IN_PROGRESS)).thenReturn(0L);
        when(dataValidator.validateAndParse(anyString())).thenReturn(request);
        when(permissionRepository.findOneByCode(request.getTaskPermissionCode())).thenReturn(Optional.of(permission));

        when(repository.findByTaskPermissionCodeAndStatus(request.getTaskPermissionCode(), WorkflowDefinitionStatus.ACTIVE))
                .thenReturn(List.of(definition));

        writeService.update(7L, command);

        verify(assembler).assembleUpdate(definition, request);
        verify(repository).saveAndFlush(definition);
        verify(structureValidator).validateForActivation(definition);
        verify(structureValidator).validateSingleActivePerTask(definition, List.of(definition));
        assertThat(definition.getStatus()).isEqualTo(WorkflowDefinitionStatus.ACTIVE);
    }

    @Test
    void updateIsRejectedForActiveDefinitionWhenInProgressInstancesExist() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        definition.setStatus(WorkflowDefinitionStatus.ACTIVE);
        when(repository.findById(7L)).thenReturn(Optional.of(definition));
        when(workflowInstanceRepository.countByWorkflowDefinitionIdAndStatus(7L, WorkflowInstanceStatus.IN_PROGRESS)).thenReturn(3L);

        assertThatThrownBy(() -> writeService.update(7L, JsonCommand.from("{}"))) //
                .isInstanceOf(WorkflowDefinitionStateException.class) //
                .hasMessageContaining("IN_PROGRESS");
        verify(assembler, never()).assembleUpdate(any(), any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateSucceedsForInactiveDefinitionWhenNoInProgressInstances() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        definition.setStatus(WorkflowDefinitionStatus.INACTIVE);
        final WorkflowDefinitionRequest request = updateRequest(definition);
        final JsonCommand command = JsonCommand.from("{}");

        when(repository.findById(7L)).thenReturn(Optional.of(definition));
        when(workflowInstanceRepository.countByWorkflowDefinitionIdAndStatus(7L, WorkflowInstanceStatus.IN_PROGRESS)).thenReturn(0L);
        when(dataValidator.validateAndParse(anyString())).thenReturn(request);
        when(permissionRepository.findOneByCode(request.getTaskPermissionCode())).thenReturn(Optional.of(permission));

        writeService.update(7L, command);

        verify(assembler).assembleUpdate(definition, request);
        verify(repository).saveAndFlush(definition);
        verify(structureValidator, never()).validateForActivation(any());
        assertThat(definition.getStatus()).isEqualTo(WorkflowDefinitionStatus.INACTIVE);
    }

    @Test
    void updateIsRejectedForInactiveDefinitionWhenInProgressInstancesExist() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        definition.setStatus(WorkflowDefinitionStatus.INACTIVE);
        when(repository.findById(7L)).thenReturn(Optional.of(definition));
        when(workflowInstanceRepository.countByWorkflowDefinitionIdAndStatus(eq(7L), eq(WorkflowInstanceStatus.IN_PROGRESS)))
                .thenReturn(1L);

        assertThatThrownBy(() -> writeService.update(7L, JsonCommand.from("{}"))) //
                .isInstanceOf(WorkflowDefinitionStateException.class);
        verify(assembler, never()).assembleUpdate(any(), any());
    }

    @Test
    void deleteIsRejectedWhenDefinitionIsNotDraft() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        definition.setStatus(WorkflowDefinitionStatus.INACTIVE);
        when(repository.findById(7L)).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> writeService.delete(7L)) //
                .isInstanceOf(WorkflowDefinitionStateException.class);
        verify(repository, never()).delete(any(WorkflowDefinition.class));
    }

    @Test
    void activationValidatesStructureAndSingleActivePerTask() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(definition));
        when(permissionRepository.findOneByCode(definition.getTaskPermissionCode())).thenReturn(Optional.of(permission));
        when(permission.hasMakerCheckerEnabled()).thenReturn(true);
        when(repository.findByTaskPermissionCodeAndStatus(anyString(), any())).thenReturn(List.of());

        writeService.activate(7L);

        verify(structureValidator).validateForActivation(definition);
        verify(structureValidator).validateSingleActivePerTask(definition, List.of());
        assertThat(definition.getStatus()).isEqualTo(WorkflowDefinitionStatus.ACTIVE);
    }

    @Test
    void activationIsRejectedWhenAnotherActiveDefinitionExistsForTask() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        final WorkflowDefinition existingActive = linearThreeStageDefinition();
        existingActive.setId(8L);
        existingActive.setStatus(WorkflowDefinitionStatus.ACTIVE);
        when(repository.findById(7L)).thenReturn(Optional.of(definition));
        when(permissionRepository.findOneByCode(definition.getTaskPermissionCode())).thenReturn(Optional.of(permission));
        when(permission.hasMakerCheckerEnabled()).thenReturn(true);
        when(repository.findByTaskPermissionCodeAndStatus(definition.getTaskPermissionCode(), WorkflowDefinitionStatus.ACTIVE))
                .thenReturn(List.of(existingActive));
        doThrow(new WorkflowConfigurationException("active.definition.already.exists.for.task",
                "Task already has an active workflow", definition.getTaskPermissionCode())).when(structureValidator)
                .validateSingleActivePerTask(definition, List.of(existingActive));

        assertThatThrownBy(() -> writeService.activate(7L)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("already has an active workflow");
        assertThat(definition.getStatus()).isEqualTo(WorkflowDefinitionStatus.DRAFT);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void activationIsRejectedWhenMakerCheckerIsDisabledForTask() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(definition));
        when(permissionRepository.findOneByCode(definition.getTaskPermissionCode())).thenReturn(Optional.of(permission));
        when(permission.hasMakerCheckerEnabled()).thenReturn(false);

        assertThatThrownBy(() -> writeService.activate(7L)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("Maker-checker is not enabled");
        assertThat(definition.getStatus()).isEqualTo(WorkflowDefinitionStatus.DRAFT);
    }

    @Test
    void activationIsRejectedWhenTaskDoesNotExist() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(definition));
        when(permissionRepository.findOneByCode(definition.getTaskPermissionCode())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writeService.activate(7L)) //
                .isInstanceOf(WorkflowConfigurationException.class) //
                .hasMessageContaining("does not match any maker-checker task");
    }

    @Test
    void activatingAnAlreadyActiveDefinitionIsRejected() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        definition.setStatus(WorkflowDefinitionStatus.ACTIVE);
        when(repository.findById(7L)).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> writeService.activate(7L)).isInstanceOf(WorkflowDefinitionStateException.class);
    }

    @Test
    void deactivationRequiresActiveStatus() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        definition.setStatus(WorkflowDefinitionStatus.DRAFT);
        when(repository.findById(7L)).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> writeService.deactivate(7L)).isInstanceOf(WorkflowDefinitionStateException.class);
    }

    @Test
    void deactivationMovesActiveDefinitionToInactive() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        definition.setStatus(WorkflowDefinitionStatus.ACTIVE);
        when(repository.findById(7L)).thenReturn(Optional.of(definition));

        writeService.deactivate(7L);

        assertThat(definition.getStatus()).isEqualTo(WorkflowDefinitionStatus.INACTIVE);
    }

    @Test
    void unknownDefinitionRaisesNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writeService.activate(99L)).isInstanceOf(WorkflowDefinitionNotFoundException.class);
    }

    private static WorkflowDefinitionRequest updateRequest(final WorkflowDefinition definition) {
        final WorkflowDefinitionRequest request = new WorkflowDefinitionRequest();
        request.setTaskPermissionCode(definition.getTaskPermissionCode());
        request.setName(definition.getName());
        request.setDescription(definition.getDescription());
        request.setPriority(definition.getPriority());
        return request;
    }
}
