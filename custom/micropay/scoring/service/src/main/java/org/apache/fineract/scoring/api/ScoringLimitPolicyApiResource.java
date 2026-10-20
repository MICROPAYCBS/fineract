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
import static org.apache.fineract.scoring.api.ScoringApiConstants.SCORING_LIMIT_POLICY_RESOURCE_NAME;

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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.scoring.data.ScoringLimitPolicyData;
import org.apache.fineract.scoring.data.ScoringLimitPolicyRequest;
import org.apache.fineract.scoring.service.ScoringLimitPlatformService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/scoring/limit-policies")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Credit Scoring Limit Policies", description = "How the nightly behavioural run gives pre-approved limits")
@RequiredArgsConstructor
public class ScoringLimitPolicyApiResource {

    private final PlatformSecurityContext context;
    private final ScoringLimitPlatformService limitService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List limit policies")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScoringLimitPolicyData.class))))
    public List<ScoringLimitPolicyData> retrievePolicies() {
        this.context.authenticatedUser().validateHasReadPermission(SCORING_LIMIT_POLICY_RESOURCE_NAME);
        return this.limitService.retrievePolicies();
    }

    @GET
    @Path("{policyId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a limit policy")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ScoringLimitPolicyData.class)))
    public ScoringLimitPolicyData retrievePolicy(@PathParam("policyId") final Long policyId) {
        this.context.authenticatedUser().validateHasReadPermission(SCORING_LIMIT_POLICY_RESOURCE_NAME);
        return this.limitService.retrievePolicy(policyId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a limit policy", description = "Which clients are scored nightly for a loan product, with which behavioural scorecard, and the limit for each grade")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ScoringLimitPolicyRequest.class)))
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createPolicy(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService
                .logCommandSource(new MicropayCommandWrapperBuilder().createScoringLimitPolicy().withJson(apiRequestBodyAsJson).build());
    }

    @PUT
    @Path("{policyId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Replace a limit policy", description = "Takes effect from the next nightly run; limits already given stay until they expire")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ScoringLimitPolicyRequest.class)))
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updatePolicy(@PathParam("policyId") final Long policyId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService.logCommandSource(
                new MicropayCommandWrapperBuilder().updateScoringLimitPolicy(policyId).withJson(apiRequestBodyAsJson).build());
    }
}
