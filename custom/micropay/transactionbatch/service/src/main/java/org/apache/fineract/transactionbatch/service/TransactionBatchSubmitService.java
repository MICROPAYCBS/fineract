/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.APPROVAL_NOT_CONFIGURED;
import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.transactionbatch.domain.TransactionBatch;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItem;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItemRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchSide;
import org.apache.fineract.transactionbatch.domain.TransactionBatchStatus;
import org.apache.fineract.transactionbatch.exception.TransactionBatchRuleException;
import org.apache.fineract.transactionbatch.service.TransactionBatchSummaryCalculator.Line;
import org.apache.fineract.transactionbatch.service.TransactionBatchSummaryCalculator.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchSubmitService {

    private final TransactionBatchAccess access;
    private final TransactionBatchItemRepository itemRepository;
    private final TransactionBatchAccountRules accountRules;
    private final ConfigurationDomainService configurationDomainService;
    private final CommandSourceRepository commandSourceRepository;

    @Transactional
    public CommandProcessingResult submit(final JsonCommand command) {
        final TransactionBatch batch = this.access.requireBatch(command.entityId());
        this.access.assertEditable(batch);
        validateContents(batch);
        if (command.commandId() == null) {
            final boolean makerChecker = this.configurationDomainService.isMakerCheckerEnabledForTask("SUBMIT_TRANSACTIONBATCH");
            if (!makerChecker || this.access.user().isCheckerSuperUser()) {
                throw new TransactionBatchRuleException("approval.not.configured", APPROVAL_NOT_CONFIGURED);
            }
            return new CommandProcessingResultBuilder().withEntityId(batch.getId()).withOfficeId(batch.getOfficeId()).build();
        }
        final CommandSource held = this.commandSourceRepository.findById(command.commandId())
                .orElseThrow(() -> new TransactionBatchRuleException("command.missing", "The submit command was not found"));
        if (held.getMaker() != null && held.getMaker().getId().equals(this.access.user().getId())) {
            throw new TransactionBatchRuleException("maker.is.approver", "The maker of a batch cannot approve it");
        }
        batch.setStatus(TransactionBatchStatus.ACTIVE.getValue());
        batch.setSubmittedBy(held.getMaker() == null ? null : held.getMaker().getId());
        batch.setSubmittedOn(DateUtils.getBusinessLocalDate());
        batch.setDecidedBy(this.access.user().getId());
        batch.setDecidedOn(DateUtils.getBusinessLocalDate());
        this.access.touch(batch);
        return new CommandProcessingResultBuilder().withEntityId(batch.getId()).withOfficeId(batch.getOfficeId()).build();
    }

    public void validateContents(final TransactionBatch batch) {
        final List<TransactionBatchItem> items = this.itemRepository.findByBatch_IdOrderByIdAsc(batch.getId());
        if (items.isEmpty()) {
            throw new TransactionBatchRuleException("empty", "A batch needs at least one line");
        }
        final List<Line> lines = new ArrayList<>();
        for (final TransactionBatchItem item : items) {
            if (item.accountType().isGl()) {
                this.accountRules.requirePostableGl(item.getGlAccountId());
                this.accountRules.requireOrganisationCurrency(item.getCurrencyCode());
            } else {
                this.accountRules.requireSavings(item.getSavingsAccountId(), item.side(), item.getCurrencyCode());
            }
            lines.add(new Line(item.getCurrencyCode(), item.getValueDate(), item.side() == TransactionBatchSide.DEBIT, item.getAmount()));
        }
        final List<Row> rows = TransactionBatchSummaryCalculator.summarise(lines);
        if (!TransactionBatchSummaryCalculator.isBalanced(rows)) {
            throw new TransactionBatchRuleException("unbalanced",
                    "Every currency and value date must have equal debits and credits before the batch can be submitted");
        }
    }
}
