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
package org.apache.fineract.treasury.api;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.COMMAND_ACTIVATE;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.COMMAND_CLOSE;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.COMMAND_COMPLETE_ACTION;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.COMMAND_DEACTIVATE;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.COMMAND_FIRE;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.TREASURY_CFP_ACTIVATION_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.TREASURY_CFP_TRIGGER_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.TREASURY_FUNDING_LINE_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.TREASURY_SCENARIO_RESOURCE_NAME;

import com.micropay.fineract.platform.command.MicropayCommandWrapperBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.treasury.data.TreasuryCfpActivationData;
import org.apache.fineract.treasury.data.TreasuryCfpSummaryData;
import org.apache.fineract.treasury.data.TreasuryCfpTriggerData;
import org.apache.fineract.treasury.data.TreasuryFundingLineData;
import org.apache.fineract.treasury.data.TreasuryScenarioData;
import org.apache.fineract.treasury.service.TreasuryCfpService;
import org.apache.fineract.treasury.service.TreasuryFundingLineService;
import org.apache.fineract.treasury.service.TreasuryScenarioService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/treasury")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Treasury Stress and CFP", description = "Stress scenarios, the contingency funding plan's funding lines, triggers and activations, and its summary")
@RequiredArgsConstructor
public class TreasuryStressApiResource {

    private final PlatformSecurityContext context;
    private final TreasuryScenarioService scenarioService;
    private final TreasuryFundingLineService fundingLineService;
    private final TreasuryCfpService cfpService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    // ---------- scenarios ----------

