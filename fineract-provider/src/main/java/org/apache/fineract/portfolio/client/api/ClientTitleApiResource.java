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
    @Operation(summary = "List client titles", operationId = "retrieveAllClientTitles")
    public List<ClientTitleData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve client title template", operationId = "retrieveClientTitleTemplate")
    public Map<String, Object> retrieveTemplate() {
        final Map<String, Object> response = new HashMap<>();
        response.put("genderOptions", ClientEnumerations.gender(Gender.values()));
        response.put("statusOptions", List.of("ACTIVE", "INACTIVE"));
        return response;
    }

    @GET
    @Path("/{clientTitleId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve client title", operationId = "retrieveClientTitle")
    public ClientTitleData retrieveOne(@PathParam("clientTitleId") final Long clientTitleId) {
        return this.readPlatformService.retrieveOne(clientTitleId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create client title", operationId = "createClientTitle")
    public CommandProcessingResult create(final ClientTitleRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createClientTitle()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{clientTitleId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update client title", operationId = "updateClientTitle")
    public CommandProcessingResult update(@PathParam("clientTitleId") final Long clientTitleId, final ClientTitleRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateClientTitle(clientTitleId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{clientTitleId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete client title", operationId = "deleteClientTitle")
    public CommandProcessingResult delete(@PathParam("clientTitleId") final Long clientTitleId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteClientTitle(clientTitleId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
