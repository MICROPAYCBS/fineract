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

import static org.apache.fineract.scoring.api.ScoringApiConstants.COMMAND_ACTIVATE;
import static org.apache.fineract.scoring.api.ScoringApiConstants.COMMAND_RETIRE;
import static org.apache.fineract.scoring.api.ScoringApiConstants.COMMAND_TEST;
import static org.apache.fineract.scoring.api.ScoringApiConstants.LOAN_ID_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.scoring.api.ScoringApiConstants.SCORECARD_RESOURCE_NAME;

import com.google.gson.JsonElement;
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
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.scoring.data.ScorecardData;
import org.apache.fineract.scoring.data.ScorecardRequest;
import org.apache.fineract.scoring.engine.ScoringResult;
import org.apache.fineract.scoring.service.ScoringReadPlatformService;
import org.apache.fineract.scoring.service.ScoringService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/scoring/scorecards")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Credit Scorecards", description = "Build, test and activate versioned expert scorecards")
@RequiredArgsConstructor
public class ScoringScorecardApiResource {

    private final PlatformSecurityContext context;
    private final ScoringReadPlatformService readPlatformService;
    private final ScoringService scoringService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final FromJsonHelper fromJsonHelper;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List scorecards", description = "Lists scorecard versions, newest version first within each code")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScorecardData.class))))
    public List<ScorecardData> retrieveAll(@QueryParam("code") @Parameter(description = "Optional scorecard code") final String code,
            @QueryParam("status") @Parameter(description = "Optional status: DRAFT, ACTIVE or RETIRED") final String status) {
        this.context.authenticatedUser().validateHasReadPermission(SCORECARD_RESOURCE_NAME);
        return this.readPlatformService.retrieveScorecards(code, status);
    }

    @GET
    @Path("{scorecardId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a scorecard version", description = "Includes bins, grade bands and rules")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ScorecardData.class)))
    @ApiResponse(responseCode = "404", description = "Scorecard not found")
    public ScorecardData retrieveOne(@PathParam("scorecardId") final Long scorecardId) {
        this.context.authenticatedUser().validateHasReadPermission(SCORECARD_RESOURCE_NAME);
        return this.readPlatformService.retrieveScorecard(scorecardId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a scorecard draft", description = "Creates a DRAFT. Using the code of an existing scorecard creates its next version.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ScorecardRequest.class)))
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult create(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new MicropayCommandWrapperBuilder().createScorecard().withJson(apiRequestBodyAsJson).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("{scorecardId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Replace a scorecard draft", description = "Only a DRAFT can change; bins, bands and rules are replaced as a whole")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ScorecardRequest.class)))
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult update(@PathParam("scorecardId") final Long scorecardId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new MicropayCommandWrapperBuilder().updateScorecard(scorecardId)
                .withJson(apiRequestBodyAsJson).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @POST
    @Path("{scorecardId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Activate, retire or test a scorecard version", description = "command=activate puts a DRAFT live and retires the previous version of the same code (maker-checker by default). "
            + "command=retire takes a version out of use. command=test scores the loan in the body ({\"loanId\": 1}) with this version and saves nothing.")
    @ApiResponse(responseCode = "200", description = "OK")
    public Object stateTransitionOrTest(@PathParam("scorecardId") final Long scorecardId,
            @QueryParam("command") @Parameter(description = "activate, retire or test", required = true) final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        if (COMMAND_TEST.equalsIgnoreCase(commandParam)) {
            this.context.authenticatedUser().validateHasReadPermission(SCORECARD_RESOURCE_NAME);
            return test(scorecardId, apiRequestBodyAsJson);
        }
        final CommandWrapper commandRequest;
        if (COMMAND_ACTIVATE.equalsIgnoreCase(commandParam)) {
            commandRequest = new MicropayCommandWrapperBuilder().activateScorecard(scorecardId).build();
        } else if (COMMAND_RETIRE.equalsIgnoreCase(commandParam)) {
            commandRequest = new MicropayCommandWrapperBuilder().retireScorecard(scorecardId).build();
        } else {
            throw new UnrecognizedQueryParamException("command", commandParam,
                    new Object[] { COMMAND_ACTIVATE, COMMAND_RETIRE, COMMAND_TEST });
        }
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    private ScoringResult test(final Long scorecardId, final String json) {
        if (json == null || json.isBlank()) {
            throw new InvalidJsonException();
        }
        final JsonElement element = this.fromJsonHelper.parse(json);
        final Long loanId = this.fromJsonHelper.extractLongNamed(LOAN_ID_PARAM, element);
        if (loanId == null) {
            throw new InvalidJsonException();
        }
        return this.scoringService.test(scorecardId, loanId);
    }
}
