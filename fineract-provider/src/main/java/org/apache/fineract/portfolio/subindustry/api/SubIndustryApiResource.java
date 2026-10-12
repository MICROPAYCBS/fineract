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
import org.apache.fineract.portfolio.industry.service.IndustryReadPlatformService;
import org.apache.fineract.portfolio.subindustry.data.SubIndustryData;
import org.apache.fineract.portfolio.subindustry.data.SubIndustryRequest;
import org.apache.fineract.portfolio.subindustry.service.SubIndustryReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/subindustries")
@Component
@Tag(name = "SubIndustry", description = "Sub-industries used to classify clients. A sub-industry belongs to an industry.")
@RequiredArgsConstructor
public class SubIndustryApiResource {

    private static final String WRITE_OK = "Command accepted. When maker-checker holds the command, the body is "
            + "{ commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.";

    private final SubIndustryReadPlatformService readPlatformService;
    private final IndustryReadPlatformService industryReadPlatformService;
    private final ToApiJsonSerializer<SubIndustryRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List sub-industries", operationId = "retrieveAllSubIndustries", description = "Returns every sub-industry, active and inactive.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubIndustryData.class))))
    @ApiResponse(responseCode = "403", description = "The user lacks READ_SUBINDUSTRY permission.")
    public List<SubIndustryData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve sub-industry template", operationId = "retrieveSubIndustryTemplate", description = "Returns the allowed status values and the active industries a sub-industry can belong to.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubIndustryApiResourceSwagger.GetSubIndustryTemplateResponse.class)))
    public Map<String, Object> retrieveTemplate() {
        final Map<String, Object> response = new HashMap<>();
        response.put("statusOptions", List.of("ACTIVE", "INACTIVE"));
        response.put("industryOptions", this.industryReadPlatformService.retrieveActiveForDropdown());
        return response;
    }

    @GET
    @Path("/{subIndustryId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve sub-industry", operationId = "retrieveSubIndustry", description = "Returns one sub-industry.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubIndustryData.class)))
    @ApiResponse(responseCode = "403", description = "The user lacks READ_SUBINDUSTRY permission.")
    @ApiResponse(responseCode = "404", description = "No sub-industry with this id (error.msg.subindustry.not.found).")
    public SubIndustryData retrieveOne(
            @PathParam("subIndustryId") @Parameter(description = "Sub-industry id", example = "1") final Long subIndustryId) {
        return this.readPlatformService.retrieveOne(subIndustryId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create sub-industry", operationId = "createSubIndustry", description = "Creates a sub-industry. subIndustryCode and subIndustryName are required. "
            + "subIndustryCode must be unique. status is ACTIVE or INACTIVE. The command can be held by maker-checker.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = SubIndustryRequest.class)))
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "A field fails validation: subIndustryCode or subIndustryName blank or too long, industryId not positive, status not ACTIVE/INACTIVE.")
    @ApiResponse(responseCode = "403", description = "The user lacks CREATE_SUBINDUSTRY permission, or subIndustryCode already exists (error.msg.subindustry.duplicate.code).")
    @ApiResponse(responseCode = "404", description = "industryId names an industry that does not exist (error.msg.industry.not.found).")
    public CommandProcessingResult create(final SubIndustryRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createSubIndustry()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{subIndustryId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update sub-industry", operationId = "updateSubIndustry", description = "Updates the fields sent. A changed subIndustryCode must stay unique. The command can be held by maker-checker.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = SubIndustryRequest.class)))
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "A field fails validation.")
    @ApiResponse(responseCode = "403", description = "The user lacks UPDATE_SUBINDUSTRY permission, or subIndustryCode already exists (error.msg.subindustry.duplicate.code).")
    @ApiResponse(responseCode = "404", description = "The sub-industry does not exist (error.msg.subindustry.not.found), or industryId names an industry that does not exist (error.msg.industry.not.found).")
    public CommandProcessingResult update(
            @PathParam("subIndustryId") @Parameter(description = "Sub-industry id", example = "1") final Long subIndustryId,
            final SubIndustryRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateSubIndustry(subIndustryId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{subIndustryId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete sub-industry", operationId = "deleteSubIndustry", description = "Deletes a sub-industry that is not in use. The command can be held by maker-checker.")
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "403", description = "The user lacks DELETE_SUBINDUSTRY permission, or the sub-industry is in use (error.msg.subindustry.in.use).")
    @ApiResponse(responseCode = "404", description = "No sub-industry with this id (error.msg.subindustry.not.found).")
    public CommandProcessingResult delete(
            @PathParam("subIndustryId") @Parameter(description = "Sub-industry id", example = "1") final Long subIndustryId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteSubIndustry(subIndustryId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
