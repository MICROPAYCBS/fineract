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

import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.workflow.domain.WorkflowApprovalAction;
import org.apache.fineract.workflow.domain.WorkflowInstance;
import org.apache.fineract.workflow.domain.WorkflowStage;
import org.apache.fineract.workflow.exception.WorkflowRuntimeException;
import org.springframework.stereotype.Component;

/**
 * Authorizes stage actions using the implied maker-checker permission
 * {@code {taskPermissionCode}_CHECKER}, plus stage office / limit / distinct-approver rules.
 */
@Component
@RequiredArgsConstructor
public class WorkflowStageAuthorizationService {

    private final OfficeRepository officeRepository;

    public void validateActorMayApproveAtStage(final CommandSource commandSource, final WorkflowInstance instance, final AppUser actor,
            final WorkflowStage stage) {
        validateActionEnabled(stage, WorkflowApprovalAction.APPROVE);
        validateCheckerPermission(actor, instance.getTaskPermissionCode(), stage);
        validateApprovalLimit(instance, stage);
        validateOfficeAccess(actor, commandSource, stage);
        validateDistinctApprover(commandSource, actor, stage);
    }

    public void validateActorMayRejectAtStage(final CommandSource commandSource, final WorkflowInstance instance, final AppUser actor,
            final WorkflowStage stage) {
        validateActionEnabled(stage, WorkflowApprovalAction.REJECT);
        validateCheckerPermission(actor, instance.getTaskPermissionCode(), stage);
        validateApprovalLimit(instance, stage);
        validateOfficeAccess(actor, commandSource, stage);
    }

    public boolean isEligibleParticipant(final AppUser actor, final CommandSource commandSource, final WorkflowInstance instance,
            final WorkflowStage stage) {
        if (!actor.hasAnyPermission(checkerPermissionCode(instance.getTaskPermissionCode()))) {
            return false;
        }
        if (!isWithinApprovalLimit(stage, instance.getTransactionAmount(), instance.getCurrencyCode())) {
            return false;
        }
        return isOfficeAccessible(actor, commandSource, stage.isAllowCrossBranchAccess());
    }

    private void validateActionEnabled(final WorkflowStage stage, final WorkflowApprovalAction action) {
        final boolean enabled = stage.getActions().stream().anyMatch(stageAction -> stageAction.getAction() == action);
        if (!enabled) {
            throw new WorkflowRuntimeException("action.not.enabled", "Action " + action + " is not enabled at stage " + stage.getStageCode(),
                    action, stage.getStageCode());
        }
    }

    private void validateCheckerPermission(final AppUser actor, final String taskPermissionCode, final WorkflowStage stage) {
        final String checkerPermission = checkerPermissionCode(taskPermissionCode);
        if (!actor.hasAnyPermission(checkerPermission)) {
            throw new WorkflowRuntimeException("approver.not.authorized",
                    "User is not authorized to act at workflow stage " + stage.getStageCode() + " (requires " + checkerPermission + ")",
                    stage.getStageCode(), checkerPermission);
        }
    }

    private String checkerPermissionCode(final String taskPermissionCode) {
        return taskPermissionCode.toUpperCase() + "_CHECKER";
    }

    private void validateApprovalLimit(final WorkflowInstance instance, final WorkflowStage stage) {
        if (!isWithinApprovalLimit(stage, instance.getTransactionAmount(), instance.getCurrencyCode())) {
            throw new WorkflowRuntimeException("approver.not.authorized",
                    "Transaction amount exceeds approval limit for stage " + stage.getStageCode(), stage.getStageCode());
        }
    }

    private boolean isWithinApprovalLimit(final WorkflowStage stage, final BigDecimal transactionAmount,
            final String transactionCurrencyCode) {
        if (stage.getApprovalLimitAmount() == null) {
            return true;
        }
        if (transactionAmount == null) {
            return true;
        }
        if (stage.getApprovalLimitCurrency() != null && !stage.getApprovalLimitCurrency().equals(transactionCurrencyCode)) {
            return false;
        }
        return transactionAmount.compareTo(stage.getApprovalLimitAmount()) <= 0;
    }

    private void validateOfficeAccess(final AppUser actor, final CommandSource commandSource, final WorkflowStage stage) {
        if (!isOfficeAccessible(actor, commandSource, stage.isAllowCrossBranchAccess())) {
            throw new WorkflowRuntimeException("approver.not.authorized",
                    "User is not authorized to act at workflow stage " + stage.getStageCode() + " for this office", stage.getStageCode());
        }
    }

    private boolean isOfficeAccessible(final AppUser actor, final CommandSource commandSource, final boolean allowCrossBranchAccess) {
        if (allowCrossBranchAccess) {
            return true;
        }
        final Long commandOfficeId = commandSource.getOfficeId();
        if (commandOfficeId == null) {
            return true;
        }
        final Office actorOffice = actor.getOffice();
        if (Objects.equals(actorOffice.getId(), commandOfficeId)) {
            return true;
        }
        return this.officeRepository.findById(commandOfficeId).map(commandOffice -> {
            final String actorHierarchy = actorOffice.getHierarchy();
            final String commandHierarchy = commandOffice.getHierarchy();
            return commandHierarchy != null && actorHierarchy != null && commandHierarchy.startsWith(actorHierarchy);
        }).orElse(false);
    }

    private void validateDistinctApprover(final CommandSource commandSource, final AppUser actor, final WorkflowStage stage) {
        if (!stage.isRequireDistinctApprover()) {
            return;
        }
        final AppUser maker = commandSource.getMaker();
        if (maker != null && Objects.equals(maker.getId(), actor.getId())) {
            throw new WorkflowRuntimeException("same.approver.as.maker", "The maker cannot approve their own submission at stage "
                    + stage.getStageCode(), stage.getStageCode());
        }
    }
}
