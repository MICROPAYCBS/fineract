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

package org.apache.fineract.portfolio.client.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.ClientComplianceProfileData;
import org.apache.fineract.portfolio.client.data.ClientComplianceProfileRequest;
import org.apache.fineract.portfolio.client.service.ClientComplianceProfileReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/clients/{clientId}/complianceprofile")
@Component
@Tag(name = "Client Compliance Profile", description = "Client compliance profile, including PEP, FATCA, and other bank accounts")
@RequiredArgsConstructor
public class ClientComplianceProfileApiResource {

    private static final String RESOURCE_NAME_FOR_PERMISSIONS = "ComplianceProfile";

    private final PlatformSecurityContext context;
    private final ClientComplianceProfileReadPlatformService readPlatformService;
    private final ToApiJsonSerializer<ClientComplianceProfileRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a client compliance profile", description = "Returns the client's compliance profile. "
            + "When no profile has been saved, the response contains the clientId and an empty otherBankAccounts list.", operationId = "retrieveClientComplianceProfile")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ClientComplianceProfileData.class)))
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks read permission for ComplianceProfile.")
    public ClientComplianceProfileData getComplianceProfile(
            @PathParam("clientId") @Parameter(description = "Client identifier", required = true, example = "1") final long clientId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);
        return this.readPlatformService.getClientComplianceProfile(clientId);
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a client compliance profile", description = "Creates or replaces the client's compliance profile. "
            + "The command is submitted through maker-checker.", operationId = "updateClientComplianceProfile")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ClientComplianceProfileRequest.class)))
    @ApiResponse(responseCode = "200", description = "Command accepted. "
            + "When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "The JSON body is missing or invalid, or a field fails validation.")
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks permission to update the compliance profile.")
    @ApiResponse(responseCode = "404", description = "The client was not found.")
    public CommandProcessingResult updateComplianceProfile(
            @PathParam("clientId") @Parameter(description = "Client identifier", required = true, example = "1") final long clientId,
            ClientComplianceProfileRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateComplianceProfile(clientId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
