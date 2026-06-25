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
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.ClientContactData;
import org.apache.fineract.portfolio.client.data.ClientContactRequest;
import org.apache.fineract.portfolio.client.data.ContactTypeData;
import org.apache.fineract.portfolio.client.service.ClientContactReadPlatformService;
import org.apache.fineract.portfolio.client.service.ContactTypeReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/clients/{clientId}/contacts")
@Component
@Tag(name = "Client Contact", description = "Client contacts such as phone numbers and email addresses")
@RequiredArgsConstructor
public class ClientContactsApiResource {

    private static final String RESOURCE_NAME_FOR_READ_PERMISSIONS = "CLIENT";

    private final PlatformSecurityContext context;
    private final ClientContactReadPlatformService clientContactReadPlatformService;
    private final ContactTypeReadPlatformService contactTypeReadPlatformService;
    private final ToApiJsonSerializer<ClientContactRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List all contacts for a client", operationId = "retrieveAllClientContacts")
    public List<ClientContactData> retrieveAllClientContacts(
            @PathParam("clientId") @Parameter(description = "clientId") final Long clientId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_READ_PERMISSIONS);
        return this.clientContactReadPlatformService.retrieveClientContacts(clientId);
    }

    @GET
    @Path("template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve client contact template", operationId = "retrieveClientContactTemplate")
    public Map<String, Object> retrieveTemplate(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_READ_PERMISSIONS);
        final List<ContactTypeData> contactTypeOptions = this.contactTypeReadPlatformService.retrieveActiveForClientDropdown();
        final Map<String, Object> response = new HashMap<>();
        response.put("contactTypeOptions", contactTypeOptions);
        return response;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a contact for a client", operationId = "createClientContact")
    public CommandProcessingResult createClientContact(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            final ClientContactRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createClientContact(clientId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @GET
    @Path("{contactId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a client contact", operationId = "retrieveOneClientContact")
    public ClientContactData retrieveClientContact(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("contactId") @Parameter(description = "contactId") final Long clientContactId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_READ_PERMISSIONS);
        return this.clientContactReadPlatformService.retrieveClientContact(clientId, clientContactId);
    }

    @PUT
    @Path("{contactId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a client contact", operationId = "updateClientContact")
    public CommandProcessingResult updateClientContact(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("contactId") @Parameter(description = "contactId") final Long clientContactId, final ClientContactRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateClientContact(clientId, clientContactId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("{contactId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete a client contact", operationId = "deleteClientContact")
    public CommandProcessingResult deleteClientContact(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @PathParam("contactId") @Parameter(description = "contactId") final Long clientContactId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteClientContact(clientId, clientContactId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
