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
package org.apache.fineract.treasury.api;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.COMMAND_APPROVE;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.COMMAND_CANCEL;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.COMMAND_CLOSE;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.COMMAND_EXECUTE;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.COMMAND_REVERSE;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_AGENT_FLOAT_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_FLOAT_REQUEST_RESOURCE_NAME;

import com.micropay.fineract.platform.command.MicropayCommandWrapperBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
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
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.treasury.data.TreasuryAgentFloatData;
import org.apache.fineract.treasury.data.TreasuryFloatRequestData;
import org.apache.fineract.treasury.service.TreasuryAgentFloatReadPlatformService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/treasury")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Treasury Agent Floats", description = "Agent float positions, and top-up and sweep requests executed as savings deposits and withdrawals")
@RequiredArgsConstructor
public class TreasuryAgentFloatApiResource {

    private final PlatformSecurityContext context;
    private final TreasuryAgentFloatReadPlatformService readPlatformService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Path("agent-floats")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List agent floats", description = "Balance against minimum and maximum with level status. Filters: officeId, clientId, status, levelStatus (OK, BELOW_MIN, ABOVE_MAX)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryAgentFloatData.class))))
    public List<TreasuryAgentFloatData> retrieveAgentFloats(@QueryParam("officeId") final Long officeId,
            @QueryParam("clientId") final Long clientId, @QueryParam("status") final String status,
            @QueryParam("levelStatus") final String levelStatus) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_AGENT_FLOAT_RESOURCE_NAME);
        return this.readPlatformService.retrieveAgentFloats(officeId, clientId, status, levelStatus);
    }

    @GET
    @Path("agent-floats/{agentFloatId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve an agent float")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryAgentFloatData.class)))
    public TreasuryAgentFloatData retrieveAgentFloat(@PathParam("agentFloatId") final Long agentFloatId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_AGENT_FLOAT_RESOURCE_NAME);
        return this.readPlatformService.retrieveAgentFloat(agentFloatId);
    }

    @POST
    @Path("agent-floats")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Register an agent float", description = "clientId, savingsAccountId, treasuryAccountId; optional minFloat, maxFloat. The savings product must map the treasury float payment type to the treasury account's GL account.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createAgentFloat(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService
                .logCommandSource(new MicropayCommandWrapperBuilder().createTreasuryAgentFloat().withJson(apiRequestBodyAsJson).build());
    }

    @PUT
    @Path("agent-floats/{agentFloatId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update an agent float", description = "Any of treasuryAccountId, minFloat, maxFloat; send null to clear a level")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateAgentFloat(@PathParam("agentFloatId") final Long agentFloatId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService.logCommandSource(
                new MicropayCommandWrapperBuilder().updateTreasuryAgentFloat(agentFloatId).withJson(apiRequestBodyAsJson).build());
    }

    @POST
    @Path("agent-floats/{agentFloatId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Close an agent float", description = "command=close; refused while it has open requests")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnAgentFloat(@PathParam("agentFloatId") final Long agentFloatId,
            @QueryParam("command") @Parameter(description = "close", required = true) final String commandParam) {
        if (!COMMAND_CLOSE.equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { COMMAND_CLOSE });
        }
        return this.commandsSourceWritePlatformService
                .logCommandSource(new MicropayCommandWrapperBuilder().closeTreasuryAgentFloat(agentFloatId).build());
    }

    @GET
    @Path("agent-floats/{agentFloatId}/requests")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Top-up and sweep requests of an agent float")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryFloatRequestData.class))))
    public List<TreasuryFloatRequestData> retrieveRequestsOfFloat(@PathParam("agentFloatId") final Long agentFloatId,
            @QueryParam("status") final String status) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_FLOAT_REQUEST_RESOURCE_NAME);
        return this.readPlatformService.retrieveRequests(agentFloatId, status);
    }

    @POST
    @Path("agent-floats/{agentFloatId}/requests")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Request a top-up or sweep", description = "requestType TOPUP or SWEEP, amount; optional note")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createFloatRequest(@PathParam("agentFloatId") final Long agentFloatId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService.logCommandSource(
                new MicropayCommandWrapperBuilder().createTreasuryFloatRequest(agentFloatId).withJson(apiRequestBodyAsJson).build());
    }

    @GET
    @Path("float-requests")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List top-up and sweep requests", description = "Filter: status")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryFloatRequestData.class))))
    public List<TreasuryFloatRequestData> retrieveRequests(@QueryParam("status") final String status) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_FLOAT_REQUEST_RESOURCE_NAME);
        return this.readPlatformService.retrieveRequests(null, status);
    }

    @GET
    @Path("float-requests/{requestId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a top-up or sweep request")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryFloatRequestData.class)))
    public TreasuryFloatRequestData retrieveRequest(@PathParam("requestId") final Long requestId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_FLOAT_REQUEST_RESOURCE_NAME);
        return this.readPlatformService.retrieveRequest(requestId);
    }

    @POST
    @Path("float-requests/{requestId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Approve, execute or cancel a request", description = "command=approve|execute|cancel|reverse; optional body {\"transactionDate\": \"2026-10-16\", \"note\": \"...\"}. Execute makes the savings deposit (top-up) or withdrawal (sweep); reverse (with a reason) undoes that savings transaction.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnRequest(@PathParam("requestId") final Long requestId,
            @QueryParam("command") @Parameter(description = "approve, execute, cancel or reverse", required = true) final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final MicropayCommandWrapperBuilder builder = new MicropayCommandWrapperBuilder();
        if (COMMAND_APPROVE.equalsIgnoreCase(commandParam)) {
            builder.approveTreasuryFloatRequest(requestId);
        } else if (COMMAND_EXECUTE.equalsIgnoreCase(commandParam)) {
            builder.executeTreasuryFloatRequest(requestId);
        } else if (COMMAND_CANCEL.equalsIgnoreCase(commandParam)) {
            builder.cancelTreasuryFloatRequest(requestId);
        } else if (COMMAND_REVERSE.equalsIgnoreCase(commandParam)) {
            builder.reverseTreasuryFloatRequest(requestId);
        } else {
            throw new UnrecognizedQueryParamException("command", commandParam,
                    new Object[] { COMMAND_APPROVE, COMMAND_EXECUTE, COMMAND_CANCEL, COMMAND_REVERSE });
        }
        return this.commandsSourceWritePlatformService
                .logCommandSource(builder.withJson(TreasuryTransferApiResource.body(apiRequestBodyAsJson)).build());
    }
}
