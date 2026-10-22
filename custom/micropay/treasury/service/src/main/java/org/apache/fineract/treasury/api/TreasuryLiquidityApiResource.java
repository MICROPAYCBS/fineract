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
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.COMMAND_ACTIVATE;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.COMMAND_SUBMIT;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.TREASURY_ASSUMPTION_SET_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.TREASURY_LADDER_ITEM_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.TREASURY_RULE_SET_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.TREASURY_RUN_RESOURCE_NAME;

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
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
import org.apache.fineract.treasury.data.TreasuryAssumptionSetData;
import org.apache.fineract.treasury.data.TreasuryLadderItemData;
import org.apache.fineract.treasury.data.TreasuryReturnData;
import org.apache.fineract.treasury.data.TreasuryRuleSetData;
import org.apache.fineract.treasury.data.TreasuryRunData;
import org.apache.fineract.treasury.service.TreasuryAssumptionSetService;
import org.apache.fineract.treasury.service.TreasuryLadderItemService;
import org.apache.fineract.treasury.service.TreasuryLiquidityRunService;
import org.apache.fineract.treasury.service.TreasuryRuleSetService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/treasury")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Treasury Liquidity", description = "Rule sets and ladder buckets, behavioural assumptions, ladder items and liquidity runs")
@RequiredArgsConstructor
public class TreasuryLiquidityApiResource {

    private final PlatformSecurityContext context;
    private final TreasuryRuleSetService ruleSetService;
    private final TreasuryAssumptionSetService assumptionSetService;
    private final TreasuryLadderItemService ladderItemService;
    private final TreasuryLiquidityRunService runService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    // ---------- rule sets ----------

