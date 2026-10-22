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
import static org.apache.fineract.treasury.api.TreasuryApiConstants.COMMAND_RECEIVE;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.COMMAND_REVERSE;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.COMMAND_SEND;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_SETTING_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_TRANSFER_RESOURCE_NAME;

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
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.treasury.data.TreasurySettingData;
import org.apache.fineract.treasury.data.TreasuryTransferData;
import org.apache.fineract.treasury.service.TreasurySettingService;
import org.apache.fineract.treasury.service.TreasuryTransferReadPlatformService;
import org.apache.fineract.treasury.service.TreasuryTransferReadPlatformService.TransferFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/treasury")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Treasury Transfers", description = "Funding transfers between treasury accounts, with an in-transit state, and treasury settings")
@RequiredArgsConstructor
public class TreasuryTransferApiResource {

    private final PlatformSecurityContext context;
    private final TreasuryTransferReadPlatformService readPlatformService;
    private final TreasurySettingService settingService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Path("transfers")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List transfers", description = "Filters: status, accountId (either side), officeId (either side), fromDate and toDate on the transfer date (yyyy-MM-dd)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryTransferData.class))))
    public List<TreasuryTransferData> retrieveTransfers(@QueryParam("status") final String status,
            @QueryParam("accountId") final Long accountId, @QueryParam("officeId") final Long officeId,
            @QueryParam("fromDate") final String fromDate, @QueryParam("toDate") final String toDate) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_TRANSFER_RESOURCE_NAME);
        return this.readPlatformService
                .retrieveTransfers(new TransferFilter(status, accountId, officeId, date("fromDate", fromDate), date("toDate", toDate)));
    }

    @GET
    @Path("transfers/in-transit")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Transfers in transit", description = "Sent and not yet received, oldest first, with days in transit")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryTransferData.class))))
    public List<TreasuryTransferData> retrieveInTransit(@QueryParam("officeId") final Long officeId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_TRANSFER_RESOURCE_NAME);
        return this.readPlatformService.retrieveInTransit(officeId);
    }

    @GET
    @Path("transfers/{transferId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a transfer")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryTransferData.class)))
    public TreasuryTransferData retrieveTransfer(@PathParam("transferId") final Long transferId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_TRANSFER_RESOURCE_NAME);
        return this.readPlatformService.retrieveTransfer(transferId);
    }

    @POST
    @Path("transfers")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Request a transfer", description = "fromAccountId, toAccountId, amount; optional transferDate, reference, description. Both accounts must be active and in the same currency.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createTransfer(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService
                .logCommandSource(new MicropayCommandWrapperBuilder().createTreasuryTransfer().withJson(apiRequestBodyAsJson).build());
    }

    @POST
    @Path("transfers/{transferId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Approve, send, receive or cancel a transfer", description = "command=approve|send|receive|cancel|reverse; optional body {\"transactionDate\": \"2026-10-16\", \"note\": \"...\"}. Send posts the source to cash in transit and receive posts cash in transit to the destination. Reverse (a sent or received transfer, with a reason) reverses those postings.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnTransfer(@PathParam("transferId") final Long transferId,
            @QueryParam("command") @Parameter(description = "approve, send, receive, cancel or reverse", required = true) final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final MicropayCommandWrapperBuilder builder = new MicropayCommandWrapperBuilder();
        if (COMMAND_APPROVE.equalsIgnoreCase(commandParam)) {
            builder.approveTreasuryTransfer(transferId);
        } else if (COMMAND_SEND.equalsIgnoreCase(commandParam)) {
            builder.sendTreasuryTransfer(transferId);
        } else if (COMMAND_RECEIVE.equalsIgnoreCase(commandParam)) {
            builder.receiveTreasuryTransfer(transferId);
        } else if (COMMAND_CANCEL.equalsIgnoreCase(commandParam)) {
            builder.cancelTreasuryTransfer(transferId);
        } else if (COMMAND_REVERSE.equalsIgnoreCase(commandParam)) {
            builder.reverseTreasuryTransfer(transferId);
        } else {
            throw new UnrecognizedQueryParamException("command", commandParam,
                    new Object[] { COMMAND_APPROVE, COMMAND_SEND, COMMAND_RECEIVE, COMMAND_CANCEL, COMMAND_REVERSE });
        }
        final CommandWrapper wrapper = builder.withJson(body(apiRequestBodyAsJson)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(wrapper);
    }

    @GET
    @Path("settings")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Treasury settings per currency")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasurySettingData.class))))
    public List<TreasurySettingData> retrieveSettings() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_SETTING_RESOURCE_NAME);
        return this.settingService.retrieveSettings();
    }

    @PUT
    @Path("settings")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update the settings of a currency", description = "currencyCode plus any of cashInTransitGlAccountId, floatPaymentTypeId, allowInsufficientBalance; only the fields sent change")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateSettings(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService
                .logCommandSource(new MicropayCommandWrapperBuilder().updateTreasurySettings().withJson(apiRequestBodyAsJson).build());
    }

    static String body(final String json) {
        return json == null || json.isBlank() ? "{}" : json;
    }

    private static LocalDate date(final String name, final String value) {
        if (value == null || value.isBlank()) {
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
