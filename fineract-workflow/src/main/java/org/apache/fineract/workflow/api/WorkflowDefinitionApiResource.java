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

import static org.apache.fineract.workflow.api.WorkflowApiConstants.COMMAND_ACTIVATE;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.COMMAND_DEACTIVATE;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.WORKFLOW_DEFINITION_RESOURCE_NAME;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.workflow.data.WorkflowDefinitionData;
import org.apache.fineract.workflow.service.WorkflowDefinitionReadPlatformService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/workflow-definitions")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Workflow Definitions", description = "Configure multi-stage approval workflows for banking operations")
@RequiredArgsConstructor
public class WorkflowDefinitionApiResource {

    private final PlatformSecurityContext context;
    private final WorkflowDefinitionReadPlatformService readPlatformService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List workflow definitions", description = "Lists workflow definitions with optional task and status filters")
    public List<WorkflowDefinitionData> retrieveAll(
            @QueryParam("taskPermissionCode") @Parameter(description = "taskPermissionCode") final String taskPermissionCode,
            @QueryParam("status") @Parameter(description = "status") final String status) {
        this.context.authenticatedUser().validateHasReadPermission(WORKFLOW_DEFINITION_RESOURCE_NAME);
        return this.readPlatformService.retrieveAll(taskPermissionCode, status);
    }

    @GET
    @Path("{definitionId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a workflow definition", description = "Retrieves a workflow definition with stages, participants, actions and transitions")
    public WorkflowDefinitionData retrieveOne(@PathParam("definitionId") @Parameter(description = "definitionId") final Long definitionId) {
        this.context.authenticatedUser().validateHasReadPermission(WORKFLOW_DEFINITION_RESOURCE_NAME);
        return this.readPlatformService.retrieveOne(definitionId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a workflow definition", description = "Creates a workflow definition in DRAFT status, including stages, participants, actions and transitions")
    public CommandProcessingResult create(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createWorkflowDefinition().withJson(apiRequestBodyAsJson).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("{definitionId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a workflow definition", description = "Updates a DRAFT workflow definition; the stage/transition structure is replaced with the submitted one")
    public CommandProcessingResult update(@PathParam("definitionId") @Parameter(description = "definitionId") final Long definitionId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateWorkflowDefinition(definitionId)
                .withJson(apiRequestBodyAsJson).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @POST
    @Path("{definitionId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Activate or deactivate a workflow definition", description = "command=activate validates the workflow structure and makes the definition selectable at runtime; command=deactivate stops it from governing new instances")
    public CommandProcessingResult stateTransition(
            @PathParam("definitionId") @Parameter(description = "definitionId") final Long definitionId,
            @QueryParam("command") @Parameter(description = "command") final String commandParam) {
        final CommandWrapper commandRequest;
        if (COMMAND_ACTIVATE.equalsIgnoreCase(commandParam)) {
            commandRequest = new CommandWrapperBuilder().activateWorkflowDefinition(definitionId).build();
        } else if (COMMAND_DEACTIVATE.equalsIgnoreCase(commandParam)) {
            commandRequest = new CommandWrapperBuilder().deactivateWorkflowDefinition(definitionId).build();
        } else {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { COMMAND_ACTIVATE, COMMAND_DEACTIVATE });
        }
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("{definitionId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete a workflow definition", description = "Deletes a DRAFT workflow definition")
    public CommandProcessingResult delete(@PathParam("definitionId") @Parameter(description = "definitionId") final Long definitionId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteWorkflowDefinition(definitionId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
