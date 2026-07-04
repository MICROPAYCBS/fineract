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

import java.math.BigDecimal;
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

    private WorkflowDefinition largeLoanWorkflow;
    private WorkflowDefinition defaultWorkflow;

    @BeforeEach
    void setUp() {
        // "Loans of 5M UGX and above" workflow, plus a criteria-less default. Higher priority first, matching the
        // repository's ORDER BY priority DESC.
        largeLoanWorkflow = definition("CREATE_LOAN", "Large Loan Approval", 20, "UGX", new BigDecimal("5000000"), null);
        defaultWorkflow = definition("CREATE_LOAN", "Standard Loan Approval", 10, null, null, null);
        lenient().when(tenantConfiguration.isApprovalWorkflowsEnabled()).thenReturn(true);
    }

    @Test
    void noWorkflowIsSelectedWhenTenantConfigurationIsDisabled() {
        when(tenantConfiguration.isApprovalWorkflowsEnabled()).thenReturn(false);

        final Optional<WorkflowDefinition> selected = selectionService.selectWorkflow("CREATE_LOAN", new BigDecimal("7000000"), "UGX");

        assertThat(selected).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void amountAboveThresholdSelectsLargeLoanWorkflow() {
        when(repository.findActiveByTaskPermissionCodeOrderByPriorityDesc("CREATE_LOAN")).thenReturn(List.of(largeLoanWorkflow, defaultWorkflow));

        final Optional<WorkflowDefinition> selected = selectionService.selectWorkflow("CREATE_LOAN", new BigDecimal("7000000"), "UGX");

        assertThat(selected).containsSame(largeLoanWorkflow);
    }

    @Test
    void amountAtExactThresholdSelectsLargeLoanWorkflow() {
        when(repository.findActiveByTaskPermissionCodeOrderByPriorityDesc("CREATE_LOAN")).thenReturn(List.of(largeLoanWorkflow, defaultWorkflow));

        final Optional<WorkflowDefinition> selected = selectionService.selectWorkflow("CREATE_LOAN", new BigDecimal("5000000"), "UGX");

        assertThat(selected).containsSame(largeLoanWorkflow);
    }

    @Test
    void amountBelowThresholdFallsBackToDefaultWorkflow() {
        when(repository.findActiveByTaskPermissionCodeOrderByPriorityDesc("CREATE_LOAN")).thenReturn(List.of(largeLoanWorkflow, defaultWorkflow));

        final Optional<WorkflowDefinition> selected = selectionService.selectWorkflow("CREATE_LOAN", new BigDecimal("2000000"), "UGX");

        assertThat(selected).containsSame(defaultWorkflow);
    }

    @Test
    void differentCurrencyFallsBackToDefaultWorkflow() {
        when(repository.findActiveByTaskPermissionCodeOrderByPriorityDesc("CREATE_LOAN")).thenReturn(List.of(largeLoanWorkflow, defaultWorkflow));

        final Optional<WorkflowDefinition> selected = selectionService.selectWorkflow("CREATE_LOAN", new BigDecimal("7000000"), "USD");

        assertThat(selected).containsSame(defaultWorkflow);
    }

    @Test
    void noActiveWorkflowsYieldsEmpty() {
        when(repository.findActiveByTaskPermissionCodeOrderByPriorityDesc("CREATE_LOAN")).thenReturn(List.of());

        final Optional<WorkflowDefinition> selected = selectionService.selectWorkflow("CREATE_LOAN", new BigDecimal("7000000"), "UGX");

        assertThat(selected).isEmpty();
    }

    @Test
    void criteriaOnlyWorkflowsWithNoMatchYieldEmpty() {
        when(repository.findActiveByTaskPermissionCodeOrderByPriorityDesc("CREATE_LOAN")).thenReturn(List.of(largeLoanWorkflow));

        final Optional<WorkflowDefinition> selected = selectionService.selectWorkflow("CREATE_LOAN", new BigDecimal("1000"), "UGX");

        assertThat(selected).isEmpty();
    }

    @Test
    void amountBandUpperBoundIsInclusive() {
        final WorkflowDefinition banded = definition("CREATE_LOAN", "Medium Loan Approval", 30, "UGX", new BigDecimal("1000000"),
                new BigDecimal("4999999"));
        when(repository.findActiveByTaskPermissionCodeOrderByPriorityDesc("CREATE_LOAN"))
                .thenReturn(List.of(banded, largeLoanWorkflow, defaultWorkflow));

        assertThat(selectionService.selectWorkflow("CREATE_LOAN", new BigDecimal("4999999"), "UGX")).containsSame(banded);
        assertThat(selectionService.selectWorkflow("CREATE_LOAN", new BigDecimal("5000000"), "UGX")).containsSame(largeLoanWorkflow);
    }
}
