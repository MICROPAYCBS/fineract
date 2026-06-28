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
package org.apache.fineract.infrastructure.interbranch.api;

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
import jakarta.ws.rs.QueryParam;
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
import org.apache.fineract.infrastructure.interbranch.data.OfficeServicingAccessData;
import org.apache.fineract.infrastructure.interbranch.data.OfficeServicingAccessRequest;
import org.apache.fineract.infrastructure.interbranch.service.OfficeServicingAccessReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/interbranch/access")
@Component
@Tag(name = "Office Servicing Access", description = "Micropay cross-branch client servicing access matrix")
@RequiredArgsConstructor
public class OfficeServicingAccessApiResource {

    private final OfficeServicingAccessReadPlatformService readPlatformService;
    private final ToApiJsonSerializer<OfficeServicingAccessRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List office servicing access rules", operationId = "retrieveAllOfficeServicingAccess")
    public List<OfficeServicingAccessData> retrieveAll(@QueryParam("servicingOfficeId") final Long servicingOfficeId) {
        if (servicingOfficeId != null) {
            return this.readPlatformService.retrieveByServicingOffice(servicingOfficeId);
        }
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve office servicing access template", operationId = "retrieveOfficeServicingAccessTemplate")
    public Map<String, Object> retrieveTemplate() {
        final Map<String, Object> response = new HashMap<>();
        response.put("statusOptions", List.of("ACTIVE", "INACTIVE"));
        return response;
    }

    @GET
    @Path("/{accessId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve office servicing access rule", operationId = "retrieveOfficeServicingAccess")
    public OfficeServicingAccessData retrieveOne(@PathParam("accessId") final Long accessId) {
        return this.readPlatformService.retrieveOne(accessId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create office servicing access rule", operationId = "createOfficeServicingAccess")
    public CommandProcessingResult create(final OfficeServicingAccessRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createOfficeServicingAccess()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{accessId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update office servicing access rule", operationId = "updateOfficeServicingAccess")
    public CommandProcessingResult update(@PathParam("accessId") final Long accessId, final OfficeServicingAccessRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateOfficeServicingAccess(accessId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{accessId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete office servicing access rule", operationId = "deleteOfficeServicingAccess")
    public CommandProcessingResult delete(@PathParam("accessId") final Long accessId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteOfficeServicingAccess(accessId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
