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
import org.apache.fineract.infrastructure.interbranch.data.InterBranchGlRuleData;
import org.apache.fineract.infrastructure.interbranch.data.InterBranchGlRuleRequest;
import org.apache.fineract.infrastructure.interbranch.service.InterBranchGlRuleReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/interbranch/rules")
@Component
@Tag(name = "Inter-Branch GL Rules", description = "The GL account used to settle a transaction posted at one office for a client of another office.")
@RequiredArgsConstructor
public class InterBranchGlRuleApiResource {

    private static final String WRITE_OK = "Command accepted. When maker-checker holds the command, the body is "
            + "{ commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.";

    private final InterBranchGlRuleReadPlatformService readPlatformService;
    private final ToApiJsonSerializer<InterBranchGlRuleRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List inter-branch GL rules", operationId = "retrieveAllInterBranchGlRules", description = "Returns every inter-branch settlement rule.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = InterBranchGlRuleData.class))))
    @ApiResponse(responseCode = "403", description = "The user lacks READ_INTERBRANCHRULE permission.")
    public List<InterBranchGlRuleData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve inter-branch GL rule template", operationId = "retrieveInterBranchGlRuleTemplate", description = "Returns the allowed status values.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = InterBranchGlRuleApiResourceSwagger.GetInterBranchGlRuleTemplateResponse.class)))
    public Map<String, Object> retrieveTemplate() {
        final Map<String, Object> response = new HashMap<>();
        response.put("statusOptions", List.of("ACTIVE", "INACTIVE"));
        return response;
    }

    @GET
    @Path("/{ruleId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve inter-branch GL rule", operationId = "retrieveInterBranchGlRule", description = "Returns one settlement rule.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = InterBranchGlRuleData.class)))
    @ApiResponse(responseCode = "403", description = "The user lacks READ_INTERBRANCHRULE permission.")
    @ApiResponse(responseCode = "404", description = "No rule with this id (error.msg.inter.branch.gl.rule.not.found).")
    public InterBranchGlRuleData retrieveOne(
            @PathParam("ruleId") @Parameter(description = "Inter-branch GL rule id", example = "1") final Long ruleId) {
        return this.readPlatformService.retrieveOne(ruleId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create inter-branch GL rule", operationId = "createInterBranchGlRule", description = "Creates a settlement rule. glAccountId is required. "
            + "leftOfficeId and rightOfficeId are optional; both empty means the default rule. They must not be the same office. The command can be held by maker-checker.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = InterBranchGlRuleRequest.class)))
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "A field fails validation: glAccountId missing or not positive, an office id not positive, currencyCode longer than 3 characters, status not ACTIVE/INACTIVE.")
    @ApiResponse(responseCode = "403", description = "The user lacks CREATE_INTERBRANCHRULE permission, leftOfficeId equals rightOfficeId (error.msg.inter.branch.gl.rule.same.office), "
            + "or a default rule already exists (error.msg.inter.branch.gl.rule.default.duplicate).")
    @ApiResponse(responseCode = "404", description = "The GL account or an office does not exist.")
    public CommandProcessingResult create(final InterBranchGlRuleRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createInterBranchGlRule()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{ruleId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update inter-branch GL rule", operationId = "updateInterBranchGlRule", description = "Updates the fields sent. The command can be held by maker-checker.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = InterBranchGlRuleRequest.class)))
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "A field fails validation.")
    @ApiResponse(responseCode = "403", description = "The user lacks UPDATE_INTERBRANCHRULE permission, the two offices are the same (error.msg.inter.branch.gl.rule.same.office), "
            + "or a second default rule would be created (error.msg.inter.branch.gl.rule.default.duplicate).")
    @ApiResponse(responseCode = "404", description = "The rule, GL account, or an office does not exist (error.msg.inter.branch.gl.rule.not.found).")
    public CommandProcessingResult update(
            @PathParam("ruleId") @Parameter(description = "Inter-branch GL rule id", example = "1") final Long ruleId,
            final InterBranchGlRuleRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateInterBranchGlRule(ruleId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{ruleId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete inter-branch GL rule", operationId = "deleteInterBranchGlRule", description = "Deletes a settlement rule. The command can be held by maker-checker.")
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "403", description = "The user lacks DELETE_INTERBRANCHRULE permission.")
    @ApiResponse(responseCode = "404", description = "No rule with this id (error.msg.inter.branch.gl.rule.not.found).")
    public CommandProcessingResult delete(
            @PathParam("ruleId") @Parameter(description = "Inter-branch GL rule id", example = "1") final Long ruleId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteInterBranchGlRule(ruleId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