    @GET
    @Path("rule-sets")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List rule sets with their buckets, every version")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryRuleSetData.class))))
    public List<TreasuryRuleSetData> retrieveRuleSets() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_RULE_SET_RESOURCE_NAME);
        return this.ruleSetService.retrieveAll();
    }

    @GET
    @Path("rule-sets/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a rule set version")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryRuleSetData.class)))
    public TreasuryRuleSetData retrieveRuleSet(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_RULE_SET_RESOURCE_NAME);
        return this.ruleSetService.retrieveOne(id);
    }

    @POST
    @Path("rule-sets")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a rule set version (DRAFT)", description = "code, name, effectiveDate, buckets [{code, label, fromDay, toDay}], optional description; an existing code gets its next version")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createRuleSet(@Parameter(hidden = true) final String body) {
        return command(TREASURY_RULE_SET_RESOURCE_NAME, "CREATE", null, "/treasury/rule-sets", body);
    }

    @PUT
    @Path("rule-sets/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Edit a draft rule set", description = "name, effectiveDate, description, buckets (replaced); on an active version only {\"isDefault\": true}")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateRuleSet(@PathParam("id") final Long id, @Parameter(hidden = true) final String body) {
        return command(TREASURY_RULE_SET_RESOURCE_NAME, "UPDATE", id, "/treasury/rule-sets/" + id, body);
    }

    @DELETE
    @Path("rule-sets/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete a draft rule set")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult deleteRuleSet(@PathParam("id") final Long id) {
        return command(TREASURY_RULE_SET_RESOURCE_NAME, "DELETE", id, "/treasury/rule-sets/" + id, "{}");
    }

    @POST
    @Path("rule-sets/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Activate a draft rule set", description = "command=activate; supersedes the code's active version")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnRuleSet(@PathParam("id") final Long id, @QueryParam("command") final String commandParam,
            @Parameter(hidden = true) final String body) {
        activateOnly(commandParam);
        return command(TREASURY_RULE_SET_RESOURCE_NAME, "ACTIVATE", id, "/treasury/rule-sets/" + id + "?command=activate", body);
    }

    // ---------- assumption sets ----------

    @GET
    @Path("assumptions")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List assumption sets with their assumptions, every version")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryAssumptionSetData.class))))
    public List<TreasuryAssumptionSetData> retrieveAssumptionSets() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_ASSUMPTION_SET_RESOURCE_NAME);
        return this.assumptionSetService.retrieveAll();
    }

    @GET
    @Path("assumptions/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve an assumption set version")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryAssumptionSetData.class)))
    public TreasuryAssumptionSetData retrieveAssumptionSet(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_ASSUMPTION_SET_RESOURCE_NAME);
        return this.assumptionSetService.retrieveOne(id);
    }

    @POST
    @Path("assumptions")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create an assumption set version (DRAFT)", description = "code, name, effectiveDate, assumptions [{type, source, productId, bucketCode, minDays, maxDays, rate}], optional description")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createAssumptionSet(@Parameter(hidden = true) final String body) {
        return command(TREASURY_ASSUMPTION_SET_RESOURCE_NAME, "CREATE", null, "/treasury/assumptions", body);
    }

    @PUT
    @Path("assumptions/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Edit a draft assumption set", description = "name, effectiveDate, description, assumptions (replaced)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateAssumptionSet(@PathParam("id") final Long id, @Parameter(hidden = true) final String body) {
        return command(TREASURY_ASSUMPTION_SET_RESOURCE_NAME, "UPDATE", id, "/treasury/assumptions/" + id, body);
    }

    @DELETE
    @Path("assumptions/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete a draft assumption set")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult deleteAssumptionSet(@PathParam("id") final Long id) {
        return command(TREASURY_ASSUMPTION_SET_RESOURCE_NAME, "DELETE", id, "/treasury/assumptions/" + id, "{}");
    }

    @POST
    @Path("assumptions/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Activate a draft assumption set", description = "command=activate; supersedes the code's active version")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnAssumptionSet(@PathParam("id") final Long id, @QueryParam("command") final String commandParam,
            @Parameter(hidden = true) final String body) {
        activateOnly(commandParam);
        return command(TREASURY_ASSUMPTION_SET_RESOURCE_NAME, "ACTIVATE", id, "/treasury/assumptions/" + id + "?command=activate", body);
    }

    // ---------- ladder items ----------

    @GET
    @Path("ladder-items")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List ladder items")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryLadderItemData.class))))
    public List<TreasuryLadderItemData> retrieveLadderItems() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_LADDER_ITEM_RESOURCE_NAME);
        return this.ladderItemService.retrieveAll();
    }

    @GET
    @Path("ladder-items/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a ladder item")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryLadderItemData.class)))
    public TreasuryLadderItemData retrieveLadderItem(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_LADDER_ITEM_RESOURCE_NAME);
        return this.ladderItemService.retrieveOne(id);
    }

    @POST
    @Path("ladder-items")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a ladder item", description = "name, direction (INFLOW, OUTFLOW), amount, currencyCode, oneOffDate or dayOfMonth, activeFrom, optional activeTo and officeId")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createLadderItem(@Parameter(hidden = true) final String body) {
        return command(TREASURY_LADDER_ITEM_RESOURCE_NAME, "CREATE", null, "/treasury/ladder-items", body);
    }

    @PUT
    @Path("ladder-items/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Edit a ladder item")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateLadderItem(@PathParam("id") final Long id, @Parameter(hidden = true) final String body) {
        return command(TREASURY_LADDER_ITEM_RESOURCE_NAME, "UPDATE", id, "/treasury/ladder-items/" + id, body);
    }

    @DELETE
    @Path("ladder-items/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete a ladder item")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult deleteLadderItem(@PathParam("id") final Long id) {
        return command(TREASURY_LADDER_ITEM_RESOURCE_NAME, "DELETE", id, "/treasury/ladder-items/" + id, "{}");
    }

    @GET
    @Path("rule-sets/{id}/ratios")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "The ratios of a rule set version with their components")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryRuleSetData.RatioData.class))))
    public List<TreasuryRuleSetData.RatioData> retrieveRatios(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_RULE_SET_RESOURCE_NAME);
        return this.ruleSetService.retrieveOne(id).ratios();
    }

    @PUT
    @Path("rule-sets/{id}/ratios")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Replace the ratios of a draft rule set", description = "{\"ratios\": [{code, name, direction (MIN, MAX), limit, warning, inReturn, numeratorLabel, denominatorLabel, components: [{part, type (GL_ACCOUNT, ASSET_CLASS, LADDER_METRIC), label, glAccountId | glCodePrefix | glCodeFrom and glCodeTo, assetClass, ladderMetric, metricDays, sign, weight}]}]}")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateRatios(@PathParam("id") final Long id, @Parameter(hidden = true) final String body) {
        return command(TREASURY_RULE_SET_RESOURCE_NAME, "UPDATE", id, "/treasury/rule-sets/" + id + "/ratios", body);
    }

    @GET
    @Path("rule-sets/{id}/asset-classes")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "The asset classes of a rule set version")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryRuleSetData.AssetClassData.class))))
    public List<TreasuryRuleSetData.AssetClassData> retrieveAssetClasses(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_RULE_SET_RESOURCE_NAME);
        return this.ruleSetService.retrieveOne(id).assetClasses();
    }

    @PUT
    @Path("rule-sets/{id}/asset-classes")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Replace the asset classes of a draft rule set", description = "{\"assetClasses\": [{code, label, liquid, accountTypes, instrumentKinds, dealTypeIds, maxMaturityDays, haircut}]}")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateAssetClasses(@PathParam("id") final Long id, @Parameter(hidden = true) final String body) {
        return command(TREASURY_RULE_SET_RESOURCE_NAME, "UPDATE", id, "/treasury/rule-sets/" + id + "/asset-classes", body);
    }

    // ---------- runs ----------

    @POST
    @Path("runs")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Run the ladder, the ratios or the return", description = "type=ladder|ratios|return, asOfDate (default the business date), currency, optional officeId, ruleSetId (default rule set), assumptionSetId (active set), sourceRunId (ratios: the ladder run; return: the ratios run; by default the scope's, built when missing); as query parameters or in the body. A rerun for the same type, date, currency and office replaces the earlier run and what was computed from it, unless a return of the scope has been submitted")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createRun(@QueryParam("type") final String type, @QueryParam("asOfDate") final String asOfDate,
            @QueryParam("currency") final String currency, @QueryParam("officeId") final Long officeId,
            @QueryParam("ruleSetId") final Long ruleSetId, @QueryParam("assumptionSetId") final Long assumptionSetId,
            @QueryParam("sourceRunId") final Long sourceRunId, @Parameter(hidden = true) final String body) {
        final JsonObject json = StringUtils.isBlank(body) ? new JsonObject() : object(body);
        put(json, "type", type);
        put(json, "asOfDate", asOfDate);
        put(json, "currencyCode", currency);
        if (officeId != null) {
            json.addProperty("officeId", officeId);
        }
        if (ruleSetId != null) {
            json.addProperty("ruleSetId", ruleSetId);
        }
        if (assumptionSetId != null) {
            json.addProperty("assumptionSetId", assumptionSetId);
        }
        if (sourceRunId != null) {
            json.addProperty("sourceRunId", sourceRunId);
        }
        return command(TREASURY_RUN_RESOURCE_NAME, "CREATE", null, "/treasury/runs", json.toString());
    }

    @GET
    @Path("runs")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List runs", description = "Filters: type, currency, officeId, from, to (as-of dates, yyyy-MM-dd)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryRunData.class))))
    public List<TreasuryRunData> retrieveRuns(@QueryParam("type") final String type, @QueryParam("currency") final String currency,
            @QueryParam("officeId") final Long officeId, @QueryParam("from") final String from, @QueryParam("to") final String to) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_RUN_RESOURCE_NAME);
        return this.runService.retrieveRuns(StringUtils.trimToNull(type), StringUtils.trimToNull(currency), officeId, date("from", from),
                date("to", to));
    }

    @GET
    @Path("runs/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "A run with its bucket totals (net and cumulative gap)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryRunData.class)))
    public TreasuryRunData retrieveRun(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_RUN_RESOURCE_NAME);
        return this.runService.retrieveRun(id);
    }

    @GET
    @Path("runs/{id}/lines")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Every line of a run: per bucket, source, line and product, and the bucket totals")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryRunData.Line.class))))
    public List<TreasuryRunData.Line> retrieveRunLines(@PathParam("id") final Long id) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_RUN_RESOURCE_NAME);
        return this.runService.retrieveLines(id);
    }

    @POST
    @Path("runs/{id}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Submit a return", description = "command=submit; the return and the runs of its scope are then locked")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnRun(@PathParam("id") final Long id, @QueryParam("command") final String commandParam,
            @Parameter(hidden = true) final String body) {
        if (!COMMAND_SUBMIT.equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { COMMAND_SUBMIT });
        }
        return command(TREASURY_RUN_RESOURCE_NAME, "SUBMIT", id, "/treasury/runs/" + id + "?command=submit", body);
    }

    @GET
    @Path("runs/{id}/return")
    @Produces({ MediaType.APPLICATION_JSON, "text/csv" })
    @Operation(summary = "A stored regulatory return", description = "format=json (default) or csv; the layout is provisional (MICROPAY Q7). Read from the stored run, so it is the same every time")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryReturnData.class)))
    public Response retrieveReturn(@PathParam("id") final Long id, @QueryParam("format") final String format) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_RUN_RESOURCE_NAME);
        if ("csv".equalsIgnoreCase(StringUtils.trimToEmpty(format))) {
            return Response.ok(this.runService.retrieveReturnCsv(id), "text/csv")
                    .header("Content-Disposition", "attachment; filename=\"treasury-return-" + id + ".csv\"").build();
        }
        return Response.ok(this.runService.retrieveReturn(id), MediaType.APPLICATION_JSON).build();
    }

    private CommandProcessingResult command(final String entity, final String action, final Long entityId, final String href,
            final String json) {
        return this.commandsSourceWritePlatformService.logCommandSource(new MicropayCommandWrapperBuilder()
                .treasuryCommand(entity, action, entityId, href).withJson(StringUtils.isBlank(json) ? "{}" : json).build());
    }

    private static void activateOnly(final String commandParam) {
        if (!COMMAND_ACTIVATE.equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { COMMAND_ACTIVATE });
        }
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
