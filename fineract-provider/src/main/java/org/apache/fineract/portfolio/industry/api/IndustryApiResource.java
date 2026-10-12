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
import org.apache.fineract.portfolio.industry.data.IndustryData;
import org.apache.fineract.portfolio.industry.data.IndustryRequest;
import org.apache.fineract.portfolio.industry.service.IndustryReadPlatformService;
import org.apache.fineract.portfolio.sector.service.SectorReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/industries")
@Component
@Tag(name = "Industry", description = "Industries used to classify clients. An industry belongs to a sector.")
@RequiredArgsConstructor
public class IndustryApiResource {

    private static final String WRITE_OK = "Command accepted. When maker-checker holds the command, the body is "
            + "{ commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.";

    private final IndustryReadPlatformService readPlatformService;
    private final SectorReadPlatformService sectorReadPlatformService;
    private final ToApiJsonSerializer<IndustryRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List industries", operationId = "retrieveAllIndustries", description = "Returns every industry, active and inactive.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = IndustryData.class))))
    @ApiResponse(responseCode = "403", description = "The user lacks READ_INDUSTRY permission.")
    public List<IndustryData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve industry template", operationId = "retrieveIndustryTemplate", description = "Returns the allowed status values and the active sectors an industry can belong to.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IndustryApiResourceSwagger.GetIndustryTemplateResponse.class)))
    public Map<String, Object> retrieveTemplate() {
        final Map<String, Object> response = new HashMap<>();
        response.put("statusOptions", List.of("ACTIVE", "INACTIVE"));
        response.put("sectorOptions", this.sectorReadPlatformService.retrieveActiveForDropdown());
        return response;
    }

    @GET
    @Path("/{industryId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve industry", operationId = "retrieveIndustry", description = "Returns one industry.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IndustryData.class)))
    @ApiResponse(responseCode = "403", description = "The user lacks READ_INDUSTRY permission.")
    @ApiResponse(responseCode = "404", description = "No industry with this id (error.msg.industry.not.found).")
    public IndustryData retrieveOne(
            @PathParam("industryId") @Parameter(description = "Industry id", example = "1") final Long industryId) {
        return this.readPlatformService.retrieveOne(industryId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create industry", operationId = "createIndustry", description = "Creates an industry. industryCode and industryName are required. "
            + "industryCode must be unique. status is ACTIVE or INACTIVE. The command can be held by maker-checker.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = IndustryRequest.class)))
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "A field fails validation: industryCode or industryName blank or too long, sectorId not positive, status not ACTIVE/INACTIVE.")
    @ApiResponse(responseCode = "403", description = "The user lacks CREATE_INDUSTRY permission, or industryCode already exists (error.msg.industry.duplicate.code).")
    @ApiResponse(responseCode = "404", description = "sectorId names a sector that does not exist (error.msg.sector.not.found).")
    public CommandProcessingResult create(final IndustryRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createIndustry()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{industryId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update industry", operationId = "updateIndustry", description = "Updates the fields sent. A changed industryCode must stay unique. The command can be held by maker-checker.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = IndustryRequest.class)))
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "A field fails validation.")
    @ApiResponse(responseCode = "403", description = "The user lacks UPDATE_INDUSTRY permission, or industryCode already exists (error.msg.industry.duplicate.code).")
    @ApiResponse(responseCode = "404", description = "The industry does not exist (error.msg.industry.not.found), or sectorId names a sector that does not exist (error.msg.sector.not.found).")
    public CommandProcessingResult update(
            @PathParam("industryId") @Parameter(description = "Industry id", example = "1") final Long industryId,
            final IndustryRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateIndustry(industryId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{industryId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete industry", operationId = "deleteIndustry", description = "Deletes an industry that is not in use. The command can be held by maker-checker.")
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "403", description = "The user lacks DELETE_INDUSTRY permission, or the industry is in use (error.msg.industry.in.use).")
    @ApiResponse(responseCode = "404", description = "No industry with this id (error.msg.industry.not.found).")
    public CommandProcessingResult delete(
            @PathParam("industryId") @Parameter(description = "Industry id", example = "1") final Long industryId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteIndustry(industryId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
