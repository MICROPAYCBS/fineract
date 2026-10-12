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
import org.apache.fineract.portfolio.client.data.ContactTypeData;
import org.apache.fineract.portfolio.client.data.ContactTypeRequest;
import org.apache.fineract.portfolio.client.service.ContactTypeReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/contacttypes")
@Component
@Tag(name = "Contact Type", description = "Micropay contact type master data")
@RequiredArgsConstructor
public class ContactTypeApiResource {

    private final ContactTypeReadPlatformService readPlatformService;
    private final ToApiJsonSerializer<ContactTypeRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List contact types", description = "Returns every contact type, ordered by display order and name.", operationId = "retrieveAllContactTypes")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ContactTypeData.class))))
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks read permission for CONTACTTYPE.")
    public List<ContactTypeData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve contact type template", description = "Returns the ACTIVE and INACTIVE status values used when creating a contact type.", operationId = "retrieveContactTypeTemplate")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ContactTypeApiResourceSwagger.GetContactTypeTemplateResponse.class)))
    public Map<String, Object> retrieveTemplate() {
        final Map<String, Object> response = new HashMap<>();
        response.put("statusOptions", List.of("ACTIVE", "INACTIVE"));
        return response;
    }

    @GET
    @Path("/{contactTypeId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve contact type", description = "Returns one contact type.", operationId = "retrieveContactType")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ContactTypeData.class)))
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks read permission for CONTACTTYPE.")
    @ApiResponse(responseCode = "404", description = "The contact type was not found.")
    public ContactTypeData retrieveOne(
            @PathParam("contactTypeId") @Parameter(description = "Contact type identifier", required = true, example = "1") final Long contactTypeId) {
        return this.readPlatformService.retrieveOne(contactTypeId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create contact type", description = "Creates a contact type. The type code must be unique and validationRegex, "
            + "when supplied, must compile. The command is submitted through maker-checker.", operationId = "createContactType")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ContactTypeRequest.class)))
    @ApiResponse(responseCode = "200", description = "Command accepted. "
            + "When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "The JSON body is missing or invalid, a field fails validation, or validationRegex does not compile.")
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks permission to create the contact type, or the type code already exists.")
    public CommandProcessingResult create(final ContactTypeRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createContactType()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{contactTypeId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update contact type", description = "Updates a contact type. A changed type code must stay unique and "
            + "validationRegex, when supplied, must compile. The command is submitted through maker-checker.", operationId = "updateContactType")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ContactTypeRequest.class)))
    @ApiResponse(responseCode = "200", description = "Command accepted. "
            + "When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "The JSON body is missing or invalid, a field fails validation, or validationRegex does not compile.")
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks permission to update the contact type, or the type code already exists.")
    @ApiResponse(responseCode = "404", description = "The contact type was not found.")
    public CommandProcessingResult update(
            @PathParam("contactTypeId") @Parameter(description = "Contact type identifier", required = true, example = "1") final Long contactTypeId,
            final ContactTypeRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateContactType(contactTypeId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{contactTypeId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete contact type", description = "Deletes a contact type that is not referenced by other records. "
            + "The command is submitted through maker-checker.", operationId = "deleteContactType")
    @ApiResponse(responseCode = "200", description = "Command accepted. "
            + "When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks permission to delete the contact type, or the contact type is referenced by other records.")
    @ApiResponse(responseCode = "404", description = "The contact type was not found.")
    public CommandProcessingResult delete(
            @PathParam("contactTypeId") @Parameter(description = "Contact type identifier", required = true, example = "1") final Long contactTypeId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteContactType(contactTypeId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
