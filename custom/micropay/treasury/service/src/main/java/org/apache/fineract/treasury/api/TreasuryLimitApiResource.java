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
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.COMMAND_ACKNOWLEDGE;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.COMMAND_ACTIVATE;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.COMMAND_DEACTIVATE;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.COMMAND_EVALUATE;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.COMMAND_RESOLVE;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.TREASURY_BREACH_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryLimitApiConstants.TREASURY_LIMIT_RESOURCE_NAME;

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
import org.apache.fineract.treasury.data.TreasuryBreachData;
import org.apache.fineract.treasury.data.TreasuryLimitData;
import org.apache.fineract.treasury.data.TreasuryLimitEvaluationData;
import org.apache.fineract.treasury.service.TreasuryBreachService;
import org.apache.fineract.treasury.service.TreasuryLimitService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/treasury")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Treasury Limits", description = "Limits over ratios, gaps, concentrations, exposures and balances, their evaluations and the breach log")
@RequiredArgsConstructor
public class TreasuryLimitApiResource {

    private final PlatformSecurityContext context;
    private final TreasuryLimitService limitService;
    private final TreasuryBreachService breachService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    // ---------- limits ----------

    @GET
    @Path("limits")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List limits", description = "Filters: status (ACTIVE, INACTIVE), metric")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryLimitData.class))))
    public List<TreasuryLimitData> retrieveLimits(@QueryParam("status") final String status, @QueryParam("metric") final String metric) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_LIMIT_RESOURCE_NAME);
        return this.limitService.retrieveAll(StringUtils.trimToNull(status), StringUtils.trimToNull(metric));
    }

    @GET
    @Path("limits/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "A limit with its version history")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryLimitData.class)))
    public TreasuryLimitData retrieveLimit(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_LIMIT_RESOURCE_NAME);
        return this.limitService.retrieveOne(id);
    }

    @POST
    @Path("limits")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a limit (INACTIVE), or evaluate the limits", description = "Create: code, name, metric (RATIO, BUCKET_GAP, DEPOSITOR_CONCENTRATION, FUNDING_CONCENTRATION, COUNTERPARTY_EXPOSURE, ACCOUNT_BALANCE), scopeType (INSTITUTION, CURRENCY, OFFICE, COUNTERPARTY, ACCOUNT) and scopeId, currencyCode, comparison (MIN, MAX), warningLevel, hardLevel, unit (PERCENT, AMOUNT, DAYS) or useOwnLevels; metricKey (ratio or bucket code), gapType, gapBasis, topN; effectiveFrom, effectiveTo, notifyRoleId, description. command=evaluate: asOfDate (default the business date), currency, officeId, or runId; as query parameters or in the body")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createLimit(@QueryParam("command") final String commandParam,
            @QueryParam("asOfDate") final String asOfDate, @QueryParam("currency") final String currency,
            @QueryParam("officeId") final Long officeId, @QueryParam("runId") final Long runId,
            @Parameter(hidden = true) final String body) {
        if (commandParam == null) {
            return command(TREASURY_LIMIT_RESOURCE_NAME, "CREATE", null, "/treasury/limits", body);
        }
        if (!COMMAND_EVALUATE.equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { COMMAND_EVALUATE });
        }
        final JsonObject json = StringUtils.isBlank(body) ? new JsonObject() : object(body);
        put(json, "asOfDate", asOfDate);
        put(json, "currencyCode", currency);
        if (officeId != null) {
            json.addProperty("officeId", officeId);
        }
        if (runId != null) {
            json.addProperty("runId", runId);
        }
        return command(TREASURY_LIMIT_RESOURCE_NAME, "EVALUATE", null, "/treasury/limits?command=evaluate", json.toString());
    }

    @PUT
    @Path("limits/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Change a limit", description = "Any field but code, laid over the limit as it is (null clears one); a new version")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateLimit(@PathParam("id") final Long id, @Parameter(hidden = true) final String body) {
        return command(TREASURY_LIMIT_RESOURCE_NAME, "UPDATE", id, "/treasury/limits/" + id, body);
    }

    @POST
    @Path("limits/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Activate or deactivate a limit", description = "command=activate|deactivate; optional note")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnLimit(@PathParam("id") final Long id, @QueryParam("command") final String commandParam,
            @Parameter(hidden = true) final String body) {
        if (COMMAND_ACTIVATE.equalsIgnoreCase(commandParam)) {
            return command(TREASURY_LIMIT_RESOURCE_NAME, "ACTIVATE", id, "/treasury/limits/" + id + "?command=activate", body);
        }
        if (COMMAND_DEACTIVATE.equalsIgnoreCase(commandParam)) {
            return command(TREASURY_LIMIT_RESOURCE_NAME, "DEACTIVATE", id, "/treasury/limits/" + id + "?command=deactivate", body);
        }
        throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { COMMAND_ACTIVATE, COMMAND_DEACTIVATE });
    }

    @GET
    @Path("limit-evaluations")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List limit evaluations", description = "Filters: currency, officeId, from, to (as-of dates); the latest 500")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryLimitEvaluationData.class))))
    public List<TreasuryLimitEvaluationData> retrieveEvaluations(@QueryParam("currency") final String currency,
            @QueryParam("officeId") final Long officeId, @QueryParam("from") final String from, @QueryParam("to") final String to) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_LIMIT_RESOURCE_NAME);
        return this.limitService.retrieveEvaluations(StringUtils.trimToNull(currency), officeId, date("from", from), date("to", to));
    }

    @GET
    @Path("limit-evaluations/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "A limit evaluation with every observation")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryLimitEvaluationData.class)))
    public TreasuryLimitEvaluationData retrieveEvaluation(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_LIMIT_RESOURCE_NAME);
        return this.limitService.retrieveEvaluation(id);
    }

    // ---------- breaches ----------

    @GET
    @Path("breaches")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "The breach log", description = "Filters: status (OPEN, ACKNOWLEDGED, RESOLVED), metric, limitId, level (WARNING, HARD), currency, scopeType, scopeId, from (last seen on or after), to (first seen on or before); the latest 500")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryBreachData.class))))
    public List<TreasuryBreachData> retrieveBreaches(@QueryParam("status") final String status, @QueryParam("metric") final String metric,
            @QueryParam("limitId") final Long limitId, @QueryParam("level") final String level,
            @QueryParam("currency") final String currency, @QueryParam("scopeType") final String scopeType,
            @QueryParam("scopeId") final Long scopeId, @QueryParam("from") final String from, @QueryParam("to") final String to) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_BREACH_RESOURCE_NAME);
        return this.breachService.retrieveAll(status, metric, limitId, level, currency, scopeType, scopeId, date("from", from),
                date("to", to));
    }

    @GET
    @Path("breaches/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "A breach with its comments")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryBreachData.class)))
    public TreasuryBreachData retrieveBreach(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_BREACH_RESOURCE_NAME);
        return this.breachService.retrieveOne(id);
    }

    @POST
    @Path("breaches/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Acknowledge or resolve a breach", description = "command=acknowledge (optional comment) or resolve (comment required)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnBreach(@PathParam("id") final Long id, @QueryParam("command") final String commandParam,
            @Parameter(hidden = true) final String body) {
        if (COMMAND_ACKNOWLEDGE.equalsIgnoreCase(commandParam)) {
            return command(TREASURY_BREACH_RESOURCE_NAME, "ACKNOWLEDGE", id, "/treasury/breaches/" + id + "?command=acknowledge", body);
        }
        if (COMMAND_RESOLVE.equalsIgnoreCase(commandParam)) {
            return command(TREASURY_BREACH_RESOURCE_NAME, "RESOLVE", id, "/treasury/breaches/" + id + "?command=resolve", body);
        }
        throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { COMMAND_ACKNOWLEDGE, COMMAND_RESOLVE });
    }

    @POST
    @Path("breaches/{id}/comments")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Comment on a breach", description = "comment")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult commentOnBreach(@PathParam("id") final Long id, @Parameter(hidden = true) final String body) {
        return command(TREASURY_BREACH_RESOURCE_NAME, "COMMENT", id, "/treasury/breaches/" + id + "/comments", body);
    }

    // ---------- helpers ----------

    private CommandProcessingResult command(final String entity, final String action, final Long entityId, final String href,
            final String json) {
        return this.commandsSourceWritePlatformService.logCommandSource(new MicropayCommandWrapperBuilder()
                .treasuryCommand(entity, action, entityId, href).withJson(StringUtils.isBlank(json) ? "{}" : json).build());
    }

    private static JsonObject object(final String body) {
        final JsonElement element = JsonParser.parseString(body);
        if (!element.isJsonObject()) {
            throw new InvalidJsonException();
        }
        return element.getAsJsonObject();
    }

    private static void put(final JsonObject json, final String name, final String value) {
        if (StringUtils.isNotBlank(value)) {
            json.addProperty(name, value.trim());
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
