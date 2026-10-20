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

import static org.apache.fineract.scoring.api.ScoringApiConstants.COMMAND_OVERRIDE;
import static org.apache.fineract.scoring.api.ScoringApiConstants.COMMAND_SCORE;
import static org.apache.fineract.scoring.api.ScoringApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.scoring.api.ScoringApiConstants.SCORECARD_RESOURCE_NAME;
import static org.apache.fineract.scoring.api.ScoringApiConstants.SCORING_AUTHORITY_RULE_RESOURCE_NAME;
import static org.apache.fineract.scoring.api.ScoringApiConstants.SCORING_DECISION_RESOURCE_NAME;
import static org.apache.fineract.scoring.api.ScoringApiConstants.SCORING_LIMIT_RESOURCE_NAME;
import static org.apache.fineract.scoring.api.ScoringApiConstants.SCORING_PRODUCT_CONFIG_RESOURCE_NAME;

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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.scoring.data.ApprovalRouteData;
import org.apache.fineract.scoring.data.ScoringCharacteristicData;
import org.apache.fineract.scoring.data.ScoringClientLimitData;
import org.apache.fineract.scoring.data.ScoringDecisionData;
import org.apache.fineract.scoring.data.ScoringProductConfigData;
import org.apache.fineract.scoring.data.ScoringOverrideRequest;
import org.apache.fineract.scoring.data.ScoringProductConfigRequest;
import org.apache.fineract.scoring.service.ScoringAuthorityReadPlatformService;
import org.apache.fineract.scoring.service.ScoringLimitPlatformService;
import org.apache.fineract.scoring.service.ScoringReadPlatformService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/scoring")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Credit Scoring", description = "Scoring inputs, product configuration and scoring decisions")
@RequiredArgsConstructor
public class ScoringApiResource {

    private final PlatformSecurityContext context;
    private final ScoringReadPlatformService readPlatformService;
    private final ScoringAuthorityReadPlatformService authorityReadPlatformService;
    private final ScoringLimitPlatformService limitService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Path("characteristics")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List built-in scoring inputs", description = "Datatable columns can also be used, as DT:<datatable>.<column>")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScoringCharacteristicData.class))))
    public List<ScoringCharacteristicData> retrieveCharacteristics() {
        this.context.authenticatedUser().validateHasReadPermission(SCORECARD_RESOURCE_NAME);
        return this.readPlatformService.retrieveCharacteristics();
    }

    @GET
    @Path("products/{productId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Scoring configuration of a loan product", description = "Mode OFF is returned when the product has no configuration")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ScoringProductConfigData.class)))
    public ScoringProductConfigData retrieveProductConfig(@PathParam("productId") final Long productId) {
        this.context.authenticatedUser().validateHasReadPermission(SCORING_PRODUCT_CONFIG_RESOURCE_NAME);
        return this.readPlatformService.retrieveProductConfig(productId);
    }

    @PUT
    @Path("products/{productId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Set the scorecard and mode of a loan product", description = "mode is OFF, ADVISORY (record only) or ENFORCED (a DECLINE blocks approval)")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ScoringProductConfigRequest.class)))
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateProductConfig(@PathParam("productId") final Long productId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new MicropayCommandWrapperBuilder().updateScoringProductConfig(productId)
                .withJson(apiRequestBodyAsJson).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @GET
    @Path("decisions")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List scoring decisions", description = "Newest first, for a loan or for a client, with reason codes, rule results, points and the input snapshot")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScoringDecisionData.class))))
    public List<ScoringDecisionData> retrieveDecisions(@QueryParam("loanId") final Long loanId,
            @QueryParam("clientId") final Long clientId) {
        this.context.authenticatedUser().validateHasReadPermission(SCORING_DECISION_RESOURCE_NAME);
        return this.readPlatformService.retrieveDecisions(loanId, clientId);
    }

    @POST
    @Path("decisions/{decisionId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Override a scoring decision", description = "command=override changes the outcome of a loan's latest decision while the loan awaits approval. Body: toOutcome (APPROVE, REFER or DECLINE), reasonId (a value of the ScoringOverrideReason code) and an optional comment.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ScoringOverrideRequest.class)))
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult overrideDecision(@PathParam("decisionId") final Long decisionId,
            @QueryParam("command") @Parameter(description = "override", required = true) final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        if (!COMMAND_OVERRIDE.equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { COMMAND_OVERRIDE });
        }
        return this.commandsSourceWritePlatformService.logCommandSource(
                new MicropayCommandWrapperBuilder().overrideScoringDecision(decisionId).withJson(apiRequestBodyAsJson).build());
    }

    @GET
    @Path("loans/{loanId}/approval-route")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Approval stages a loan needs", description = "Applies the authority rules to the loan as it is now and lists each stage of the task's active workflow as required or skipped, with the reason")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ApprovalRouteData.class)))
    public ApprovalRouteData retrieveApprovalRoute(@PathParam("loanId") final Long loanId,
            @QueryParam("taskPermissionCode") @Parameter(description = "defaults to APPROVE_LOAN") final String taskPermissionCode) {
        this.context.authenticatedUser().validateHasReadPermission(SCORING_AUTHORITY_RULE_RESOURCE_NAME);
        return this.authorityReadPlatformService.retrieveApprovalRoute(loanId, taskPermissionCode);
    }

    @POST
    @Path("loans/{loanId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Score a loan now", description = "command=score records a MANUAL decision with the product's active scorecard")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult scoreLoan(@PathParam("loanId") final Long loanId,
            @QueryParam("command") @Parameter(description = "score", required = true) final String commandParam) {
        if (!COMMAND_SCORE.equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { COMMAND_SCORE });
        }
        return this.commandsSourceWritePlatformService.logCommandSource(new MicropayCommandWrapperBuilder().scoreLoan(loanId).build());
    }

    @GET
    @Path("clients/{clientId}/limits")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "A client's pre-approved limits", description = "The latest limit under each policy, with the amount used and available today")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScoringClientLimitData.class))))
    public List<ScoringClientLimitData> retrieveClientLimits(@PathParam("clientId") final Long clientId) {
        this.context.authenticatedUser().validateHasReadPermission(SCORING_LIMIT_RESOURCE_NAME);
        return this.limitService.retrieveClientLimits(clientId);
    }
}
