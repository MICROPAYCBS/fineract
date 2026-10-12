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
import jakarta.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.portfolio.client.data.ClientTitleData;
import org.apache.fineract.portfolio.client.data.ClientTitleRequest;
import org.apache.fineract.portfolio.client.domain.ClientEnumerations;
import org.apache.fineract.portfolio.client.domain.Gender;
import org.apache.fineract.portfolio.client.service.ClientTitleReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/clienttitles")
@Component
@Tag(name = "Client Title", description = "Micropay client title master data")
@RequiredArgsConstructor
public class ClientTitleApiResource {

    private final ClientTitleReadPlatformService readPlatformService;
    private final ToApiJsonSerializer<ClientTitleRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List client titles", description = "Returns every client title, ordered by display order and name.", operationId = "retrieveAllClientTitles")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ClientTitleData.class))))
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks read permission for CLIENTTITLE.")
    public List<ClientTitleData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve client title template", description = "Returns gender options and the ACTIVE and INACTIVE status values "
            + "used when creating a client title.", operationId = "retrieveClientTitleTemplate")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ClientTitleApiResourceSwagger.GetClientTitleTemplateResponse.class)))
    public Map<String, Object> retrieveTemplate() {
        final Map<String, Object> response = new HashMap<>();
        response.put("genderOptions", ClientEnumerations.gender(Gender.values()));
        response.put("statusOptions", List.of("ACTIVE", "INACTIVE"));
        return response;
    }

    @GET
    @Path("/{clientTitleId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve client title", description = "Returns one client title.", operationId = "retrieveClientTitle")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ClientTitleData.class)))
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks read permission for CLIENTTITLE.")
    @ApiResponse(responseCode = "404", description = "The client title was not found.")
    public ClientTitleData retrieveOne(
            @PathParam("clientTitleId") @Parameter(description = "Client title identifier", required = true, example = "1") final Long clientTitleId) {
        return this.readPlatformService.retrieveOne(clientTitleId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create client title", description = "Creates a client title. The title code must be unique. "
            + "The command is submitted through maker-checker.", operationId = "createClientTitle")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ClientTitleRequest.class)))
    @ApiResponse(responseCode = "200", description = "Command accepted. "
            + "When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "The JSON body is missing or invalid, or a field fails validation.")
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks permission to create the client title, or the title code already exists.")
    public CommandProcessingResult create(final ClientTitleRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createClientTitle()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{clientTitleId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update client title", description = "Updates a client title. A changed title code must stay unique. "
            + "The command is submitted through maker-checker.", operationId = "updateClientTitle")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ClientTitleRequest.class)))
    @ApiResponse(responseCode = "200", description = "Command accepted. "
            + "When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "The JSON body is missing or invalid, or a field fails validation.")
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks permission to update the client title, or the title code already exists.")
    @ApiResponse(responseCode = "404", description = "The client title was not found.")
    public CommandProcessingResult update(
            @PathParam("clientTitleId") @Parameter(description = "Client title identifier", required = true, example = "1") final Long clientTitleId,
            final ClientTitleRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateClientTitle(clientTitleId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{clientTitleId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete client title", description = "Deletes a client title that is not referenced by other records. "
            + "The command is submitted through maker-checker.", operationId = "deleteClientTitle")
    @ApiResponse(responseCode = "200", description = "Command accepted. "
            + "When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks permission to delete the client title, or the title is referenced by other records.")
    @ApiResponse(responseCode = "404", description = "The client title was not found.")
    public CommandProcessingResult delete(
            @PathParam("clientTitleId") @Parameter(description = "Client title identifier", required = true, example = "1") final Long clientTitleId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteClientTitle(clientTitleId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
