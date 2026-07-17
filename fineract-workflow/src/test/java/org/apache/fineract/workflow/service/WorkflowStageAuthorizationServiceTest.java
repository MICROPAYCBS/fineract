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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.workflow.WorkflowTestFixtures;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowInstance;
import org.apache.fineract.workflow.domain.WorkflowStage;
import org.apache.fineract.workflow.exception.WorkflowRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowStageAuthorizationServiceTest {

    @Mock
    private OfficeRepository officeRepository;

    @Mock
    private AppUser actor;

    @InjectMocks
    private WorkflowStageAuthorizationService authorizationService;

    private CommandSource commandSource;
    private WorkflowInstance instance;
    private WorkflowStage stage;
    private Role branchManagerRole;
    private Role headOfficeRole;

    @BeforeEach
    void setUp() {
        commandSource = CommandSource.builder().actionName("APPROVE").entityName("LOAN").build();
        commandSource.setId(42L);

        final WorkflowDefinition definition = WorkflowTestFixtures.linearTwoStageDefinitionForApproveLoan();
        instance = WorkflowInstance.create(42L, definition, "BRANCH_MANAGER");
        stage = definition.findStageByCode("BRANCH_MANAGER").orElseThrow();

        branchManagerRole = new Role("Branch Manager", "Branch managers");
        branchManagerRole.setId(11L);
        headOfficeRole = new Role("Head Office", "Head office approvers");
        headOfficeRole.setId(22L);

        when(actor.hasAnyPermission(anyString())).thenReturn(true);
    }

    @Test
    void allowsActorWhenStageHasNoRoleRestriction() {
        assertThatCode(() -> authorizationService.validateActorMayApproveAtStage(commandSource, instance, actor, stage))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsActorWhenStageRoleMatches() {
        stage.setRole(branchManagerRole);
        when(actor.getRoles()).thenReturn(Set.of(branchManagerRole));

        assertThatCode(() -> authorizationService.validateActorMayApproveAtStage(commandSource, instance, actor, stage))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsActorWhenStageRoleDoesNotMatch() {
        stage.setRole(branchManagerRole);
        when(actor.getRoles()).thenReturn(Set.of(headOfficeRole));

        assertThatThrownBy(() -> authorizationService.validateActorMayApproveAtStage(commandSource, instance, actor, stage))
                .isInstanceOf(WorkflowRuntimeException.class) //
                .hasMessageContaining("requires role Branch Manager");
    }

    @Test
    void rejectsActorOnRejectWhenStageRoleDoesNotMatch() {
        stage.setRole(branchManagerRole);
        when(actor.getRoles()).thenReturn(Set.of(headOfficeRole));

        assertThatThrownBy(() -> authorizationService.validateActorMayRejectAtStage(commandSource, instance, actor, stage))
                .isInstanceOf(WorkflowRuntimeException.class) //
                .hasMessageContaining("requires role Branch Manager");
    }
}
