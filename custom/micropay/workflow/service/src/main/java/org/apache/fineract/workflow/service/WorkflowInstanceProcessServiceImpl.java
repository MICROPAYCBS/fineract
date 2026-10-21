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
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.service.ApprovalWorkflowDecision;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.workflow.domain.WorkflowApprovalAction;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowInstance;
import org.apache.fineract.workflow.domain.WorkflowInstanceAction;
import org.apache.fineract.workflow.domain.WorkflowInstanceActionRepository;
import org.apache.fineract.workflow.domain.WorkflowInstanceRepository;
import org.apache.fineract.workflow.domain.WorkflowInstanceStatus;
import org.apache.fineract.workflow.domain.WorkflowStage;
import org.apache.fineract.workflow.exception.WorkflowRuntimeException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class WorkflowInstanceProcessServiceImpl implements WorkflowInstanceProcessService {

    private final WorkflowTenantConfiguration tenantConfiguration;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final WorkflowInstanceActionRepository workflowInstanceActionRepository;
    private final WorkflowStageAuthorizationService stageAuthorizationService;
    private final TerminalStageExecution terminalStageExecution;
    private final WorkflowStageRouter stageRouter;

    @Override
    @Transactional
    public ApprovalWorkflowDecision processCheckerApprove(final CommandSource commandSource, final AppUser checker) {
        if (!this.tenantConfiguration.isApprovalWorkflowsEnabled()) {
            return ApprovalWorkflowDecision.NOT_APPLICABLE;
        }
        if (checker.isCheckerSuperUser()) {
            return ApprovalWorkflowDecision.PROCEED_TO_EXECUTE;
        }

        final WorkflowInstance instance = findInProgressInstance(commandSource.getId());
        if (instance == null) {
            return ApprovalWorkflowDecision.NOT_APPLICABLE;
        }

        final WorkflowDefinition definition = instance.getWorkflowDefinition();
        definition.getTransitions().size();
        final WorkflowStage currentStage = definition.findStageByCode(instance.getCurrentStageCode())
                .orElseThrow(() -> new WorkflowRuntimeException("stage.not.found",
                        "Workflow stage " + instance.getCurrentStageCode() + " is not defined", instance.getCurrentStageCode()));

        this.stageAuthorizationService.validateActorMayApproveAtStage(commandSource, instance, checker, currentStage);

        final boolean alreadyApproved = this.workflowInstanceActionRepository.existsByWorkflowInstanceIdAndStageCodeAndActionAndCreatedBy(
                instance.getId(), instance.getCurrentStageCode(), WorkflowApprovalAction.APPROVE, checker.getId());
        if (alreadyApproved) {
            // This transaction commits before the held command runs. A previous terminal approval may already be
            // recorded while the command is still awaiting approval because execution failed.
            if (isRetryableTerminalExecution(commandSource, definition, instance, currentStage)) {
                this.terminalStageExecution.arm(instance.getId());
                return ApprovalWorkflowDecision.PROCEED_TO_EXECUTE;
            }
            throw new WorkflowRuntimeException("duplicate.approval", "User has already approved at stage " + instance.getCurrentStageCode(),
                    instance.getCurrentStageCode());
        }

        recordAction(instance, instance.getCurrentStageCode(), WorkflowApprovalAction.APPROVE, null);

        final long approvalCount = this.workflowInstanceActionRepository.countDistinctActorsByInstanceAndStageAndAction(instance.getId(),
                instance.getCurrentStageCode(), WorkflowApprovalAction.APPROVE);
        if (approvalCount < currentStage.getRequiredApprovals()) {
            return ApprovalWorkflowDecision.STAGE_RECORDED;
        }

        // The next stage is the next one that applies to this command; stages skipped by authority rules are passed
        // over.
        final Optional<String> nextStageCode = this.stageRouter.nextStage(definition, instance.getCurrentStageCode(), commandSource);
        if (nextStageCode.isEmpty()) {
            // Stay IN_PROGRESS until the command succeeds. TerminalStageExecutionAspect marks the instance COMPLETED
            // only after approveEntry returns; a failed execution stays retryable for this approver.
            this.terminalStageExecution.arm(instance.getId());
            return ApprovalWorkflowDecision.PROCEED_TO_EXECUTE;
        }

        instance.setCurrentStageCode(nextStageCode.get());
        this.workflowInstanceRepository.saveAndFlush(instance);
        return ApprovalWorkflowDecision.STAGE_RECORDED;
    }

    @Override
    @Transactional
    public ApprovalWorkflowDecision processCheckerReject(final CommandSource commandSource, final AppUser checker) {
        if (!this.tenantConfiguration.isApprovalWorkflowsEnabled()) {
            return ApprovalWorkflowDecision.NOT_APPLICABLE;
        }
        if (checker.isCheckerSuperUser()) {
            return ApprovalWorkflowDecision.WORKFLOW_REJECTED;
        }

        final WorkflowInstance instance = findInProgressInstance(commandSource.getId());
        if (instance == null) {
            return ApprovalWorkflowDecision.NOT_APPLICABLE;
        }

        final WorkflowDefinition definition = instance.getWorkflowDefinition();
        definition.getTransitions().size();
        final WorkflowStage currentStage = definition.findStageByCode(instance.getCurrentStageCode())
                .orElseThrow(() -> new WorkflowRuntimeException("stage.not.found",
                        "Workflow stage " + instance.getCurrentStageCode() + " is not defined", instance.getCurrentStageCode()));

        this.stageAuthorizationService.validateActorMayRejectAtStage(commandSource, instance, checker, currentStage);
        recordAction(instance, instance.getCurrentStageCode(), WorkflowApprovalAction.REJECT, null);

        if (isStageRejected(currentStage, instance)) {
            instance.setStatus(WorkflowInstanceStatus.REJECTED);
            this.workflowInstanceRepository.saveAndFlush(instance);
            return ApprovalWorkflowDecision.WORKFLOW_REJECTED;
        }

        return ApprovalWorkflowDecision.STAGE_RECORDED;
    }

    private boolean isRetryableTerminalExecution(final CommandSource commandSource, final WorkflowDefinition definition,
            final WorkflowInstance instance, final WorkflowStage currentStage) {
        if (this.stageRouter.nextStage(definition, instance.getCurrentStageCode(), commandSource).isPresent()) {
            return false;
        }
        final long approvalCount = this.workflowInstanceActionRepository.countDistinctActorsByInstanceAndStageAndAction(instance.getId(),
                instance.getCurrentStageCode(), WorkflowApprovalAction.APPROVE);
        return approvalCount >= currentStage.getRequiredApprovals();
    }

    private WorkflowInstance findInProgressInstance(final Long commandSourceId) {
        return this.workflowInstanceRepository.findByCommandSourceIdWithDefinitionGraph(commandSourceId)
                .filter(WorkflowInstance::isInProgress).orElse(null);
    }

    private void recordAction(final WorkflowInstance instance, final String stageCode, final WorkflowApprovalAction action,
            final String comment) {
        this.workflowInstanceActionRepository.saveAndFlush(WorkflowInstanceAction.create(instance, stageCode, action, comment));
    }

    private boolean isStageRejected(final WorkflowStage stage, final WorkflowInstance instance) {
        final long rejectionCount = this.workflowInstanceActionRepository.countByWorkflowInstanceIdAndStageCodeAndAction(instance.getId(),
                instance.getCurrentStageCode(), WorkflowApprovalAction.REJECT);
        return switch (stage.getRejectionPolicy()) {
            case ANY -> rejectionCount >= 1;
            case ALL -> rejectionCount >= stage.getRequiredApprovals();
            case THRESHOLD -> stage.getRejectionThreshold() != null && rejectionCount >= stage.getRejectionThreshold();
        };
    }
}
