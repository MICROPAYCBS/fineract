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
package org.apache.fineract.portfolio.subindustry.api;

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
import org.apache.fineract.portfolio.industry.service.IndustryReadPlatformService;
import org.apache.fineract.portfolio.subindustry.data.SubIndustryData;
import org.apache.fineract.portfolio.subindustry.data.SubIndustryRequest;
import org.apache.fineract.portfolio.subindustry.service.SubIndustryReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/subindustries")
@Component
@Tag(name = "SubIndustry", description = "Micropay sub-industry master data")
@RequiredArgsConstructor
public class SubIndustryApiResource {

    private final SubIndustryReadPlatformService readPlatformService;
    private final IndustryReadPlatformService industryReadPlatformService;
    private final ToApiJsonSerializer<SubIndustryRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List sub-industries", operationId = "retrieveAllSubIndustries")
    public List<SubIndustryData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve sub-industry template", operationId = "retrieveSubIndustryTemplate")
    public Map<String, Object> retrieveTemplate() {
        final Map<String, Object> response = new HashMap<>();
        response.put("statusOptions", List.of("ACTIVE", "INACTIVE"));
        response.put("industryOptions", this.industryReadPlatformService.retrieveActiveForDropdown());
        return response;
    }

    @GET
    @Path("/{subIndustryId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve sub-industry", operationId = "retrieveSubIndustry")
    public SubIndustryData retrieveOne(@PathParam("subIndustryId") final Long subIndustryId) {
        return this.readPlatformService.retrieveOne(subIndustryId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create sub-industry", operationId = "createSubIndustry")
    public CommandProcessingResult create(final SubIndustryRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createSubIndustry()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{subIndustryId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update sub-industry", operationId = "updateSubIndustry")
    public CommandProcessingResult update(@PathParam("subIndustryId") final Long subIndustryId, final SubIndustryRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateSubIndustry(subIndustryId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{subIndustryId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete sub-industry", operationId = "deleteSubIndustry")
    public CommandProcessingResult delete(@PathParam("subIndustryId") final Long subIndustryId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteSubIndustry(subIndustryId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
