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

import static org.apache.fineract.workflow.api.WorkflowApiConstants.MODULE_ENABLED_PROPERTY;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.workflow.data.WorkflowInstanceData;
import org.apache.fineract.workflow.exception.WorkflowInstanceNotFoundException;
import org.apache.fineract.workflow.service.WorkflowInstanceReadPlatformService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/workflow-instances")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Workflow Instances", description = "Runtime approval workflow state for held maker-checker commands")
@RequiredArgsConstructor
public class WorkflowInstanceApiResource {

    private final PlatformSecurityContext context;
    private final WorkflowInstanceReadPlatformService readPlatformService;

    @GET
    @Path("by-command/{commandSourceId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve workflow instance by maker-checker command id",
            description = "Returns the in-flight workflow instance for a held command (checker inbox row id).")
    public WorkflowInstanceData retrieveByCommandSourceId(
            @PathParam("commandSourceId") @Parameter(description = "commandSourceId") final Long commandSourceId) {
        this.context.authenticatedUser();
        return this.readPlatformService.retrieveByCommandSourceId(commandSourceId)
                .orElseThrow(() -> new WorkflowInstanceNotFoundException(commandSourceId));
    }
}
