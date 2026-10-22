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

import static org.apache.fineract.treasury.api.TreasuryApiConstants.COMMAND_CLOSE;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_ACCOUNT_RESOURCE_NAME;

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
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.treasury.data.TreasuryAccountData;
import org.apache.fineract.treasury.data.TreasuryAccountRequest;
import org.apache.fineract.treasury.data.TreasuryPositionData;
import org.apache.fineract.treasury.data.TreasuryPositionSummaryData;
import org.apache.fineract.treasury.service.TreasuryAccountReadPlatformService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/treasury")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Treasury Accounts", description = "Treasury accounts (bank, central bank, mobile wallet, vault, agent float pool) and their cash positions")
@RequiredArgsConstructor
public class TreasuryAccountApiResource {

    private final PlatformSecurityContext context;
    private final TreasuryAccountReadPlatformService readPlatformService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Path("accounts")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List treasury accounts", description = "Optionally filtered by office, currency, account type and status")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryAccountData.class))))
    public List<TreasuryAccountData> retrieveAccounts(@QueryParam("officeId") final Long officeId,
            @QueryParam("currency") final String currencyCode, @QueryParam("type") final String accountType,
            @QueryParam("status") final String status) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_ACCOUNT_RESOURCE_NAME);
        return this.readPlatformService.retrieveAccounts(officeId, currencyCode, accountType, status);
    }

    @GET
    @Path("accounts/{accountId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a treasury account")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryAccountData.class)))
    public TreasuryAccountData retrieveAccount(@PathParam("accountId") final Long accountId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_ACCOUNT_RESOURCE_NAME);
        return this.readPlatformService.retrieveAccount(accountId);
    }

    @POST
    @Path("accounts")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a treasury account", description = "The GL account must be an asset detail account and the currency an organisation currency")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = TreasuryAccountRequest.class)))
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createAccount(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService
                .logCommandSource(new MicropayCommandWrapperBuilder().createTreasuryAccount().withJson(apiRequestBodyAsJson).build());
    }

    @PUT
    @Path("accounts/{accountId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a treasury account", description = "Only the fields sent are changed; send null to clear an optional field. The code cannot be changed.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = TreasuryAccountRequest.class)))
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateAccount(@PathParam("accountId") final Long accountId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService.logCommandSource(
                new MicropayCommandWrapperBuilder().updateTreasuryAccount(accountId).withJson(apiRequestBodyAsJson).build());
    }

    @POST
    @Path("accounts/{accountId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Close a treasury account", description = "command=close. A closed account leaves the positions and the day-end snapshot.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnAccount(@PathParam("accountId") final Long accountId,
            @QueryParam("command") @Parameter(description = "close", required = true) final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        if (!COMMAND_CLOSE.equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { COMMAND_CLOSE });
        }
        return this.commandsSourceWritePlatformService
                .logCommandSource(new MicropayCommandWrapperBuilder().closeTreasuryAccount(accountId).build());
    }

    @GET
    @Path("accounts/{accountId}/position")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Live position of a treasury account", description = "GL balance of the account's GL account in its office and currency, its minimum and maximum, the level status and the latest day-end snapshot")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryPositionData.class)))
    public TreasuryPositionData retrievePosition(@PathParam("accountId") final Long accountId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_ACCOUNT_RESOURCE_NAME);
        return this.readPlatformService.retrievePosition(accountId);
    }

    @GET
    @Path("positions")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Live positions by office and currency", description = "Totals of the active treasury accounts per office and currency, with each account's position")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryPositionSummaryData.class))))
    public List<TreasuryPositionSummaryData> retrievePositions(@QueryParam("officeId") final Long officeId,
            @QueryParam("currency") final String currencyCode) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_ACCOUNT_RESOURCE_NAME);
        return this.readPlatformService.retrievePositions(officeId, currencyCode);
    }
}
