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
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.TREASURY_DEAL_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.TREASURY_ECL_PARAMETER_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryHoldingApiConstants.TREASURY_PRICE_RESOURCE_NAME;

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
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.treasury.data.TreasuryEclParameterData;
import org.apache.fineract.treasury.data.TreasuryPortfolioData;
import org.apache.fineract.treasury.data.TreasuryPriceData;
import org.apache.fineract.treasury.service.TreasuryEclService;
import org.apache.fineract.treasury.service.TreasuryPortfolioService;
import org.apache.fineract.treasury.service.TreasuryPriceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/treasury")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Treasury Holdings", description = "Market prices, expected credit loss parameters and the holdings portfolio")
@RequiredArgsConstructor
public class TreasuryHoldingApiResource {

    private final PlatformSecurityContext context;
    private final TreasuryPriceService priceService;
    private final TreasuryEclService eclService;
    private final TreasuryPortfolioService portfolioService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Path("prices")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Latest price of an instrument", description = "instrumentId (ISIN, or the deal's external reference); asOf (yyyy-MM-dd, default the business date)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryPriceData.class)))
    public TreasuryPriceData retrieveLatestPrice(@QueryParam("instrumentId") final String instrumentId,
            @QueryParam("asOf") final String asOf) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_PRICE_RESOURCE_NAME);
        return this.priceService.retrieveLatest(required("instrumentId", instrumentId), date("asOf", asOf));
    }

    @GET
    @Path("prices/history")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Prices of an instrument between two dates", description = "instrumentId, from and to (yyyy-MM-dd) are required")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryPriceData.class))))
    public List<TreasuryPriceData> retrievePriceHistory(@QueryParam("instrumentId") final String instrumentId,
            @QueryParam("from") final String from, @QueryParam("to") final String to) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_PRICE_RESOURCE_NAME);
        final LocalDate fromDate = date("from", required("from", from));
        final LocalDate toDate = date("to", required("to", to));
        return this.priceService.retrieveHistory(required("instrumentId", instrumentId), fromDate, toDate);
    }

    @POST
    @Path("prices")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Enter prices", description = "One price (instrumentId, priceDate, cleanPrice and/or yield, optional source MANUAL, BOU_AUCTION, BOU_SECONDARY, OTHER) or several under prices; a price for an instrument and date already priced replaces it")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createPrices(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return command(TREASURY_PRICE_RESOURCE_NAME, "CREATE", "/treasury/prices", apiRequestBodyAsJson);
    }

    @GET
    @Path("ecl-parameters")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Expected credit loss parameters by rating and stage")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryEclParameterData.class))))
    public List<TreasuryEclParameterData> retrieveEclParameters() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_ECL_PARAMETER_RESOURCE_NAME);
        return this.eclService.retrieveParameters();
    }

    @PUT
    @Path("ecl-parameters")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Set the parameters of a rating and stage", description = "ratingCode (a counterparty rating, or DEFAULT), stage (1-3), pd12Month, pdLifetime, lgd (percent)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateEclParameter(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return command(TREASURY_ECL_PARAMETER_RESOURCE_NAME, "UPDATE", "/treasury/ecl-parameters", apiRequestBodyAsJson);
    }

    @GET
    @Path("portfolio")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Holdings portfolio", description = "Settled asset deals held on date (yyyy-MM-dd, default the business date), optionally in one currency: amortised cost, fair value, OCI reserve, ECL stage and allowance, pledged and unencumbered nominal, with totals per currency")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryPortfolioData.class)))
    public TreasuryPortfolioData retrievePortfolio(@QueryParam("date") final String date, @QueryParam("currency") final String currency) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_DEAL_RESOURCE_NAME);
        return this.portfolioService.portfolio(date("date", date), currency);
    }

    private CommandProcessingResult command(final String entity, final String action, final String href, final String json) {
        return this.commandsSourceWritePlatformService.logCommandSource(new MicropayCommandWrapperBuilder()
                .treasuryCommand(entity, action, null, href).withJson(StringUtils.isBlank(json) ? "{}" : json).build());
    }

    private static String required(final String name, final String value) {
        if (StringUtils.isBlank(value)) {
            throw new PlatformApiDataValidationException("validation.msg.treasury.query." + name + ".required",
                    "The query parameter `" + name + "` is required", name);
        }
        return value.trim();
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
