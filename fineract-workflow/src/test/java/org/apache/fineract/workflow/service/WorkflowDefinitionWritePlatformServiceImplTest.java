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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowDefinitionRepository;
import org.apache.fineract.workflow.domain.WorkflowDefinitionStatus;
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
    private WorkflowDefinitionDataValidator dataValidator;

    @Mock
    private WorkflowDefinitionAssembler assembler;

    @Mock
    private WorkflowDefinitionStructureValidator structureValidator;

    @InjectMocks
    private WorkflowDefinitionWritePlatformServiceImpl writeService;

    @Test
    void updateIsRejectedWhenDefinitionIsActive() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        definition.setStatus(WorkflowDefinitionStatus.ACTIVE);
        when(repository.findById(7L)).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> writeService.update(7L, JsonCommand.fromJsonElement(7L, null))) //
                .isInstanceOf(WorkflowDefinitionStateException.class) //
                .hasMessageContaining("ACTIVE");
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
    void activationValidatesStructureAndAmbiguity() {
        final WorkflowDefinition definition = linearThreeStageDefinition();
        definition.setId(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(definition));
        when(repository.findByModuleNameAndStatus(anyString(), any())).thenReturn(List.of());

        writeService.activate(7L);

        verify(structureValidator).validateForActivation(definition);
        verify(structureValidator).validateNoAmbiguousSelection(definition, List.of());
        assertThat(definition.getStatus()).isEqualTo(WorkflowDefinitionStatus.ACTIVE);
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
}
