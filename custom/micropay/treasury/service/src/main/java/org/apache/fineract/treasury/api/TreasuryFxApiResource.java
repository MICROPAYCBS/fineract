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
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.COMMAND_CANCEL;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.COMMAND_REVERSE;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.COMMAND_RUN;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.TREASURY_CORE_CAPITAL_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.TREASURY_FX_POSITION_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.TREASURY_FX_RATE_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.TREASURY_FX_REVALUATION_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryFxApiConstants.TREASURY_FX_REVAL_CONFIG_RESOURCE_NAME;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micropay.fineract.platform.command.MicropayCommandWrapperBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.treasury.data.TreasuryConsolidatedLadderData;
import org.apache.fineract.treasury.data.TreasuryCoreCapitalData;
import org.apache.fineract.treasury.data.TreasuryFxPositionData;
import org.apache.fineract.treasury.data.TreasuryFxRateData;
import org.apache.fineract.treasury.data.TreasuryFxRevalConfigData;
import org.apache.fineract.treasury.data.TreasuryFxRevaluationData;
import org.apache.fineract.treasury.service.TreasuryFxPositionService;
import org.apache.fineract.treasury.service.TreasuryFxRateService;
import org.apache.fineract.treasury.service.TreasuryFxRevaluationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/treasury")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Treasury FX", description = "Exchange rates, FX revaluation, currency positions and the net open position, core capital")
@RequiredArgsConstructor
public class TreasuryFxApiResource {

    private final PlatformSecurityContext context;
    private final TreasuryFxRateService rateService;
    private final TreasuryFxRevaluationService revaluationService;
    private final TreasuryFxPositionService positionService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    // ---------- rates ----------

