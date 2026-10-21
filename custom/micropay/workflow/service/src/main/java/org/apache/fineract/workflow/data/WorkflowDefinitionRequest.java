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
@Schema(description = "Create or replace a workflow definition")
public class WorkflowDefinitionRequest {

    @Schema(description = "Maker-checker permission code of the task this workflow governs", example = "APPROVE_LOAN", requiredMode = Schema.RequiredMode.REQUIRED)
    private String taskPermissionCode;
    @Schema(description = "Workflow name, unique per task", example = "Loan approval", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(description = "Workflow description", example = "Two-stage approval for loan disbursement")
    private String description;
    @Schema(description = "Selection priority. Omit, or send zero or a positive integer.", example = "1")
    private Integer priority;
    @Schema(description = "Stages. Required before the definition can be activated.")
    private List<WorkflowStageRequest> stages;
    @Schema(description = "Directed transitions between stages")
    private List<WorkflowTransitionRequest> transitions;
}
