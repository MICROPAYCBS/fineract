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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Directed transition submitted with a workflow definition")
public class WorkflowTransitionRequest {

    @Schema(description = "Stage code the transition leaves", example = "REVIEW", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fromStageCode;
    @Schema(description = "Stage code the transition enters", example = "APPROVAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String toStageCode;
    @Schema(description = "Order of this transition among those leaving the same stage. Must be greater than zero.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer sequenceNo;
}
