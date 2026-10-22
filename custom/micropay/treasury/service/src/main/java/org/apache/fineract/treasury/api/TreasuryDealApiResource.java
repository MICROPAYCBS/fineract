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

import static org.apache.fineract.treasury.api.TreasuryApiConstants.COMMAND_APPROVE;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.COMMAND_CANCEL;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.COMMAND_CLOSE;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.COMMAND_CONFIRM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.COMMAND_REJECT;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.COMMAND_SETTLE;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.TREASURY_COUNTERPARTY_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.TREASURY_DEAL_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.TREASURY_DEAL_TYPE_RESOURCE_NAME;

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
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.treasury.data.TreasuryCounterpartyData;
import org.apache.fineract.treasury.data.TreasuryDealData;
import org.apache.fineract.treasury.data.TreasuryDealTypeData;
import org.apache.fineract.treasury.data.TreasuryExposureData;
import org.apache.fineract.treasury.data.TreasuryMaturityData;
import org.apache.fineract.treasury.service.TreasuryCounterpartyService;
import org.apache.fineract.treasury.service.TreasuryDealReadService;
import org.apache.fineract.treasury.service.TreasuryDealReadService.DealFilter;
import org.apache.fineract.treasury.service.TreasuryDealTypeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/treasury")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Treasury Deals", description = "Counterparties and their limits, deal types, and money market, fixed deposit, T-bill, bond and borrowing deals")
@RequiredArgsConstructor
public class TreasuryDealApiResource {

    private final PlatformSecurityContext context;
    private final TreasuryCounterpartyService counterpartyService;
    private final TreasuryDealTypeService dealTypeService;
    private final TreasuryDealReadService dealReadService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    // ---------- counterparties ----------

