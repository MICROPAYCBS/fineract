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

import static org.apache.fineract.treasury.api.TreasuryApiConstants.CLOSING_BALANCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.COMMAND_AUTOMATCH;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.COMMAND_EXPLAIN;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.CONTENT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.FILE_NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.FORMAT_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MAX_STATEMENT_FILE_BYTES;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.OPENING_BALANCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_RECON_MATCH_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_STATEMENT_FORMAT_RESOURCE_NAME;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_STATEMENT_RESOURCE_NAME;

import com.google.gson.JsonObject;
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
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.treasury.data.TreasuryReconMatchData;
import org.apache.fineract.treasury.data.TreasuryReconciliationData;
import org.apache.fineract.treasury.data.TreasuryStatementData;
import org.apache.fineract.treasury.data.TreasuryStatementFormatData;
import org.apache.fineract.treasury.data.TreasuryStatementLineData;
import org.apache.fineract.treasury.data.TreasuryUnmatchedData;
import org.apache.fineract.treasury.service.TreasuryReconciliationReadService;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/treasury")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Treasury Reconciliation", description = "Bank and wallet statement import, automatic and manual matching against the GL, and explanations")
@RequiredArgsConstructor
public class TreasuryReconciliationApiResource {

    private final PlatformSecurityContext context;
    private final TreasuryReconciliationReadService readService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    // ---------- formats ----------

