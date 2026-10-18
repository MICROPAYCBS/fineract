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
package org.apache.fineract.workflow;

import org.apache.fineract.workflow.domain.WorkflowApprovalAction;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowStage;
import org.apache.fineract.workflow.domain.WorkflowStageAction;
import org.apache.fineract.workflow.domain.WorkflowStageType;
import org.apache.fineract.workflow.domain.WorkflowTransition;

public final class WorkflowTestFixtures {

    private WorkflowTestFixtures() {}

    public static WorkflowDefinition definition(final String taskPermissionCode, final String name, final Integer priority) {
        return WorkflowDefinition.create(taskPermissionCode, name, null, priority);
    }

    public static WorkflowStage stage(final String stageCode) {
        final WorkflowStage stage = new WorkflowStage();
        stage.setStageCode(stageCode);
        stage.setStageType(WorkflowStageType.APPROVAL);
        stage.setRequiredApprovals(1);
        stage.setRejectionPolicy(org.apache.fineract.workflow.domain.WorkflowRejectionPolicy.ANY);
        stage.addAction(WorkflowStageAction.create(WorkflowApprovalAction.APPROVE));
        stage.addAction(WorkflowStageAction.create(WorkflowApprovalAction.REJECT));
        return stage;
    }

    public static void connect(final WorkflowDefinition definition, final String fromCode, final String toCode, final int sequenceNo) {
        final WorkflowStage from = definition.findStageByCode(fromCode).orElseThrow();
        final WorkflowStage to = definition.findStageByCode(toCode).orElseThrow();
        definition.addTransition(WorkflowTransition.create(from, to, sequenceNo));
    }

    /**
     * A valid three-stage linear workflow: BRANCH_MANAGER -> REGIONAL_MANAGER -> HEAD_OFFICE.
     */
    public static WorkflowDefinition linearThreeStageDefinition() {
        final WorkflowDefinition definition = definition("CREATE_LOAN", "Loan Application Approval", 0);
        definition.addStage(stage("BRANCH_MANAGER"));
        definition.addStage(stage("REGIONAL_MANAGER"));
        definition.addStage(stage("HEAD_OFFICE"));
        connect(definition, "BRANCH_MANAGER", "REGIONAL_MANAGER", 1);
        connect(definition, "REGIONAL_MANAGER", "HEAD_OFFICE", 1);
        return definition;
    }

    /**
     * Two-stage linear workflow for loan approval tasks.
     */
    public static WorkflowDefinition linearTwoStageDefinitionForApproveLoan() {
        final WorkflowDefinition definition = definition("APPROVE_LOAN", "Loan Approval", 0);
        definition.addStage(stage("BRANCH_MANAGER"));
        definition.addStage(stage("HEAD_OFFICE"));
        connect(definition, "BRANCH_MANAGER", "HEAD_OFFICE", 1);
        return definition;
    }
}