    @GET
    @Path("counterparties")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List counterparties")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryCounterpartyData.class))))
    public List<TreasuryCounterpartyData> retrieveCounterparties() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_COUNTERPARTY_RESOURCE_NAME);
        return this.counterpartyService.retrieveAll();
    }

    @GET
    @Path("counterparties/{counterpartyId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a counterparty")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryCounterpartyData.class)))
    public TreasuryCounterpartyData retrieveCounterparty(@PathParam("counterpartyId") final Long counterpartyId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_COUNTERPARTY_RESOURCE_NAME);
        return this.counterpartyService.retrieveOne(counterpartyId);
    }

    @GET
    @Path("counterparties/{counterpartyId}/exposure")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Exposure of a counterparty against its limit", description = "Asset deals approved, confirmed or settled: principal, or face value for bills and bonds")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryExposureData.class)))
    public TreasuryExposureData retrieveExposure(@PathParam("counterpartyId") final Long counterpartyId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_COUNTERPARTY_RESOURCE_NAME);
        return this.counterpartyService.exposure(counterpartyId);
    }

    @POST
    @Path("counterparties")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a counterparty", description = "name, shortCode, counterpartyType; optional rating, ratingCodeValueId, exposureLimit with limitCurrencyCode, warningPercent (default 80)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createCounterparty(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return command(TREASURY_COUNTERPARTY_RESOURCE_NAME, "CREATE", null, "/treasury/counterparties", apiRequestBodyAsJson);
    }

    @PUT
    @Path("counterparties/{counterpartyId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a counterparty", description = "Any field but shortCode")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateCounterparty(@PathParam("counterpartyId") final Long counterpartyId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return command(TREASURY_COUNTERPARTY_RESOURCE_NAME, "UPDATE", counterpartyId, "/treasury/counterparties/" + counterpartyId,
                apiRequestBodyAsJson);
    }

    @POST
    @Path("counterparties/{counterpartyId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Close a counterparty", description = "command=close; refused while it has open deals")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnCounterparty(@PathParam("counterpartyId") final Long counterpartyId,
            @QueryParam("command") @Parameter(description = "close", required = true) final String commandParam) {
        if (!COMMAND_CLOSE.equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { COMMAND_CLOSE });
        }
        return command(TREASURY_COUNTERPARTY_RESOURCE_NAME, "CLOSE", counterpartyId,
                "/treasury/counterparties/" + counterpartyId + "?command=close", "{}");
    }

    // ---------- deal types ----------

    @GET
    @Path("deal-types")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List deal types")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryDealTypeData.class))))
    public List<TreasuryDealTypeData> retrieveDealTypes() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_DEAL_TYPE_RESOURCE_NAME);
        return this.dealTypeService.retrieveAll();
    }

    @GET
    @Path("deal-types/{dealTypeId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a deal type")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryDealTypeData.class)))
    public TreasuryDealTypeData retrieveDealType(@PathParam("dealTypeId") final Long dealTypeId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_DEAL_TYPE_RESOURCE_NAME);
        return this.dealTypeService.retrieveOne(dealTypeId);
    }

    @POST
    @Path("deal-types")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a deal type", description = "code, name, instrumentKind; optional defaultDayCount (ACT_365), defaultFrequency, and the GL accounts the kind needs")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createDealType(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return command(TREASURY_DEAL_TYPE_RESOURCE_NAME, "CREATE", null, "/treasury/deal-types", apiRequestBodyAsJson);
    }

    @PUT
    @Path("deal-types/{dealTypeId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a deal type", description = "Any field but code and instrumentKind; active=false retires it")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateDealType(@PathParam("dealTypeId") final Long dealTypeId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return command(TREASURY_DEAL_TYPE_RESOURCE_NAME, "UPDATE", dealTypeId, "/treasury/deal-types/" + dealTypeId, apiRequestBodyAsJson);
    }

    // ---------- deals ----------

    @GET
    @Path("deals")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List deals", description = "Filters: status, dealTypeId, counterpartyId, maturityFrom, maturityTo (yyyy-MM-dd)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryDealData.class))))
    public List<TreasuryDealData> retrieveDeals(@QueryParam("status") final String status, @QueryParam("dealTypeId") final Long dealTypeId,
            @QueryParam("counterpartyId") final Long counterpartyId, @QueryParam("maturityFrom") final String maturityFrom,
            @QueryParam("maturityTo") final String maturityTo) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_DEAL_RESOURCE_NAME);
        return this.dealReadService.retrieveDeals(
                new DealFilter(status, dealTypeId, counterpartyId, date("maturityFrom", maturityFrom), date("maturityTo", maturityTo)));
    }

    @GET
    @Path("deals/maturity-calendar")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Cash flows falling due", description = "from and to (yyyy-MM-dd) are required; deals neither rejected nor cancelled")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryMaturityData.class))))
    public List<TreasuryMaturityData> retrieveMaturityCalendar(@QueryParam("from") final String from, @QueryParam("to") final String to) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_DEAL_RESOURCE_NAME);
        final LocalDate fromDate = date("from", from);
        final LocalDate toDate = date("to", to);
        if (fromDate == null || toDate == null) {
            throw new PlatformApiDataValidationException("validation.msg.treasury.maturity.calendar.dates.required",
                    "The query parameters `from` and `to` are required", "from");
        }
        return this.dealReadService.retrieveMaturityCalendar(fromDate, toDate);
    }

    @GET
    @Path("deals/{dealId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a deal with its cash flows and history")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryDealData.class)))
    public TreasuryDealData retrieveDeal(@PathParam("dealId") final Long dealId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_DEAL_RESOURCE_NAME);
        return this.dealReadService.retrieveDeal(dealId);
    }

    @POST
    @Path("deals")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Capture a deal", description = "dealTypeId, counterpartyId, settlementAccountId, tradeDate, valueDate, maturityDate, amount (principal or face), rate (rate, yield or coupon, %); bonds also cleanPrice and optionally issueDate; optional dayCount, frequency, amortising, fees, classification, externalReference, isin, notes")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult captureDeal(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return command(TREASURY_DEAL_RESOURCE_NAME, "CREATE", null, "/treasury/deals", apiRequestBodyAsJson);
    }

    @PUT
    @Path("deals/{dealId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Edit a captured deal", description = "Only while CAPTURED; cash flows are generated again")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateDeal(@PathParam("dealId") final Long dealId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return command(TREASURY_DEAL_RESOURCE_NAME, "UPDATE", dealId, "/treasury/deals/" + dealId, apiRequestBodyAsJson);
    }

    @POST
    @Path("deals/{dealId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Approve, reject, confirm, cancel or settle a deal", description = "command=approve|reject|confirm|cancel|settle. Body: reason (reject, cancel), confirmationReference (confirm), note.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnDeal(@PathParam("dealId") final Long dealId,
            @QueryParam("command") @Parameter(description = "approve, reject, confirm, cancel or settle", required = true) final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final List<String> commands = List.of(COMMAND_APPROVE, COMMAND_REJECT, COMMAND_CONFIRM, COMMAND_CANCEL, COMMAND_SETTLE);
        if (commandParam == null || !commands.contains(commandParam.toLowerCase(Locale.ROOT))) {
            throw new UnrecognizedQueryParamException("command", commandParam, commands.toArray());
        }
        return command(TREASURY_DEAL_RESOURCE_NAME, commandParam.toUpperCase(Locale.ROOT), dealId,
                "/treasury/deals/" + dealId + "?command=" + commandParam.toLowerCase(Locale.ROOT), apiRequestBodyAsJson);
    }

    private CommandProcessingResult command(final String entity, final String action, final Long entityId, final String href,
            final String json) {
        return this.commandsSourceWritePlatformService.logCommandSource(new MicropayCommandWrapperBuilder()
                .treasuryCommand(entity, action, entityId, href).withJson(StringUtils.isBlank(json) ? "{}" : json).build());
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
