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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.apache.fineract.workflow.data.WorkflowDefinitionRequest;
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
    @Operation(summary = "List workflow definitions", description = "Lists workflow definitions with optional task and status filters. status must be DRAFT, ACTIVE, or INACTIVE when supplied.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = WorkflowDefinitionData.class))))
    @ApiResponse(responseCode = "403", description = "The user does not have permission to read workflow definitions")
    public List<WorkflowDefinitionData> retrieveAll(
            @QueryParam("taskPermissionCode") @Parameter(description = "Optional filter. Maker-checker permission code of the task the workflow governs.") final String taskPermissionCode,
            @QueryParam("status") @Parameter(description = "Optional lifecycle filter: DRAFT, ACTIVE, or INACTIVE.") final String status) {
        this.context.authenticatedUser().validateHasReadPermission(WORKFLOW_DEFINITION_RESOURCE_NAME);
        return this.readPlatformService.retrieveAll(taskPermissionCode, status);
    }

    @GET
    @Path("{definitionId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a workflow definition", description = "Retrieves a workflow definition with stages, actions and transitions")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = WorkflowDefinitionData.class)))
    @ApiResponse(responseCode = "403", description = "The user does not have permission to read workflow definitions")
    @ApiResponse(responseCode = "404", description = "Workflow definition not found")
    public WorkflowDefinitionData retrieveOne(
            @PathParam("definitionId") @Parameter(description = "Identifier of the workflow definition to retrieve") final Long definitionId) {
        this.context.authenticatedUser().validateHasReadPermission(WORKFLOW_DEFINITION_RESOURCE_NAME);
        return this.readPlatformService.retrieveOne(definitionId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a workflow definition", description = "Creates a workflow definition in DRAFT status, including stages, actions and transitions")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = WorkflowDefinitionRequest.class)))
    @ApiResponse(responseCode = "200", description = "OK. When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "The body is missing or invalid, or a required field fails validation")
    @ApiResponse(responseCode = "403", description = "The user lacks permission, the task permission code is unknown, or a workflow with the same name already exists for the task")
    public CommandProcessingResult create(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createWorkflowDefinition().withJson(apiRequestBodyAsJson).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("{definitionId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a workflow definition", description = "Replaces the submitted fields and the stage and transition structure. A DRAFT definition may always be replaced. An ACTIVE or INACTIVE definition may be replaced only when no IN_PROGRESS instance still uses it. An ACTIVE definition is validated again after the replacement.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = WorkflowDefinitionRequest.class)))
    @ApiResponse(responseCode = "200", description = "OK. When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "The body is missing or invalid, or a required field fails validation")
    @ApiResponse(responseCode = "403", description = "The user lacks permission, the definition still has in-progress instances, the task is unknown, or an active definition fails structure rules")
    @ApiResponse(responseCode = "404", description = "Workflow definition not found")
    public CommandProcessingResult update(
            @PathParam("definitionId") @Parameter(description = "Identifier of the workflow definition to update") final Long definitionId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateWorkflowDefinition(definitionId)
                .withJson(apiRequestBodyAsJson).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @POST
    @Path("{definitionId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Activate or deactivate a workflow definition", description = "command=activate validates the workflow structure and makes the definition the sole ACTIVE workflow for its task (fails if another ACTIVE definition already exists for that task); command=deactivate stops it from governing new instances")
    @ApiResponse(responseCode = "200", description = "OK. When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "command is missing or is not activate or deactivate")
    @ApiResponse(responseCode = "403", description = "The user lacks permission, the definition is already in the requested state, activation fails structure or maker-checker rules, or another ACTIVE definition already exists for the task")
    @ApiResponse(responseCode = "404", description = "Workflow definition not found")
    public CommandProcessingResult stateTransition(
            @PathParam("definitionId") @Parameter(description = "Identifier of the workflow definition to activate or deactivate") final Long definitionId,
            @QueryParam("command") @Parameter(description = "Lifecycle action: activate or deactivate.", required = true) final String commandParam) {
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
    @ApiResponse(responseCode = "200", description = "OK. When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "403", description = "The user lacks permission, or the definition is not in DRAFT status")
    @ApiResponse(responseCode = "404", description = "Workflow definition not found")
    public CommandProcessingResult delete(
            @PathParam("definitionId") @Parameter(description = "Identifier of the DRAFT workflow definition to delete") final Long definitionId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteWorkflowDefinition(definitionId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
