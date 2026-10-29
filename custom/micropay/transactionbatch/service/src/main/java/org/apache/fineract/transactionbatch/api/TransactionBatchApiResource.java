/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.api;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.COMMAND_COPY;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.COMMAND_REPAIR;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.COMMAND_SUBMIT;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import com.micropay.fineract.platform.command.MicropayCommandWrapperBuilder;
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
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.transactionbatch.data.TransactionBatchAccountData;
import org.apache.fineract.transactionbatch.data.TransactionBatchImportResult;
import org.apache.fineract.transactionbatch.data.TransactionBatchData;
import org.apache.fineract.transactionbatch.data.TransactionBatchItemData;
import org.apache.fineract.transactionbatch.data.TransactionBatchPageData;
import org.apache.fineract.transactionbatch.data.TransactionBatchSettingsData;
import org.apache.fineract.transactionbatch.data.TransactionBatchSummaryData;
import org.apache.fineract.transactionbatch.data.TransactionBatchTemplateData;
import org.apache.fineract.transactionbatch.service.TransactionBatchImportService;
import org.apache.fineract.transactionbatch.service.TransactionBatchReadPlatformService;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/transactionbatches")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Transaction batches", description = "Branch batch posting of GL and customer lines")
@RequiredArgsConstructor
public class TransactionBatchApiResource {

    private final PortfolioCommandSourceWritePlatformService commands;
    private final TransactionBatchReadPlatformService reads;
    private final TransactionBatchImportService imports;

    @GET
    @Path("template")
    @Produces({ MediaType.APPLICATION_JSON })
    public TransactionBatchTemplateData template() {
        return this.reads.template();
    }

    @GET
    @Path("settings")
    @Produces({ MediaType.APPLICATION_JSON })
    public TransactionBatchSettingsData settings() {
        return this.reads.settings();
    }

    @PUT
    @Path("settings")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult updateSettings(final String body) {
        return this.commands.logCommandSource(new MicropayCommandWrapperBuilder().updateTransactionBatchSettings().withJson(json(body)).build());
    }

    @GET
    @Path("accounts")
    @Produces({ MediaType.APPLICATION_JSON })
    public List<TransactionBatchAccountData> accounts(@QueryParam("search") final String search) {
        return this.reads.accounts(search);
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public TransactionBatchPageData search(@QueryParam("officeId") final Long officeId, @QueryParam("createdBy") final Long createdBy,
            @QueryParam("batchNumber") final Long batchNumber, @QueryParam("code") final String code,
            @QueryParam("fromDate") final String fromDate, @QueryParam("toDate") final String toDate,
            @QueryParam("status") final String status, @QueryParam("offset") final Integer offset, @QueryParam("limit") final Integer limit,
            @QueryParam("orderBy") final String orderBy, @QueryParam("sortOrder") final String sortOrder) {
        return this.reads.search(officeId, createdBy, batchNumber, code, date("fromDate", fromDate), date("toDate", toDate), status, offset,
                limit, orderBy, sortOrder);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult create(final String body) {
        return this.commands.logCommandSource(new MicropayCommandWrapperBuilder().createTransactionBatch().withJson(json(body)).build());
    }

    @GET
    @Path("{batchId}")
    @Produces({ MediaType.APPLICATION_JSON })
    public TransactionBatchData retrieve(@PathParam("batchId") final Long batchId, @QueryParam("associations") final String associations) {
        return this.reads.retrieveOne(batchId, associations);
    }

    @PUT
    @Path("{batchId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult update(@PathParam("batchId") final Long batchId, final String body) {
        return this.commands
                .logCommandSource(new MicropayCommandWrapperBuilder().updateTransactionBatch(batchId).withJson(json(body)).build());
    }

    @DELETE
    @Path("{batchId}")
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult delete(@PathParam("batchId") final Long batchId) {
        return this.commands.logCommandSource(new MicropayCommandWrapperBuilder().deleteTransactionBatch(batchId).withJson("{}").build());
    }

    @POST
    @Path("{batchId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult command(@PathParam("batchId") final Long batchId, @QueryParam("command") final String command,
            final String body) {
        final MicropayCommandWrapperBuilder builder = new MicropayCommandWrapperBuilder();
        if (COMMAND_COPY.equalsIgnoreCase(command)) {
            return this.commands.logCommandSource(builder.copyTransactionBatch(batchId).withJson(json(body)).build());
        }
        if (COMMAND_SUBMIT.equalsIgnoreCase(command)) {
            return this.commands.logCommandSource(builder.submitTransactionBatch(batchId).withJson(json(body)).build());
        }
        throw new UnrecognizedQueryParamException("command", command, new Object[] { COMMAND_COPY, COMMAND_SUBMIT });
    }

    @GET
    @Path("{batchId}/summary")
    @Produces({ MediaType.APPLICATION_JSON })
    public List<TransactionBatchSummaryData> summary(@PathParam("batchId") final Long batchId) {
        return this.reads.summary(batchId);
    }

    @GET
    @Path("{batchId}/items")
    @Produces({ MediaType.APPLICATION_JSON })
    public List<TransactionBatchItemData> items(@PathParam("batchId") final Long batchId) {
        return this.reads.items(batchId);
    }

    @POST
    @Path("{batchId}/items/import")
    @Consumes({ MediaType.MULTIPART_FORM_DATA })
    @Produces({ MediaType.APPLICATION_JSON })
    public TransactionBatchImportResult importItems(@PathParam("batchId") final Long batchId,
            @FormDataParam("file") final InputStream stream, @FormDataParam("file") final FormDataContentDisposition file) {
        final String name = file == null ? null : file.getFileName();
        return this.imports.importFile(batchId, name, stream);
    }

    @POST
    @Path("{batchId}/items/{itemId}/postings/{postingId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult repair(@PathParam("batchId") final Long batchId, @PathParam("itemId") final Long itemId,
            @PathParam("postingId") final Long postingId, @QueryParam("command") final String command, final String body) {
        if (!COMMAND_REPAIR.equalsIgnoreCase(command)) {
            throw new UnrecognizedQueryParamException("command", command, COMMAND_REPAIR);
        }
        return this.commands.logCommandSource(new MicropayCommandWrapperBuilder().repairTransactionBatchItem(batchId, itemId, postingId)
                .withJson(json(body)).build());
    }

    @POST
    @Path("{batchId}/items")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult addItem(@PathParam("batchId") final Long batchId, final String body) {
        return this.commands
                .logCommandSource(new MicropayCommandWrapperBuilder().createTransactionBatchItem(batchId).withJson(json(body)).build());
    }

    @PUT
    @Path("{batchId}/items/{itemId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult updateItem(@PathParam("batchId") final Long batchId, @PathParam("itemId") final Long itemId,
            final String body) {
        return this.commands.logCommandSource(
                new MicropayCommandWrapperBuilder().updateTransactionBatchItem(batchId, itemId).withJson(json(body)).build());
    }

    @DELETE
    @Path("{batchId}/items/{itemId}")
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult deleteItem(@PathParam("batchId") final Long batchId, @PathParam("itemId") final Long itemId) {
        return this.commands
                .logCommandSource(new MicropayCommandWrapperBuilder().deleteTransactionBatchItem(batchId, itemId).withJson("{}").build());
    }

    private static String json(final String body) {
        return StringUtils.isBlank(body) ? "{}" : body;
    }

    private static LocalDate date(final String name, final String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (final DateTimeParseException ex) {
            throw new PlatformApiDataValidationException("validation.msg.transactionbatch." + name + ".invalid",
                    name + " must be an ISO date", name, value);
        }
    }
}
