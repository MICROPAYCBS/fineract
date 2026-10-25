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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.treasury.data.TreasuryTrustData;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.service.TreasuryTrustService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/treasury/trust")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Treasury e-money trust", description = "E-money trust safeguarding (NPS Act 2020, NPS Regulations 2021): trust accounts and holdings, e-money, the daily reconciliation, the asset breakdown, trust interest")
@RequiredArgsConstructor
public class TreasuryTrustApiResource {

    public static final String TREASURY_TRUST_RESOURCE_NAME = "TREASURYTRUST";

    private final PlatformSecurityContext context;
    private final TreasuryTrustService trustService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Path("accounts")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "TRUST accounts", description = "With the institution holding each, its trustee and its GL balance on the business date")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryTrustData.Account.class))))
    public List<TreasuryTrustData.Account> accounts() {
        read();
        return this.trustService.accounts();
    }

    @PUT
    @Path("accounts/{accountId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Name a TRUST account's institution and trustee", description = "bankCounterpartyId, trusteeName, trustDeedReference; maker-checker capable")
    public CommandProcessingResult updateAccount(@PathParam("accountId") final Long accountId,
            @Parameter(hidden = true) final String body) {
        return command("TREASURYTRUSTACCOUNT", "UPDATE", accountId, "/treasury/trust/accounts/" + accountId, body);
    }

    @GET
    @Path("holdings")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Deals held in trust")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryTrustData.Holding.class))))
    public List<TreasuryTrustData.Holding> holdings() {
        read();
        return this.trustService.holdings();
    }

    @POST
    @Path("holdings")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Hold a deal in trust", description = "dealId (a bill, bond or placement), trustAccountId (a TRUST account of its currency); maker-checker capable")
    public CommandProcessingResult createHolding(@Parameter(hidden = true) final String body) {
        return command("TREASURYTRUSTHOLDING", "CREATE", null, "/treasury/trust/holdings", body);
    }

    @DELETE
    @Path("holdings/{dealId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Release a deal from the trust", description = "maker-checker capable")
    public CommandProcessingResult deleteHolding(@PathParam("dealId") final Long dealId) {
        return command("TREASURYTRUSTHOLDING", "DELETE", dealId, "/treasury/trust/holdings/" + dealId, null);
    }

    @GET
    @Path("emoney-balances")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Imported e-money balances", description = "from, to (default the last 30 days), currency")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryTrustData.EMoneyBalance.class))))
    public List<TreasuryTrustData.EMoneyBalance> emoneyBalances(@QueryParam("from") final String from, @QueryParam("to") final String to,
            @QueryParam("currency") final String currency) {
        read();
        return this.trustService.emoneyBalances(date("from", from), date("to", to), currency);
    }

    @POST
    @Path("emoney-balances")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Enter or import the e-money balances of a date", description = "balanceDate, currencyCode, customers, agents, other, note; the IMPORTED source; maker-checker capable")
    public CommandProcessingResult importEMoney(@Parameter(hidden = true) final String body) {
        return command("TREASURYEMONEYBALANCE", "CREATE", null, "/treasury/trust/emoney-balances", body);
    }

    @POST
    @Path("reconciliations")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Reconcile e-money against the trust", description = "asOfDate (default the closed day), currencyCode (default the base); replaces the date's earlier reconciliation unless submitted; evaluates the trust limits; maker-checker capable")
    public CommandProcessingResult reconcile(@Parameter(hidden = true) final String body) {
        return command("TREASURYTRUSTRECON", "CREATE", null, "/treasury/trust/reconciliations", body);
    }

    @GET
    @Path("reconciliations")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Trust reconciliations", description = "from, to (default the last 30 days), currency (default the base): each date's institution-wide reconciliation with its status and headline figures, newest first")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryTrustData.ReconciliationSummary.class))))
    public List<TreasuryTrustData.ReconciliationSummary> reconciliations(@QueryParam("from") final String from,
            @QueryParam("to") final String to, @QueryParam("currency") final String currency) {
        read();
        return this.trustService.reconciliations(date("from", from), date("to", to), currency);
    }

    @GET
    @Path("reconciliations/{runId}")
    @Produces({ MediaType.APPLICATION_JSON, "text/csv" })
    @Operation(summary = "The daily reconciliation statement", description = "format=csv for CSV (NPS Regulations 2021 reg 22(5))")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryTrustData.Statement.class)))
    public Response statement(@PathParam("runId") final Long runId, @QueryParam("format") final String format) {
        read();
        final TreasuryTrustData.Statement statement = this.trustService.statement(runId);
        if ("csv".equalsIgnoreCase(StringUtils.trimToEmpty(format))) {
            return Response.ok(TreasuryTrustService.csv(statement), "text/csv")
                    .header("Content-Disposition", "attachment; filename=\"trust-reconciliation-" + statement.asOfDate() + ".csv\"")
                    .build();
        }
        return Response.ok(statement, MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("reconciliations/{runId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Submit a reconciliation statement", description = "command=submit; locks the date's reconciliation; maker-checker capable")
    public CommandProcessingResult submit(@PathParam("runId") final Long runId, @QueryParam("command") final String commandParam,
            @Parameter(hidden = true) final String body) {
        if (!"submit".equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { "submit" });
        }
        return command("TREASURYTRUSTRECON", "SUBMIT", runId, "/treasury/trust/reconciliations/" + runId + "?command=submit", body);
    }

    @GET
    @Path("asset-breakdown")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Trust balances by asset type and institution", description = "asOfDate (default the closed day), currency; from the date's reconciliation (reg 22(6))")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryTrustData.Breakdown.class)))
    public TreasuryTrustData.Breakdown breakdown(@QueryParam("asOfDate") final String asOfDate,
            @QueryParam("currency") final String currency) {
        read();
        return this.trustService.breakdown(date("asOfDate", asOfDate), currency);
    }

    @GET
    @Path("interest")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Interest earned on the trust", description = "from, to (default the calendar quarter of the business date), currency: accrued, by source, the average trust balance and the annual rate (reg 14(2), 14(7))")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryTrustData.Interest.class)))
    public TreasuryTrustData.Interest interest(@QueryParam("from") final String from, @QueryParam("to") final String to,
            @QueryParam("currency") final String currency) {
        read();
        return this.trustService.interest(date("from", from), date("to", to), currency);
    }

    private void read() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_TRUST_RESOURCE_NAME);
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
        } catch (RuntimeException e) {
            throw new TreasuryWorkflowException("trust", "invalid.date", "The query parameter " + name + " must be yyyy-MM-dd", name, value,
                    e);
        }
    }
}
