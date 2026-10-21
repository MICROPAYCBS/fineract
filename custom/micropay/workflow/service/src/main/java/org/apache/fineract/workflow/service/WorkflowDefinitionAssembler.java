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
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.workflow.data.WorkflowDefinitionRequest;
import org.apache.fineract.workflow.data.WorkflowStageRequest;
import org.apache.fineract.workflow.data.WorkflowTransitionRequest;
import org.apache.fineract.workflow.domain.WorkflowApprovalAction;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowExpiryPeriodUnit;
import org.apache.fineract.workflow.domain.WorkflowRejectionPolicy;
import org.apache.fineract.workflow.domain.WorkflowRoleRepository;
import org.apache.fineract.workflow.domain.WorkflowStage;
import org.apache.fineract.workflow.domain.WorkflowStageAction;
import org.apache.fineract.workflow.domain.WorkflowStageType;
import org.apache.fineract.workflow.domain.WorkflowTransition;
import org.apache.fineract.workflow.exception.WorkflowConfigurationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link WorkflowDefinition} entity graph from a validated request payload. Stage codes are resolved to
 * stage entities for transitions; unknown references fail fast with a configuration error.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class WorkflowDefinitionAssembler {

    private final WorkflowRoleRepository workflowRoleRepository;

    public WorkflowDefinition assembleNew(final WorkflowDefinitionRequest request) {
        final Integer priority = request.getPriority() != null ? request.getPriority() : 0;
        final WorkflowDefinition definition = WorkflowDefinition.create(request.getTaskPermissionCode(), request.getName(),
                request.getDescription(), priority);
        assembleStructure(definition, request);
        return definition;
    }

    public void assembleUpdate(final WorkflowDefinition definition, final WorkflowDefinitionRequest request) {
        definition.setTaskPermissionCode(request.getTaskPermissionCode());
        definition.setName(request.getName());
        definition.setDescription(request.getDescription());
        definition.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        definition.clearStructure();
        assembleStructure(definition, request);
    }

    private void assembleStructure(final WorkflowDefinition definition, final WorkflowDefinitionRequest request) {
        final List<WorkflowStageRequest> stageRequests = request.getStages() != null ? request.getStages() : List.of();
        for (final WorkflowStageRequest stageRequest : stageRequests) {
            definition.addStage(assembleStage(stageRequest));
        }

        final List<WorkflowTransitionRequest> transitionRequests = request.getTransitions() != null ? request.getTransitions() : List.of();
        for (final WorkflowTransitionRequest transitionRequest : transitionRequests) {
            final WorkflowStage fromStage = definition.findStageByCode(transitionRequest.getFromStageCode())
                    .orElseThrow(() -> new WorkflowConfigurationException("transition.unknown.stage",
                            "Transition references unknown stage " + transitionRequest.getFromStageCode(),
                            transitionRequest.getFromStageCode()));
            final WorkflowStage toStage = definition.findStageByCode(transitionRequest.getToStageCode())
                    .orElseThrow(() -> new WorkflowConfigurationException("transition.unknown.stage",
                            "Transition references unknown stage " + transitionRequest.getToStageCode(),
                            transitionRequest.getToStageCode()));
            definition.addTransition(WorkflowTransition.create(fromStage, toStage, transitionRequest.getSequenceNo()));
        }
    }

    private WorkflowStage assembleStage(final WorkflowStageRequest stageRequest) {
        final WorkflowStage stage = new WorkflowStage();
        stage.setStageCode(stageRequest.getStageCode());
        stage.setName(stageRequest.getName());
        stage.setStageType(WorkflowStageType.fromString(stageRequest.getStageType()));
        stage.setRequiredApprovals(stageRequest.getRequiredApprovals());
        stage.setRejectionPolicy(
                stageRequest.getRejectionPolicy() != null ? WorkflowRejectionPolicy.fromString(stageRequest.getRejectionPolicy())
                        : WorkflowRejectionPolicy.ANY);
        stage.setRejectionThreshold(stageRequest.getRejectionThreshold());
        stage.setExpiryPeriodUnit(
                stageRequest.getExpiryPeriodUnit() != null ? WorkflowExpiryPeriodUnit.fromString(stageRequest.getExpiryPeriodUnit())
                        : null);
        stage.setExpiryPeriodValue(stageRequest.getExpiryPeriodValue());
        stage.setEscalationEnabled(Boolean.TRUE.equals(stageRequest.getEscalationEnabled()));
        stage.setEscalationTargetStageCode(stageRequest.getEscalationTargetStageCode());
        stage.setAllowCrossBranchAccess(Boolean.TRUE.equals(stageRequest.getAllowCrossBranchAccess()));
        stage.setRequireDistinctApprover(
                stageRequest.getRequireDistinctApprover() == null || Boolean.TRUE.equals(stageRequest.getRequireDistinctApprover()));
        stage.setRole(resolveRole(stageRequest.getRoleId(), stageRequest.getStageCode()));

        final List<String> actions = stageRequest.getActions() != null ? stageRequest.getActions() : List.of();
        for (final String action : actions) {
            stage.addAction(WorkflowStageAction.create(WorkflowApprovalAction.fromString(action)));
        }
        return stage;
    }

    private Role resolveRole(final Long roleId, final String stageCode) {
        if (roleId == null) {
            return null;
        }
        final Role role = this.workflowRoleRepository.findById(roleId).orElseThrow(() -> new WorkflowConfigurationException("unknown.role",
                "Stage " + stageCode + " references unknown role " + roleId, stageCode, roleId));
        if (Boolean.TRUE.equals(role.isDisabled())) {
            throw new WorkflowConfigurationException("role.disabled", "Stage " + stageCode + " references disabled role " + role.getName(),
                    stageCode, role.getName());
        }
        return role;
    }
}
