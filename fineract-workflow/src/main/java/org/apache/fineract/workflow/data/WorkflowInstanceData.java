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
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "In-flight approval workflow for a held maker-checker command")
public class WorkflowInstanceData {

    @Schema(description = "Workflow instance id", example = "7")
    private final Long id;
    @Schema(description = "Maker-checker command id this instance belongs to", example = "42")
    private final Long commandSourceId;
    @Schema(description = "Workflow definition governing this instance", example = "1")
    private final Long workflowDefinitionId;
    @Schema(description = "Maker-checker permission code of the held task", example = "APPROVE_LOAN")
    private final String taskPermissionCode;
    @Schema(description = "Stage code the instance is waiting on", example = "REVIEW")
    private final String currentStageCode;
    @Schema(description = "Instance status", example = "IN_PROGRESS", allowableValues = { "IN_PROGRESS", "COMPLETED", "REJECTED",
            "RETURNED" })
    private final String status;
}
