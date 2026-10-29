/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.transactionbatch.domain.TransactionBatch;
import org.apache.fineract.transactionbatch.domain.TransactionBatchAccountType;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItem;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItemRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItemStatus;
import org.apache.fineract.transactionbatch.domain.TransactionBatchRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchSide;
import org.apache.fineract.transactionbatch.domain.TransactionBatchStatus;
import org.apache.fineract.transactionbatch.exception.TransactionBatchRuleException;
import org.apache.fineract.transactionbatch.service.TransactionBatchValidator.Header;
import org.apache.fineract.transactionbatch.service.TransactionBatchValidator.ItemFields;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchWritePlatformService {

    private final TransactionBatchAccess access;
    private final TransactionBatchRepository batchRepository;
    private final TransactionBatchItemRepository itemRepository;
    private final TransactionBatchValidator validator;
    private final TransactionBatchAccountRules accountRules;
    private final TransactionBatchReferenceGenerator references;

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final Header header = this.validator.parseHeader(command.json());
        final TransactionBatch batch = new TransactionBatch();
        apply(batch, header);
        batch.setOfficeId(this.access.user().getOffice().getId());
        batch.setStatus(TransactionBatchStatus.INACTIVE.getValue());
        batch.setCreatedBy(this.access.user().getId());
        batch.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.access.touch(batch);
        this.batchRepository.saveAndFlush(batch);
        batch.setCode(this.access.config().getCodePrefix() + batch.getId());
        this.batchRepository.save(batch);
        return new CommandProcessingResultBuilder().withEntityId(batch.getId()).withOfficeId(batch.getOfficeId()).build();
    }

    @Transactional
    public CommandProcessingResult update(final JsonCommand command) {
        final TransactionBatch batch = this.access.requireBatch(command.entityId());
        this.access.assertEditable(batch);
        apply(batch, this.validator.parseHeader(command.json()));
        this.access.touch(batch);
        return new CommandProcessingResultBuilder().withEntityId(batch.getId()).withOfficeId(batch.getOfficeId()).build();
    }

    @Transactional
    public CommandProcessingResult delete(final JsonCommand command) {
        final TransactionBatch batch = this.access.requireBatch(command.entityId());
        this.access.assertEditable(batch);
        this.itemRepository.deleteByBatch_Id(batch.getId());
        this.batchRepository.delete(batch);
        return new CommandProcessingResultBuilder().withEntityId(batch.getId()).build();
    }

    @Transactional
    public CommandProcessingResult copy(final JsonCommand command) {
        final TransactionBatch source = this.access.requireBatch(command.entityId());
        final TransactionBatch copy = new TransactionBatch();
        copy.setDescription(source.getDescription());
        copy.setOfficeId(source.getOfficeId());
        copy.setRecurring(source.isRecurring());
        copy.setEffectiveDate(source.getEffectiveDate());
        copy.setFrequencyNumber(source.getFrequencyNumber());
        copy.setFrequencyUnit(source.getFrequencyUnit());
        copy.setNextPostingDate(source.getNextPostingDate());
        copy.setExpiryDate(source.getExpiryDate());
        copy.setStatus(TransactionBatchStatus.INACTIVE.getValue());
        copy.setCopiedFromBatchId(source.getId());
        copy.setCreatedBy(this.access.user().getId());
        copy.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.access.touch(copy);
        this.batchRepository.saveAndFlush(copy);
        copy.setCode(this.access.config().getCodePrefix() + copy.getId());
        int sequence = 1;
        for (final TransactionBatchItem sourceItem : this.itemRepository.findByBatch_IdOrderByIdAsc(source.getId())) {
            final TransactionBatchItem item = new TransactionBatchItem();
            item.setBatch(copy);
            item.setAccountType(sourceItem.getAccountType());
            item.setGlAccountId(sourceItem.getGlAccountId());
            item.setSavingsAccountId(sourceItem.getSavingsAccountId());
            item.setSide(sourceItem.getSide());
            item.setCurrencyCode(sourceItem.getCurrencyCode());
            item.setAmount(sourceItem.getAmount());
            item.setNarrative(sourceItem.getNarrative());
            item.setValueDate(sourceItem.getValueDate());
            item.setReference(this.references.next(copy.getCode(), DateUtils.getBusinessLocalDate(), sequence++));
            item.setStatus(TransactionBatchItemStatus.ACTIVE.getValue());
            item.setCreatedBy(this.access.user().getId());
            item.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
            this.access.touch(item);
            this.itemRepository.save(item);
        }
        return new CommandProcessingResultBuilder().withEntityId(copy.getId()).withOfficeId(copy.getOfficeId()).build();
    }

    @Transactional
    public CommandProcessingResult addItem(final JsonCommand command) {
        final TransactionBatch batch = this.access.requireBatch(command.entityId());
        this.access.assertEditable(batch);
        final TransactionBatchItem item = new TransactionBatchItem();
        item.setBatch(batch);
        applyItem(batch, item, this.validator.parseItem(command.json()));
        item.setStatus(TransactionBatchItemStatus.ACTIVE.getValue());
        item.setCreatedBy(this.access.user().getId());
        item.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.access.touch(item);
        this.itemRepository.save(item);
        return new CommandProcessingResultBuilder().withEntityId(item.getId()).withOfficeId(batch.getOfficeId()).build();
    }

    @Transactional
    public CommandProcessingResult updateItem(final JsonCommand command) {
        final Long batchId = command.subentityId();
        final TransactionBatch batch = this.access.requireBatch(batchId);
        this.access.assertEditable(batch);
        final TransactionBatchItem item = this.access.requireItem(batchId, command.entityId());
        applyItem(batch, item, this.validator.parseItem(command.json()));
        this.access.touch(item);
        return new CommandProcessingResultBuilder().withEntityId(item.getId()).withOfficeId(batch.getOfficeId()).build();
    }

    @Transactional
    public CommandProcessingResult deleteItem(final JsonCommand command) {
        final Long batchId = command.subentityId();
        final TransactionBatch batch = this.access.requireBatch(batchId);
        this.access.assertEditable(batch);
        final TransactionBatchItem item = this.access.requireItem(batchId, command.entityId());
        this.itemRepository.delete(item);
        return new CommandProcessingResultBuilder().withEntityId(item.getId()).build();
    }

    public TransactionBatchItem buildItem(final TransactionBatch batch, final ItemFields fields) {
        final TransactionBatchItem item = new TransactionBatchItem();
        item.setBatch(batch);
        applyItem(batch, item, fields);
        item.setStatus(TransactionBatchItemStatus.ACTIVE.getValue());
        item.setCreatedBy(this.access.user().getId());
        item.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.access.touch(item);
        return item;
    }

    private void apply(final TransactionBatch batch, final Header header) {
        batch.setDescription(header.description());
        batch.setRecurring(header.recurring());
        batch.setEffectiveDate(header.effectiveDate());
        if (header.recurring()) {
            batch.setFrequencyNumber(header.frequencyNumber());
            batch.setFrequencyUnit(header.frequencyUnit().getValue());
            batch.setNextPostingDate(header.nextPostingDate());
            batch.setExpiryDate(header.expiryDate());
        } else {
            batch.setFrequencyNumber(null);
            batch.setFrequencyUnit(null);
            batch.setNextPostingDate(null);
            batch.setExpiryDate(null);
        }
    }

    private void applyItem(final TransactionBatch batch, final TransactionBatchItem item, final ItemFields fields) {
        final TransactionBatchAccountType accountType;
        try {
            accountType = TransactionBatchAccountType.fromCode(fields.accountType());
        } catch (final IllegalArgumentException ex) {
            throw new TransactionBatchRuleException("account.type.invalid", "Account Type must be GL or CUSTOMER");
        }
        final TransactionBatchSide side;
        try {
            side = TransactionBatchSide.fromCode(fields.side());
        } catch (final IllegalArgumentException ex) {
            throw new TransactionBatchRuleException("side.invalid", "Transaction Type must be DEBIT or CREDIT");
        }
        final LocalDate valueDate = fields.valueDate() == null ? DateUtils.getBusinessLocalDate() : fields.valueDate();
        if (batch.getEffectiveDate() != null && valueDate.isAfter(batch.getEffectiveDate())) {
            throw new TransactionBatchRuleException("value.date.after.effective",
                    "Value Date cannot be after the batch Effective Date");
        }
        final String narrative = fields.narrative() == null ? batch.getDescription() : fields.narrative();
        if (narrative == null || narrative.isBlank()) {
            throw new TransactionBatchRuleException("narrative.required", "Narrative is required");
        }
        item.setAccountType(accountType.getValue());
        item.setSide(side.getValue());
        item.setCurrencyCode(fields.currencyCode());
        item.setAmount(fields.amount());
        item.setNarrative(narrative);
        item.setValueDate(valueDate);
        if (accountType.isGl()) {
            this.accountRules.requirePostableGl(fields.glAccountId());
            this.accountRules.requireOrganisationCurrency(fields.currencyCode());
            item.setGlAccountId(fields.glAccountId());
            item.setSavingsAccountId(null);
        } else {
            this.accountRules.requireSavings(fields.savingsAccountId(), side, fields.currencyCode());
            item.setSavingsAccountId(fields.savingsAccountId());
            item.setGlAccountId(null);
        }
        if (fields.reference() != null) {
            item.setReference(fields.reference());
        } else if (item.getReference() == null) {
            final int sequence = (int) this.itemRepository.countByBatch_Id(batch.getId()) + 1;
            item.setReference(this.references.next(batch.getCode(), DateUtils.getBusinessLocalDate(), sequence));
        }
    }
}