    @GET
    @Path("statement-formats")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List statement import formats")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryStatementFormatData.class))))
    public List<TreasuryStatementFormatData> retrieveFormats() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_STATEMENT_FORMAT_RESOURCE_NAME);
        return this.readService.retrieveFormats();
    }

    @POST
    @Path("statement-formats")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a statement import format", description = "code, name, formatType CSV or MT940. CSV: delimiter, headerRows, dateFormat, decimalSeparator, dateColumn, and amountColumn or debitColumn and creditColumn; optional valueDateColumn, descriptionColumn, referenceColumn, balanceColumn, invertAmount. Columns are 1-based numbers or header names.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createFormat(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService.logCommandSource(
                new MicropayCommandWrapperBuilder().createTreasuryStatementFormat().withJson(apiRequestBodyAsJson).build());
    }

    @PUT
    @Path("statement-formats/{formatId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a statement import format", description = "Any field but code and formatType; active=false retires it")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult updateFormat(@PathParam("formatId") final Long formatId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService.logCommandSource(
                new MicropayCommandWrapperBuilder().updateTreasuryStatementFormat(formatId).withJson(apiRequestBodyAsJson).build());
    }

    // ---------- statements ----------

    @POST
    @Path("statements")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Import a statement (JSON)", description = "treasuryAccountId, formatCode, content (the file, base64), optional fileName; openingBalance and closingBalance when the file has none")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult importStatement(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService
                .logCommandSource(new MicropayCommandWrapperBuilder().importTreasuryStatement().withJson(apiRequestBodyAsJson).build());
    }

    @POST
    @Path("statements")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Import a statement (file upload)", description = "Form fields file, treasuryAccountId, formatCode; openingBalance and closingBalance when the file has none")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult uploadStatement(@FormDataParam("file") final InputStream file,
            @FormDataParam("file") final FormDataContentDisposition fileDetail,
            @FormDataParam(TREASURY_ACCOUNT_ID_PARAM) final Long accountId, @FormDataParam(FORMAT_CODE_PARAM) final String formatCode,
            @FormDataParam(OPENING_BALANCE_PARAM) final String openingBalance,
            @FormDataParam(CLOSING_BALANCE_PARAM) final String closingBalance) {
        final JsonObject json = new JsonObject();
        if (accountId != null) {
            json.addProperty(TREASURY_ACCOUNT_ID_PARAM, accountId);
        }
        json.addProperty(FORMAT_CODE_PARAM, formatCode);
        if (fileDetail != null && fileDetail.getFileName() != null) {
            json.addProperty(FILE_NAME_PARAM, StringUtils.abbreviate(fileDetail.getFileName(), 255));
        }
        json.addProperty(CONTENT_PARAM, Base64.getEncoder().encodeToString(read(file)));
        if (StringUtils.isNotBlank(openingBalance)) {
            json.addProperty(OPENING_BALANCE_PARAM, number(OPENING_BALANCE_PARAM, openingBalance));
        }
        if (StringUtils.isNotBlank(closingBalance)) {
            json.addProperty(CLOSING_BALANCE_PARAM, number(CLOSING_BALANCE_PARAM, closingBalance));
        }
        return importStatement(json.toString());
    }

    @GET
    @Path("statements")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List statements", description = "Filters: accountId, status (IMPORTED, RECONCILED)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryStatementData.class))))
    public List<TreasuryStatementData> retrieveStatements(@QueryParam("accountId") final Long accountId,
            @QueryParam("status") final String status) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_STATEMENT_RESOURCE_NAME);
        return this.readService.retrieveStatements(accountId, status);
    }

    @GET
    @Path("statements/{statementId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a statement")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryStatementData.class)))
    public TreasuryStatementData retrieveStatement(@PathParam("statementId") final Long statementId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_STATEMENT_RESOURCE_NAME);
        return this.readService.retrieveStatement(statementId);
    }

    @GET
    @Path("statements/{statementId}/lines")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Lines of a statement", description = "Filter: matchStatus (UNMATCHED, MATCHED, EXPLAINED). Amounts are positive for money into the account.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryStatementLineData.class))))
    public List<TreasuryStatementLineData> retrieveLines(@PathParam("statementId") final Long statementId,
            @QueryParam("matchStatus") final String matchStatus) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_STATEMENT_RESOURCE_NAME);
        return this.readService.retrieveLines(statementId, matchStatus);
    }

    @POST
    @Path("statements/{statementId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Match a statement automatically", description = "command=automatch: one-to-one by reference, then same date, then the date window, with the currency's rules")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnStatement(@PathParam("statementId") final Long statementId,
            @QueryParam("command") @Parameter(description = "automatch", required = true) final String commandParam) {
        if (!COMMAND_AUTOMATCH.equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { COMMAND_AUTOMATCH });
        }
        return this.commandsSourceWritePlatformService
                .logCommandSource(new MicropayCommandWrapperBuilder().automatchTreasuryStatement(statementId).withJson("{}").build());
    }

    @POST
    @Path("statements/lines/{lineId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Explain a statement line", description = "command=explain with reasonCode (BANK_CHARGE, INTEREST, TIMING, BANK_ERROR, OTHER or a code value id), optional note; with glAccountId an adjusting entry is posted against the treasury account's GL account, dated the line's date unless transactionDate is given")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult actOnLine(@PathParam("lineId") final Long lineId,
            @QueryParam("command") @Parameter(description = "explain", required = true) final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        if (!COMMAND_EXPLAIN.equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { COMMAND_EXPLAIN });
        }
        return this.commandsSourceWritePlatformService.logCommandSource(
                new MicropayCommandWrapperBuilder().explainTreasuryStatementLine(lineId).withJson(apiRequestBodyAsJson).build());
    }

    // ---------- matches ----------

    @GET
    @Path("reconciliation/matches")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List reconciliation matches", description = "Filters: accountId, status (ACTIVE, UNMATCHED)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryReconMatchData.class))))
    public List<TreasuryReconMatchData> retrieveMatches(@QueryParam("accountId") final Long accountId,
            @QueryParam("status") final String status) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_RECON_MATCH_RESOURCE_NAME);
        return this.readService.retrieveMatches(accountId, status);
    }

    @GET
    @Path("reconciliation/matches/{matchId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a reconciliation match")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryReconMatchData.class)))
    public TreasuryReconMatchData retrieveMatch(@PathParam("matchId") final Long matchId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_RECON_MATCH_RESOURCE_NAME);
        return this.readService.retrieveMatch(matchId);
    }

    @POST
    @Path("reconciliation/matches")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Match statement lines and journal entries by hand", description = "lineIds and journalEntryIds (any number of each, one treasury account) whose totals are equal; optional note")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult createMatch(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService
                .logCommandSource(new MicropayCommandWrapperBuilder().createTreasuryReconMatch().withJson(apiRequestBodyAsJson).build());
    }

    @DELETE
    @Path("reconciliation/matches/{matchId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Unmatch", description = "Body {\"reason\": \"...\"}. Lines go back to unmatched; an explanation's adjusting entry is reversed.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult unmatch(@PathParam("matchId") final Long matchId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        return this.commandsSourceWritePlatformService
                .logCommandSource(new MicropayCommandWrapperBuilder().unmatchTreasuryReconMatch(matchId)
                        .withJson(StringUtils.isBlank(apiRequestBodyAsJson) ? "{}" : apiRequestBodyAsJson).build());
    }

    // ---------- reports ----------

    @GET
    @Path("reconciliation/unmatched")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Unmatched statement lines and GL entries", description = "accountId (default all active accounts), ageBucket 0-7, 8-30, 31-90 or 90+ days")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryUnmatchedData.class)))
    public TreasuryUnmatchedData retrieveUnmatched(@QueryParam("accountId") final Long accountId,
            @QueryParam("ageBucket") final String ageBucket) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_RECON_MATCH_RESOURCE_NAME);
        return this.readService.retrieveUnmatched(accountId, ageBucket);
    }

    @GET
    @Path("accounts/{accountId}/reconciliation")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Reconciliation state of a treasury account", description = "Last reconciled date, that statement's closing balance against the GL balance on the date, and what is still unmatched")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryReconciliationData.class)))
    public TreasuryReconciliationData retrieveAccountReconciliation(@PathParam("accountId") final Long accountId) {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_RECON_MATCH_RESOURCE_NAME);
        return this.readService.retrieveAccountReconciliation(accountId);
    }

    private static byte[] read(final InputStream file) {
        if (file == null) {
            throw new PlatformApiDataValidationException("validation.msg.treasury.statement.file.missing",
                    "The form field `file` is required", "file");
        }
        try (InputStream in = file) {
            final byte[] content = in.readNBytes(MAX_STATEMENT_FILE_BYTES + 1);
            if (content.length > MAX_STATEMENT_FILE_BYTES) {
                throw new PlatformApiDataValidationException("validation.msg.treasury.statement.file.too.large",
                        "The statement file is larger than " + MAX_STATEMENT_FILE_BYTES + " bytes", "file", MAX_STATEMENT_FILE_BYTES);
            }
            return content;
        } catch (IOException e) {
            throw new PlatformApiDataValidationException("validation.msg.treasury.statement.file.unreadable",
                    "The statement file could not be read", "file", e);
        }
    }

    private static java.math.BigDecimal number(final String name, final String value) {
        try {
            return new java.math.BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new PlatformApiDataValidationException("validation.msg.treasury.statement." + name + ".not.a.number",
                    "The form field `" + name + "` must be a number", name, e, value);
        }
    }
}
