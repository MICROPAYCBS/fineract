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
package org.apache.fineract.organisation.monetary.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.organisation.monetary.data.CurrencyLegalTenderData;
import org.apache.fineract.organisation.monetary.data.CurrencyLegalTenderRequest;
import org.apache.fineract.organisation.monetary.service.LegalTenderReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/currencies/{currencyCode}/legal-tenders")
@Component
@Tag(name = "Legal Tender", description = "Notes and coins for a currency. Listing an unknown currency returns an empty list.")
@RequiredArgsConstructor
public class LegalTenderApiResource {

    private static final String WRITE_OK = "Command accepted. When maker-checker holds the command, the body is "
            + "{ commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.";

    private final LegalTenderReadPlatformService readPlatformService;
    private final ToApiJsonSerializer<CurrencyLegalTenderRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List legal tenders for currency", operationId = "retrieveLegalTenders", description = "Returns tenders for the currency. includeInactive defaults to false (active only). An unknown currency returns an empty list.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CurrencyLegalTenderData.class))))
    @ApiResponse(responseCode = "403", description = "The user lacks READ_LEGAL_TENDER permission.")
    public List<CurrencyLegalTenderData> retrieveAll(
            @PathParam("currencyCode") @Parameter(description = "ISO currency code", example = "UGX") final String currencyCode,
            @QueryParam("includeInactive") @Parameter(description = "When true, include inactive tenders. Default false.", example = "false") final Boolean includeInactive) {
        return this.readPlatformService.retrieveAll(currencyCode, Boolean.TRUE.equals(includeInactive));
    }

    @GET
    @Path("{legalTenderId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve legal tender", operationId = "retrieveLegalTender", description = "Returns one note or coin.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CurrencyLegalTenderData.class)))
    @ApiResponse(responseCode = "403", description = "The user lacks READ_LEGAL_TENDER permission.")
    @ApiResponse(responseCode = "404", description = "No legal tender with this id (error.msg.legal.tender.not.found).")
    public CurrencyLegalTenderData retrieveOne(
            @PathParam("currencyCode") @Parameter(description = "ISO currency code", example = "UGX") final String currencyCode,
            @PathParam("legalTenderId") @Parameter(description = "Legal tender id", example = "1") final Long legalTenderId) {
        return this.readPlatformService.retrieveOne(currencyCode, legalTenderId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create legal tender", operationId = "createLegalTender", description = "Creates a note or coin. value, tenderType (NOTE or COIN), label and displayOrder are required. active is a boolean.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = CurrencyLegalTenderRequest.class)))
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "A field fails validation.")
    @ApiResponse(responseCode = "403", description = "The user lacks CREATE_LEGAL_TENDER permission, or the same value and type already exist (error.msg.legal.tender.duplicate).")
    @ApiResponse(responseCode = "404", description = "The currency does not exist.")
    public CommandProcessingResult create(
            @PathParam("currencyCode") @Parameter(description = "ISO currency code", example = "UGX") final String currencyCode,
            final CurrencyLegalTenderRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createLegalTender(currencyCode)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("{legalTenderId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update legal tender", operationId = "updateLegalTender", description = "Updates the fields sent.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = CurrencyLegalTenderRequest.class)))
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "A field fails validation.")
    @ApiResponse(responseCode = "403", description = "The user lacks UPDATE_LEGAL_TENDER permission, or the same value and type already exist (error.msg.legal.tender.duplicate).")
    @ApiResponse(responseCode = "404", description = "No legal tender with this id (error.msg.legal.tender.not.found).")
    public CommandProcessingResult update(
            @PathParam("currencyCode") @Parameter(description = "ISO currency code", example = "UGX") final String currencyCode,
            @PathParam("legalTenderId") @Parameter(description = "Legal tender id", example = "1") final Long legalTenderId,
            final CurrencyLegalTenderRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateLegalTender(currencyCode, legalTenderId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("{legalTenderId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete legal tender", operationId = "deleteLegalTender", description = "Deletes a tender that cashier transactions do not use.")
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "403", description = "The user lacks DELETE_LEGAL_TENDER permission, or the tender is used by cashier transactions (error.msg.legal.tender.in.use).")
    @ApiResponse(responseCode = "404", description = "No legal tender with this id (error.msg.legal.tender.not.found).")
    public CommandProcessingResult delete(
            @PathParam("currencyCode") @Parameter(description = "ISO currency code", example = "UGX") final String currencyCode,
            @PathParam("legalTenderId") @Parameter(description = "Legal tender id", example = "1") final Long legalTenderId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteLegalTender(currencyCode, legalTenderId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
