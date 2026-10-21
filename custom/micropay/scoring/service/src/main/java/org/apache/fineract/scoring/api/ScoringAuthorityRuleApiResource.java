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
package org.apache.fineract.scoring.api;

import static org.apache.fineract.scoring.api.ScoringApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.scoring.api.ScoringApiConstants.SCORING_AUTHORITY_RULE_RESOURCE_NAME;

import com.micropay.fineract.platform.command.MicropayCommandWrapperBuilder;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.scoring.data.ScoringAuthorityRuleData;
import org.apache.fineract.scoring.data.ScoringAuthorityRuleRequest;
import org.apache.fineract.scoring.service.ScoringAuthorityReadPlatformService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/scoring/authority-rules")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Credit Scoring Authority", description = "Approval authority matrix: when each approval workflow stage is required")
@RequiredArgsConstructor
public class ScoringAuthorityRuleApiResource {

    private final PlatformSecurityContext context;
    private final ScoringAuthorityReadPlatformService readPlatformService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List authority rules", description = "Optionally for one task, such as APPROVE_LOAN")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScoringAuthorityRuleData.class))))
    public List<ScoringAuthorityRuleData> retrieveRules(@QueryParam("taskPermissionCode") final String taskPermissionCode) {
        this.context.authenticatedUser().validateHasReadPermission(SCORING_AUTHORITY_RULE_RESOURCE_NAME);
        return this.readPlatformService.retrieveRules(taskPermissionCode);
    }

    @GET
    @Path("{ruleId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve an authority rule")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ScoringAuthorityRuleData.class)))
    public ScoringAuthorityRuleData retrieveRule(@PathParam("ruleId") final Long ruleId) {
        this.context.authenticatedUser().validateHasReadPermission(SCORING_AUTHORITY_RULE_RESOURCE_NAME);
        return this.readPlatformService.retrieveRule(ruleId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create an authority rule", description = "The stage is required when any of its rules matches; a stage with no rules is always required")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ScoringAuthorityRuleRequest.class)))
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createRule(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService
                .logCommandSource(new MicropayCommandWrapperBuilder().createScoringAuthorityRule().withJson(apiRequestBodyAsJson).build());
    }

    @PUT
    @Path("{ruleId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Replace an authority rule", description = "Every field is replaced; leave a condition out to clear it")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ScoringAuthorityRuleRequest.class)))
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateRule(@PathParam("ruleId") final Long ruleId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService.logCommandSource(
                new MicropayCommandWrapperBuilder().updateScoringAuthorityRule(ruleId).withJson(apiRequestBodyAsJson).build());
    }

    @DELETE
    @Path("{ruleId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete an authority rule")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult deleteRule(@PathParam("ruleId") final Long ruleId) {
        return this.commandsSourceWritePlatformService
                .logCommandSource(new MicropayCommandWrapperBuilder().deleteScoringAuthorityRule(ruleId).build());
    }
}
