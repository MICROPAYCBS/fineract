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
package org.apache.fineract.portfolio.sector.api;

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
import org.apache.fineract.portfolio.sector.data.SectorData;
import org.apache.fineract.portfolio.sector.data.SectorRequest;
import org.apache.fineract.portfolio.sector.service.SectorReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/sectors")
@Component
@Tag(name = "Sector", description = "Micropay sector master data")
@RequiredArgsConstructor
public class SectorApiResource {

    private final SectorReadPlatformService readPlatformService;
    private final ToApiJsonSerializer<SectorRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List sectors", operationId = "retrieveAllSectors")
    public List<SectorData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve sector template", operationId = "retrieveSectorTemplate")
    public Map<String, Object> retrieveTemplate() {
        final Map<String, Object> response = new HashMap<>();
        response.put("statusOptions", List.of("ACTIVE", "INACTIVE"));
        response.put("parentSectorOptions", this.readPlatformService.retrieveActiveForDropdown());
        return response;
    }

    @GET
    @Path("/{sectorId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve sector", operationId = "retrieveSector")
    public SectorData retrieveOne(@PathParam("sectorId") final Long sectorId) {
        return this.readPlatformService.retrieveOne(sectorId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create sector", operationId = "createSector")
    public CommandProcessingResult create(final SectorRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createSector()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{sectorId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update sector", operationId = "updateSector")
    public CommandProcessingResult update(@PathParam("sectorId") final Long sectorId, final SectorRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateSector(sectorId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{sectorId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete sector", operationId = "deleteSector")
    public CommandProcessingResult delete(@PathParam("sectorId") final Long sectorId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteSector(sectorId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
