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
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.workflow.data.WorkflowDefinitionData;
import org.apache.fineract.workflow.data.WorkflowStageData;
import org.apache.fineract.workflow.data.WorkflowStageParticipantData;
import org.apache.fineract.workflow.data.WorkflowTransitionData;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowDefinitionRepository;
import org.apache.fineract.workflow.domain.WorkflowDefinitionStatus;
import org.apache.fineract.workflow.domain.WorkflowStage;
import org.apache.fineract.workflow.domain.WorkflowStageAction;
import org.apache.fineract.workflow.exception.WorkflowDefinitionNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class WorkflowDefinitionReadPlatformServiceImpl implements WorkflowDefinitionReadPlatformService {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;

    @Override
    public List<WorkflowDefinitionData> retrieveAll(final String taskPermissionCode, final String status) {
        final List<WorkflowDefinition> definitions;
        if (StringUtils.isNotBlank(taskPermissionCode) && StringUtils.isNotBlank(status)) {
            definitions = this.workflowDefinitionRepository.findByTaskPermissionCodeAndStatus(taskPermissionCode,
                    WorkflowDefinitionStatus.fromString(status));
        } else if (StringUtils.isNotBlank(taskPermissionCode)) {
            definitions = this.workflowDefinitionRepository.findByTaskPermissionCode(taskPermissionCode);
        } else if (StringUtils.isNotBlank(status)) {
            definitions = this.workflowDefinitionRepository.findByStatus(WorkflowDefinitionStatus.fromString(status));
        } else {
            definitions = this.workflowDefinitionRepository.findAll();
        }
        return definitions.stream().map(this::mapToData).toList();
    }

    @Override
    public WorkflowDefinitionData retrieveOne(final Long definitionId) {
        final WorkflowDefinition definition = this.workflowDefinitionRepository.findById(definitionId)
                .orElseThrow(() -> new WorkflowDefinitionNotFoundException(definitionId));
        return mapToData(definition);
    }

    private WorkflowDefinitionData mapToData(final WorkflowDefinition definition) {
        final List<WorkflowStageData> stages = definition.getStages().stream().map(this::mapToStageData).toList();
        final List<WorkflowTransitionData> transitions = definition.getTransitions().stream()
                .map(transition -> WorkflowTransitionData.builder() //
                        .id(transition.getId()) //
                        .fromStageCode(transition.getFromStage().getStageCode()) //
                        .toStageCode(transition.getToStage().getStageCode()) //
                        .sequenceNo(transition.getSequenceNo()) //
                        .minAmount(transition.getMinAmount()) //
                        .maxAmount(transition.getMaxAmount()) //
                        .build())
                .toList();

        return WorkflowDefinitionData.builder() //
                .id(definition.getId()) //
                .taskPermissionCode(definition.getTaskPermissionCode()) //
                .name(definition.getName()) //
                .description(definition.getDescription()) //
                .status(definition.getStatus().name()) //
                .priority(definition.getPriority()) //
                .currencyCode(definition.getCurrencyCode()) //
                .minAmount(definition.getMinAmount()) //
                .maxAmount(definition.getMaxAmount()) //
                .stages(stages) //
                .transitions(transitions) //
                .build();
    }

    private WorkflowStageData mapToStageData(final WorkflowStage stage) {
        final List<String> actions = stage.getActions().stream().map(WorkflowStageAction::getAction).map(Enum::name).toList();
        final List<WorkflowStageParticipantData> participants = stage.getParticipants().stream()
                .map(participant -> WorkflowStageParticipantData.builder() //
                        .id(participant.getId()) //
                        .roleId(participant.getRole().getId()) //
                        .roleName(participant.getRole().getName()) //
                        .approvalLimitAmount(participant.getApprovalLimitAmount()) //
                        .approvalLimitCurrency(participant.getApprovalLimitCurrency()) //
                        .build())
                .toList();

        return WorkflowStageData.builder() //
                .id(stage.getId()) //
                .stageCode(stage.getStageCode()) //
                .name(stage.getName()) //
                .stageType(stage.getStageType().name()) //
                .requiredApprovals(stage.getRequiredApprovals()) //
                .rejectionPolicy(stage.getRejectionPolicy().name()) //
                .rejectionThreshold(stage.getRejectionThreshold()) //
                .expiryPeriodUnit(stage.getExpiryPeriodUnit() != null ? stage.getExpiryPeriodUnit().name() : null) //
                .expiryPeriodValue(stage.getExpiryPeriodValue()) //
                .escalationEnabled(stage.isEscalationEnabled()) //
                .escalationTargetStageCode(stage.getEscalationTargetStageCode()) //
                .allowCrossBranchAccess(stage.isAllowCrossBranchAccess()) //
                .requireDistinctApprover(stage.isRequireDistinctApprover()) //
                .actions(actions) //
                .participants(participants) //
                .build();
    }
}
