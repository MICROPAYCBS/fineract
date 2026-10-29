/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.micropay.fineract.infrastructure.interbranch.MicropayCrossBranchTransactionAccessService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.apache.fineract.transactionbatch.domain.TransactionBatch;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Customer line through glToSavings or savingsToGl. The GL is clearing on a normal post and suspense on a repair. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchCustomerPoster {

    private final SavingsAccountWritePlatformService savingsAccountWritePlatformService;
    private final MicropayCrossBranchTransactionAccessService crossBranch;
    private final OfficeRepositoryWrapper officeRepository;
    private final FromJsonHelper jsonHelper;

    public Long post(final TransactionBatch batch, final TransactionBatchItem item, final Long glAccountId, final LocalDate date) {
        final Office origin = this.officeRepository.findOneWithNotFoundDetection(batch.getOfficeId());
        final JsonCommand command = command(item, glAccountId, date);
        final CommandProcessingResult result = this.crossBranch.withServicingOffice(origin, () -> item.isDebit()
                ? this.savingsAccountWritePlatformService.savingsToGl(item.getSavingsAccountId(), command)
                : this.savingsAccountWritePlatformService.glToSavings(item.getSavingsAccountId(), command));
        return result.getResourceId();
    }

    private JsonCommand command(final TransactionBatchItem item, final Long glAccountId, final LocalDate date) {
        final JsonObject body = new JsonObject();
        body.addProperty("locale", "en");
        body.addProperty("dateFormat", "yyyy-MM-dd");
        body.addProperty("transactionDate", date.toString());
        body.addProperty("transactionAmount", item.getAmount());
        body.addProperty("glAccountId", glAccountId);
        body.addProperty("note", item.getNarrative());
        body.addProperty("externalId", item.getReference());
        final String json = body.toString();
        final JsonElement parsed = this.jsonHelper.parse(json);
        return JsonCommand.from(json, parsed, this.jsonHelper, null, null, null, null, null, null, item.getSavingsAccountId(), null, null,
                null, null, null, null, null);
    }
}