    @GET
    @Path("fx-rates")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Exchange rates", description = "pair (the quote currency, or UGX/USD), date (the rate of each type in effect on it), or from and to (every rate entered in the period)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryFxRateData.class))))
    public List<TreasuryFxRateData> retrieveRates(@QueryParam("pair") final String pair, @QueryParam("date") final String date,
            @QueryParam("from") final String from, @QueryParam("to") final String to) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_FX_RATE_RESOURCE_NAME);
        return this.rateService.retrieve(StringUtils.trimToNull(pair), date("date", date), date("from", from), date("to", to));
    }

    @POST
    @Path("fx-rates")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Enter a rate, or a batch", description = "quoteCurrency, rateDate, rate (base currency units for one quote unit), rateType (MID default, BUY, SELL), source (MANUAL default, BOU, IMPORT), note; or rates [...] (IMPORT by default). A rate of the same pair, date, type and source replaces the earlier one. Maker-checker capable")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createRate(@Parameter(hidden = true) final String body) {
        return command(TREASURY_FX_RATE_RESOURCE_NAME, "CREATE", null, "/treasury/fx-rates", body);
    }

    @POST
    @Path("fx-rates/batch")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Import a batch of rates", description = "{rates: [...]}: the same command as one rate")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult importRates(@Parameter(hidden = true) final String body) {
        return command(TREASURY_FX_RATE_RESOURCE_NAME, "CREATE", null, "/treasury/fx-rates/batch", body);
    }

    @POST
    @Path("fx-rates/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Cancel a rate", description = "command=cancel; optional note")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult cancelRate(@PathParam("id") final Long id, @QueryParam("command") final String commandParam,
            @Parameter(hidden = true) final String body) {
        only(commandParam, COMMAND_CANCEL);
        return command(TREASURY_FX_RATE_RESOURCE_NAME, "CANCEL", id, "/treasury/fx-rates/" + id + "?command=cancel", body);
    }

    // ---------- revaluation ----------

    @GET
    @Path("fx/revaluation-config")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "The foreign currency GL accounts revalued and their accounts")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryFxRevalConfigData.class))))
    public List<TreasuryFxRevalConfigData> retrieveConfigs() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_FX_REVAL_CONFIG_RESOURCE_NAME);
        return this.revaluationService.retrieveConfigs();
    }

    @PUT
    @Path("fx/revaluation-config")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Set how a foreign currency GL account is revalued", description = "glAccountId, currencyCode, adjustmentGlAccountId (base currency value of the balance), gainGlAccountId (income), lossGlAccountId (expense), active, note; one per GL account and currency")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateConfig(@Parameter(hidden = true) final String body) {
        return command(TREASURY_FX_REVAL_CONFIG_RESOURCE_NAME, "UPDATE", null, "/treasury/fx/revaluation-config", body);
    }

    @GET
    @Path("fx/revaluations")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "FX revaluations", description = "Filters: date, currency, glAccountId, status (POSTED, ZERO, REVERSED); the latest 500")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryFxRevaluationData.class))))
    public List<TreasuryFxRevaluationData> retrieveRevaluations(@QueryParam("date") final String date,
            @QueryParam("currency") final String currency, @QueryParam("glAccountId") final Long glAccountId,
            @QueryParam("status") final String status) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_FX_REVALUATION_RESOURCE_NAME);
        return this.revaluationService.retrieveAll(date("date", date), StringUtils.trimToNull(currency), glAccountId,
                StringUtils.trimToNull(status));
    }

    @GET
    @Path("fx/revaluations/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "An FX revaluation")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryFxRevaluationData.class)))
    public TreasuryFxRevaluationData retrieveRevaluation(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_FX_REVALUATION_RESOURCE_NAME);
        return this.revaluationService.retrieveOne(id);
    }

    @POST
    @Path("fx/revaluations")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Run the FX revaluation", description = "command=run, asOfDate (default the business date), optional currency; every active setting, each office; a rerun posts only the difference")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult runRevaluation(@QueryParam("command") final String commandParam,
            @QueryParam("asOfDate") final String asOfDate, @QueryParam("currency") final String currency,
            @Parameter(hidden = true) final String body) {
        only(commandParam, COMMAND_RUN);
        final JsonObject json = StringUtils.isBlank(body) ? new JsonObject() : object(body);
        if (StringUtils.isNotBlank(asOfDate)) {
            json.addProperty("asOfDate", asOfDate.trim());
        }
        if (StringUtils.isNotBlank(currency)) {
            json.addProperty("currencyCode", currency.trim());
        }
        return command(TREASURY_FX_REVALUATION_RESOURCE_NAME, "RUN", null, "/treasury/fx/revaluations?command=run", json.toString());
    }

    @POST
    @Path("fx/revaluations/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Reverse a revaluation", description = "command=reverse; the latest posted revaluation of its account, dated the business date")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult reverseRevaluation(@PathParam("id") final Long id, @QueryParam("command") final String commandParam,
            @Parameter(hidden = true) final String body) {
        only(commandParam, COMMAND_REVERSE);
        return command(TREASURY_FX_REVALUATION_RESOURCE_NAME, "REVERSE", id, "/treasury/fx/revaluations/" + id + "?command=reverse", body);
    }

    // ---------- positions ----------

    @GET
    @Path("fx/positions")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Currency positions and the net open position", description = "asOfDate (default the business date), optional officeId")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryFxPositionData.class)))
    public TreasuryFxPositionData retrievePositions(@QueryParam("asOfDate") final String asOfDate,
            @QueryParam("officeId") final Long officeId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_FX_POSITION_RESOURCE_NAME);
        return this.positionService.positions(date("asOfDate", asOfDate), officeId);
    }

    @GET
    @Path("fx/consolidated-ladder")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "The ladders of every currency in the base currency", description = "asOfDate (default the business date), optional officeId: each currency's latest ladder run of the date at the MID rate, summed per bucket")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryConsolidatedLadderData.class)))
    public TreasuryConsolidatedLadderData retrieveConsolidatedLadder(@QueryParam("asOfDate") final String asOfDate,
            @QueryParam("officeId") final Long officeId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_FX_POSITION_RESOURCE_NAME);
        return this.positionService.consolidatedLadder(date("asOfDate", asOfDate), officeId);
    }

    // ---------- core capital ----------

    @GET
    @Path("settings/core-capital")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Core capital by effective date")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryCoreCapitalData.class))))
    public List<TreasuryCoreCapitalData> retrieveCoreCapital() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_CORE_CAPITAL_RESOURCE_NAME);
        return this.positionService.retrieveCoreCapital();
    }

    @POST
    @Path("settings/core-capital")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Set core capital from a date", description = "effectiveDate, amount (base currency), note; maker-checker capable")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createCoreCapital(@Parameter(hidden = true) final String body) {
        return command(TREASURY_CORE_CAPITAL_RESOURCE_NAME, "CREATE", null, "/treasury/settings/core-capital", body);
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

    private static JsonObject object(final String body) {
        final JsonElement element = JsonParser.parseString(body);
        if (!element.isJsonObject()) {
            throw new InvalidJsonException();
        }
        return element.getAsJsonObject();
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
