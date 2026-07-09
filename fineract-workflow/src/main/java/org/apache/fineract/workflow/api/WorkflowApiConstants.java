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
package org.apache.fineract.workflow.api;

public final class WorkflowApiConstants {

    private WorkflowApiConstants() {}

    public static final String WORKFLOW_DEFINITION_RESOURCE_NAME = "WORKFLOW_DEFINITION";

    public static final String MODULE_ENABLED_PROPERTY = "fineract.module.workflow.enabled";

    public static final String COMMAND_ACTIVATE = "activate";
    public static final String COMMAND_DEACTIVATE = "deactivate";

    public static final String TASK_PERMISSION_CODE_PARAM = "taskPermissionCode";
    public static final String NAME_PARAM = "name";
    public static final String DESCRIPTION_PARAM = "description";
    public static final String PRIORITY_PARAM = "priority";
    public static final String CURRENCY_CODE_PARAM = "currencyCode";
    public static final String MIN_AMOUNT_PARAM = "minAmount";
    public static final String MAX_AMOUNT_PARAM = "maxAmount";
    public static final String STAGES_PARAM = "stages";
    public static final String TRANSITIONS_PARAM = "transitions";

    public static final String STAGE_CODE_PARAM = "stageCode";
    public static final String STAGE_TYPE_PARAM = "stageType";
    public static final String REQUIRED_APPROVALS_PARAM = "requiredApprovals";
    public static final String REJECTION_POLICY_PARAM = "rejectionPolicy";
    public static final String REJECTION_THRESHOLD_PARAM = "rejectionThreshold";
    public static final String EXPIRY_PERIOD_UNIT_PARAM = "expiryPeriodUnit";
    public static final String EXPIRY_PERIOD_VALUE_PARAM = "expiryPeriodValue";
    public static final String ESCALATION_ENABLED_PARAM = "escalationEnabled";
    public static final String ESCALATION_TARGET_STAGE_CODE_PARAM = "escalationTargetStageCode";
    public static final String ALLOW_CROSS_BRANCH_ACCESS_PARAM = "allowCrossBranchAccess";
    public static final String REQUIRE_DISTINCT_APPROVER_PARAM = "requireDistinctApprover";
    public static final String ACTIONS_PARAM = "actions";
    public static final String APPROVAL_LIMIT_AMOUNT_PARAM = "approvalLimitAmount";
    public static final String APPROVAL_LIMIT_CURRENCY_PARAM = "approvalLimitCurrency";

    public static final String FROM_STAGE_CODE_PARAM = "fromStageCode";
    public static final String TO_STAGE_CODE_PARAM = "toStageCode";
    public static final String SEQUENCE_NO_PARAM = "sequenceNo";
}
