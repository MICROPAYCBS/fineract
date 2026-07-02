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
package org.apache.fineract.portfolio.industry.api;

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
import org.apache.fineract.portfolio.industry.data.IndustryData;
import org.apache.fineract.portfolio.industry.data.IndustryRequest;
import org.apache.fineract.portfolio.industry.service.IndustryReadPlatformService;
import org.apache.fineract.portfolio.sector.service.SectorReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/industries")
@Component
@Tag(name = "Industry", description = "Micropay industry master data")
@RequiredArgsConstructor
public class IndustryApiResource {

    private final IndustryReadPlatformService readPlatformService;
    private final SectorReadPlatformService sectorReadPlatformService;
    private final ToApiJsonSerializer<IndustryRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List industries", operationId = "retrieveAllIndustries")
    public List<IndustryData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve industry template", operationId = "retrieveIndustryTemplate")
    public Map<String, Object> retrieveTemplate() {
        final Map<String, Object> response = new HashMap<>();
        response.put("statusOptions", List.of("ACTIVE", "INACTIVE"));
        response.put("sectorOptions", this.sectorReadPlatformService.retrieveActiveForDropdown());
        return response;
    }

    @GET
    @Path("/{industryId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve industry", operationId = "retrieveIndustry")
    public IndustryData retrieveOne(@PathParam("industryId") final Long industryId) {
        return this.readPlatformService.retrieveOne(industryId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create industry", operationId = "createIndustry")
    public CommandProcessingResult create(final IndustryRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createIndustry()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{industryId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update industry", operationId = "updateIndustry")
    public CommandProcessingResult update(@PathParam("industryId") final Long industryId, final IndustryRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateIndustry(industryId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{industryId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete industry", operationId = "deleteIndustry")
    public CommandProcessingResult delete(@PathParam("industryId") final Long industryId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteIndustry(industryId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
