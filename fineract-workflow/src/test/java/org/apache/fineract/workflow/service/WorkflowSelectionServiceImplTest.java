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

import static org.apache.fineract.workflow.WorkflowTestFixtures.definition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowSelectionServiceImplTest {

    @Mock
    private WorkflowDefinitionRepository repository;

    @Mock
    private WorkflowTenantConfiguration tenantConfiguration;

    @InjectMocks
    private WorkflowSelectionServiceImpl selectionService;

    private WorkflowDefinition highPriorityWorkflow;
    private WorkflowDefinition lowPriorityWorkflow;

    @BeforeEach
    void setUp() {
        // Repository returns ACTIVE definitions ordered by priority DESC.
        highPriorityWorkflow = definition("CREATE_LOAN", "Primary Loan Approval", 20);
        lowPriorityWorkflow = definition("CREATE_LOAN", "Fallback Loan Approval", 10);
        lenient().when(tenantConfiguration.isApprovalWorkflowsEnabled()).thenReturn(true);
    }

    @Test
    void noWorkflowIsSelectedWhenTenantConfigurationIsDisabled() {
        when(tenantConfiguration.isApprovalWorkflowsEnabled()).thenReturn(false);

        final Optional<WorkflowDefinition> selected = selectionService.selectWorkflow("CREATE_LOAN");

        assertThat(selected).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void highestPriorityActiveWorkflowIsSelected() {
        when(repository.findActiveByTaskPermissionCodeOrderByPriorityDesc("CREATE_LOAN"))
                .thenReturn(List.of(highPriorityWorkflow, lowPriorityWorkflow));

        final Optional<WorkflowDefinition> selected = selectionService.selectWorkflow("CREATE_LOAN");

        assertThat(selected).containsSame(highPriorityWorkflow);
    }

    @Test
    void singleActiveWorkflowIsSelected() {
        when(repository.findActiveByTaskPermissionCodeOrderByPriorityDesc("CREATE_LOAN")).thenReturn(List.of(lowPriorityWorkflow));

        final Optional<WorkflowDefinition> selected = selectionService.selectWorkflow("CREATE_LOAN");

        assertThat(selected).containsSame(lowPriorityWorkflow);
    }

    @Test
    void noActiveWorkflowsYieldsEmpty() {
        when(repository.findActiveByTaskPermissionCodeOrderByPriorityDesc("CREATE_LOAN")).thenReturn(List.of());

        final Optional<WorkflowDefinition> selected = selectionService.selectWorkflow("CREATE_LOAN");

        assertThat(selected).isEmpty();
    }
}
