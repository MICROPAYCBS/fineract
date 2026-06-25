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
    @Operation(summary = "List contact types", operationId = "retrieveAllContactTypes")
    public List<ContactTypeData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve contact type template", operationId = "retrieveContactTypeTemplate")
    public Map<String, Object> retrieveTemplate() {
        final Map<String, Object> response = new HashMap<>();
        response.put("statusOptions", List.of("ACTIVE", "INACTIVE"));
        return response;
    }

    @GET
    @Path("/{contactTypeId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve contact type", operationId = "retrieveContactType")
    public ContactTypeData retrieveOne(@PathParam("contactTypeId") final Long contactTypeId) {
        return this.readPlatformService.retrieveOne(contactTypeId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create contact type", operationId = "createContactType")
    public CommandProcessingResult create(final ContactTypeRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createContactType()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{contactTypeId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update contact type", operationId = "updateContactType")
    public CommandProcessingResult update(@PathParam("contactTypeId") final Long contactTypeId, final ContactTypeRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateContactType(contactTypeId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{contactTypeId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete contact type", operationId = "deleteContactType")
    public CommandProcessingResult delete(@PathParam("contactTypeId") final Long contactTypeId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteContactType(contactTypeId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