    @GET
    @Path("scenarios")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List stress scenarios with their shocks, every version")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryScenarioData.class))))
    public List<TreasuryScenarioData> retrieveScenarios() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_SCENARIO_RESOURCE_NAME);
        return this.scenarioService.retrieveAll();
    }

    @GET
    @Path("scenarios/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "A stress scenario version")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryScenarioData.class)))
    public TreasuryScenarioData retrieveScenario(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_SCENARIO_RESOURCE_NAME);
        return this.scenarioService.retrieveOne(id);
    }

    @POST
    @Path("scenarios")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a scenario version (DRAFT), or copy one", description = "code, name, scenarioType (IDIOSYNCRATIC, MARKET_WIDE, COMBINED), severity, effectiveDate, description, shocks [{type (MULTIPLY, ADD_OUTFLOW_PCT_OF_BALANCE, REMOVE_ROLLOVER, HAIRCUT_LIQUID_ASSET, FREEZE_LINE), label, source, lineCode, productId, bucketCode, side (INFLOW, OUTFLOW, BOTH), factor, assetClass, fundingSourceType, fundingLineId}]; or copyFromId with any of them. An existing code gets its next version")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createScenario(@Parameter(hidden = true) final String body) {
        return command(TREASURY_SCENARIO_RESOURCE_NAME, "CREATE", null, "/treasury/scenarios", body);
    }

    @PUT
    @Path("scenarios/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Change a draft scenario", description = "name, scenarioType, severity, effectiveDate, description, shocks (replaced)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateScenario(@PathParam("id") final Long id, @Parameter(hidden = true) final String body) {
        return command(TREASURY_SCENARIO_RESOURCE_NAME, "UPDATE", id, "/treasury/scenarios/" + id, body);
    }

    @DELETE
    @Path("scenarios/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete a draft scenario")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult deleteScenario(@PathParam("id") final Long id) {
        return command(TREASURY_SCENARIO_RESOURCE_NAME, "DELETE", id, "/treasury/scenarios/" + id, "{}");
    }

    @POST
    @Path("scenarios/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Activate a draft scenario, or retire the active one", description = "command=activate (supersedes the code's active version) or deactivate (the active version is SUPERSEDED)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult activateScenario(@PathParam("id") final Long id, @QueryParam("command") final String commandParam,
            @Parameter(hidden = true) final String body) {
        if (COMMAND_DEACTIVATE.equalsIgnoreCase(commandParam)) {
            return command(TREASURY_SCENARIO_RESOURCE_NAME, "DEACTIVATE", id, "/treasury/scenarios/" + id + "?command=deactivate", body);
        }
        only(commandParam, COMMAND_ACTIVATE);
        return command(TREASURY_SCENARIO_RESOURCE_NAME, "ACTIVATE", id, "/treasury/scenarios/" + id + "?command=activate", body);
    }

    // ---------- funding lines ----------

    @GET
    @Path("funding-lines")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "The contingency funding plan's funding lines", description = "Filters: currency, status")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryFundingLineData.class))))
    public List<TreasuryFundingLineData> retrieveFundingLines(@QueryParam("currency") final String currency,
            @QueryParam("status") final String status) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_FUNDING_LINE_RESOURCE_NAME);
        return this.fundingLineService.retrieveAll(StringUtils.trimToNull(currency), StringUtils.trimToNull(status));
    }

    @GET
    @Path("funding-lines/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "A funding line")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryFundingLineData.class)))
    public TreasuryFundingLineData retrieveFundingLine(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_FUNDING_LINE_RESOURCE_NAME);
        return this.fundingLineService.retrieveOne(id);
    }

    @POST
    @Path("funding-lines")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Add a funding line", description = "code, name, sourceType (COMMITTED_LINE_RECEIVED, UNCOMMITTED_LINE, REPO_BOU, ASSET_SALE, SHAREHOLDER, DEPOSIT_DRIVE, OTHER), currencyCode, committedAmount, drawnAmount, availableAmount (default committed less drawn), daysToAccess; optional counterpartyId, expiryDate, ownerUserId, ownerName, status, includeInCapacity (default: committed lines received), description")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createFundingLine(@Parameter(hidden = true) final String body) {
        return command(TREASURY_FUNDING_LINE_RESOURCE_NAME, "CREATE", null, "/treasury/funding-lines", body);
    }

    @PUT
    @Path("funding-lines/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Change a funding line", description = "Any field but code, laid over the line (null clears one)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateFundingLine(@PathParam("id") final Long id, @Parameter(hidden = true) final String body) {
        return command(TREASURY_FUNDING_LINE_RESOURCE_NAME, "UPDATE", id, "/treasury/funding-lines/" + id, body);
    }

    // ---------- triggers ----------

    @GET
    @Path("cfp/triggers")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "The contingency funding plan's triggers with their actions")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryCfpTriggerData.class))))
    public List<TreasuryCfpTriggerData> retrieveTriggers() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_CFP_TRIGGER_RESOURCE_NAME);
        return this.cfpService.retrieveTriggers();
    }

    @GET
    @Path("cfp/triggers/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "A trigger")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryCfpTriggerData.class)))
    public TreasuryCfpTriggerData retrieveTrigger(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_CFP_TRIGGER_RESOURCE_NAME);
        return this.cfpService.retrieveTrigger(id);
    }

    @POST
    @Path("cfp/triggers")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Add a trigger (INACTIVE)", description = "code, name, conditionType (LIMIT_BREACH with limitId or metric and level; SURVIVAL_BELOW with survivalDays and optional scenarioCode; MANUAL), stage (WATCH, ALERT, CRISIS), notifyRoleId, description, actions [{description, ownerRoleId, ownerUserId, fundingLineId}] in order")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createTrigger(@Parameter(hidden = true) final String body) {
        return command(TREASURY_CFP_TRIGGER_RESOURCE_NAME, "CREATE", null, "/treasury/cfp/triggers", body);
    }

    @PUT
    @Path("cfp/triggers/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Change a trigger", description = "Any field but code, laid over the trigger; actions sent replace its actions")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateTrigger(@PathParam("id") final Long id, @Parameter(hidden = true) final String body) {
        return command(TREASURY_CFP_TRIGGER_RESOURCE_NAME, "UPDATE", id, "/treasury/cfp/triggers/" + id, body);
    }

    @POST
    @Path("cfp/triggers/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Activate, deactivate or fire a trigger", description = "command=activate|deactivate|fire (a manual event: opens an activation); optional note")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnTrigger(@PathParam("id") final Long id, @QueryParam("command") final String commandParam,
            @Parameter(hidden = true) final String body) {
        for (final String[] c : new String[][] { { COMMAND_ACTIVATE, "ACTIVATE" }, { COMMAND_DEACTIVATE, "DEACTIVATE" },
                { COMMAND_FIRE, "FIRE" } }) {
            if (c[0].equalsIgnoreCase(commandParam)) {
                return command(TREASURY_CFP_TRIGGER_RESOURCE_NAME, c[1], id, "/treasury/cfp/triggers/" + id + "?command=" + c[0], body);
            }
        }
        throw new UnrecognizedQueryParamException("command", commandParam,
                new Object[] { COMMAND_ACTIVATE, COMMAND_DEACTIVATE, COMMAND_FIRE });
    }

    // ---------- activations and summary ----------

    @GET
    @Path("cfp/activations")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Activations of the plan", description = "Filter: status (OPEN, CLOSED)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryCfpActivationData.class))))
    public List<TreasuryCfpActivationData> retrieveActivations(@QueryParam("status") final String status) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_CFP_ACTIVATION_RESOURCE_NAME);
        return this.cfpService.retrieveActivations(StringUtils.trimToNull(status));
    }

    @GET
    @Path("cfp/activations/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "An activation with its actions")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryCfpActivationData.class)))
    public TreasuryCfpActivationData retrieveActivation(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_CFP_ACTIVATION_RESOURCE_NAME);
        return this.cfpService.retrieveActivation(id);
    }

    @POST
    @Path("cfp/activations/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Complete an action of an activation, or close it", description = "command=complete-action (actionId, optional comment) or close (comment)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnActivation(@PathParam("id") final Long id, @QueryParam("command") final String commandParam,
            @Parameter(hidden = true) final String body) {
        if (COMMAND_COMPLETE_ACTION.equalsIgnoreCase(commandParam)) {
            return command(TREASURY_CFP_ACTIVATION_RESOURCE_NAME, "COMPLETEACTION", id,
                    "/treasury/cfp/activations/" + id + "?command=complete-action", body);
        }
        if (COMMAND_CLOSE.equalsIgnoreCase(commandParam)) {
            return command(TREASURY_CFP_ACTIVATION_RESOURCE_NAME, "CLOSE", id, "/treasury/cfp/activations/" + id + "?command=close", body);
        }
        throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { COMMAND_COMPLETE_ACTION, COMMAND_CLOSE });
    }

    @GET
    @Path("cfp/summary")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "The contingency funding plan on a date", description = "asOfDate (default the business date): per currency the funding available by source and days to access, each scenario's worst stressed gap and survival, the coverage of the gaps by the funding available, and the open activations")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryCfpSummaryData.class)))
    public TreasuryCfpSummaryData retrieveSummary(@QueryParam("asOfDate") final String asOfDate) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_FUNDING_LINE_RESOURCE_NAME);
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_CFP_ACTIVATION_RESOURCE_NAME);
        return this.cfpService.summary(date("asOfDate", asOfDate));
    }

    // ---------- helpers ----------

    private CommandProcessingResult command(final String entity, final String action, final Long entityId, final String href,
            final String json) {
        return this.commandsSourceWritePlatformService.logCommandSource(new MicropayCommandWrapperBuilder()
                .treasuryCommand(entity, action, entityId, href).withJson(StringUtils.isBlank(json) ? "{}" : json).build());
    }

    private static void only(final String commandParam, final String expected) {
        if (!expected.equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { expected });
        }
    }

    private static LocalDate date(final String name, final String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new PlatformApiDataValidationException("validation.msg.treasury.query.date.invalid",
                    "The query parameter `" + name + "` must be a date such as 2026-10-16", name, e, value);
        }
    }
}
