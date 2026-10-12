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
package org.apache.fineract.workflow.data;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "One stage of a workflow definition")
public class WorkflowStageData {

    @Schema(description = "Stage id", example = "10")
    private final Long id;
    @Schema(description = "Stable stage code, unique within the definition", example = "REVIEW")
    private final String stageCode;
    @Schema(description = "Stage display name", example = "Branch review")
    private final String name;
    @Schema(description = "Kind of stage", example = "REVIEW", allowableValues = { "REVIEW", "APPROVAL", "VERIFICATION" })
    private final String stageType;
    @Schema(description = "Approvals required before the stage completes", example = "1")
    private final Integer requiredApprovals;
    @Schema(description = "When the stage is rejected", example = "ANY", allowableValues = { "ANY", "ALL", "THRESHOLD" })
    private final String rejectionPolicy;
    @Schema(description = "Rejections required when rejectionPolicy is THRESHOLD", example = "2")
    private final Integer rejectionThreshold;
    @Schema(description = "Unit of the stage expiry period", example = "DAYS", allowableValues = { "HOURS", "DAYS" })
    private final String expiryPeriodUnit;
    @Schema(description = "Length of the stage expiry period, in expiryPeriodUnit", example = "2")
    private final Integer expiryPeriodValue;
    @Schema(description = "True when the stage escalates after it expires", example = "false")
    private final Boolean escalationEnabled;
    @Schema(description = "Stage code to move to when the stage expires and escalation is enabled", example = "ESCALATED")
    private final String escalationTargetStageCode;
    @Schema(description = "True when an approver from another branch may act on this stage", example = "false")
    private final Boolean allowCrossBranchAccess;
    @Schema(description = "True when the same user cannot approve more than once on this stage", example = "true")
    private final Boolean requireDistinctApprover;
    @Schema(description = "Role id allowed to act on this stage", example = "3")
    private final Long roleId;
    @Schema(description = "Name of the role allowed to act on this stage", example = "Branch manager")
    private final String roleName;
    @Schema(description = "Actions enabled on this stage", example = "[\"APPROVE\", \"REJECT\"]")
    private final List<String> actions;
}
