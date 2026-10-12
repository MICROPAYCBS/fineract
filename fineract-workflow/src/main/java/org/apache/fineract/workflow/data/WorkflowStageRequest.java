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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "One stage submitted with a workflow definition")
public class WorkflowStageRequest {

    @Schema(description = "Stable stage code, unique within the definition", example = "REVIEW", requiredMode = Schema.RequiredMode.REQUIRED)
    private String stageCode;
    @Schema(description = "Stage display name", example = "Branch review")
    private String name;
    @Schema(description = "Kind of stage", example = "REVIEW", allowableValues = { "REVIEW", "APPROVAL",
            "VERIFICATION" }, requiredMode = Schema.RequiredMode.REQUIRED)
    private String stageType;
    @Schema(description = "Approvals required before the stage completes. Must be greater than zero.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer requiredApprovals;
    @Schema(description = "When the stage is rejected. Omit to leave unset.", example = "ANY", allowableValues = { "ANY", "ALL",
            "THRESHOLD" })
    private String rejectionPolicy;
    @Schema(description = "Rejections required when rejectionPolicy is THRESHOLD. Must be greater than zero when sent.", example = "2")
    private Integer rejectionThreshold;
    @Schema(description = "Unit of the stage expiry period", example = "DAYS", allowableValues = { "HOURS", "DAYS" })
    private String expiryPeriodUnit;
    @Schema(description = "Length of the stage expiry period, in expiryPeriodUnit. Must be greater than zero when sent.", example = "2")
    private Integer expiryPeriodValue;
    @Schema(description = "True when the stage escalates after it expires", example = "false")
    private Boolean escalationEnabled;
    @Schema(description = "Stage code to move to when the stage expires and escalation is enabled", example = "ESCALATED")
    private String escalationTargetStageCode;
    @Schema(description = "True when an approver from another branch may act on this stage", example = "false")
    private Boolean allowCrossBranchAccess;
    @Schema(description = "True when the same user cannot approve more than once on this stage", example = "true")
    private Boolean requireDistinctApprover;
    @Schema(description = "Role id allowed to act on this stage. Must be greater than zero when sent.", example = "3")
    private Long roleId;
    @Schema(description = "Actions enabled on this stage", example = "[\"APPROVE\", \"REJECT\"]")
    private List<String> actions;
}
