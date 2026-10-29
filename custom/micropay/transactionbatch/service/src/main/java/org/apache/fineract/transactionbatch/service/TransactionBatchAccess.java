/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandProcessingResultType;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.transactionbatch.domain.TransactionBatch;
import org.apache.fineract.transactionbatch.domain.TransactionBatchConfig;
import org.apache.fineract.transactionbatch.domain.TransactionBatchConfigRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItem;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItemRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchRepository;
import org.apache.fineract.transactionbatch.exception.TransactionBatchNotFoundException;
import org.apache.fineract.transactionbatch.exception.TransactionBatchRuleException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchAccess {

    private final PlatformSecurityContext context;
    private final TransactionBatchRepository batchRepository;
    private final TransactionBatchItemRepository itemRepository;
    private final TransactionBatchConfigRepository configRepository;
    private final OfficeRepositoryWrapper officeRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public AppUser user() {
        return this.context.authenticatedUser();
    }

    public TransactionBatch requireBatch(final Long batchId) {
        final TransactionBatch batch = this.batchRepository.findById(batchId).orElseThrow(() -> new TransactionBatchNotFoundException(batchId));
        assertOffice(batch.getOfficeId());
        return batch;
    }

    public TransactionBatch requireBatchForUpdate(final Long batchId) {
        final TransactionBatch batch = this.batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new TransactionBatchNotFoundException(batchId));
        assertOffice(batch.getOfficeId());
        return batch;
    }

    public TransactionBatchItem requireItem(final Long batchId, final Long itemId) {
        requireBatch(batchId);
        return this.itemRepository.findByIdAndBatch_Id(itemId, batchId).orElseThrow(() -> TransactionBatchNotFoundException.item(itemId));
    }

    public void assertEditable(final TransactionBatch batch) {
        if (!batch.isInactive()) {
            throw new TransactionBatchRuleException("not.inactive", "Only an Inactive batch can be changed");
        }
        if (pendingCommandId(batch.getId()) != null) {
            throw new TransactionBatchRuleException("pending.submit", "The batch is submitted and awaiting approval");
        }
    }

    public Long pendingCommandId(final Long batchId) {
        final List<Long> ids = this.entityManager.createQuery("""
                select c.id from CommandSource c
                where c.entityName = 'TRANSACTIONBATCH' and c.actionName = 'SUBMIT'
                and c.resourceId = :id and c.status = :status
                order by c.id desc
                """, Long.class).setParameter("id", batchId).setParameter("status", CommandProcessingResultType.AWAITING_APPROVAL.getValue())
                .setMaxResults(1).getResultList();
        return ids.isEmpty() ? null : ids.get(0);
    }

    public void assertOffice(final Long officeId) {
        final Office office = this.officeRepository.findOneWithNotFoundDetection(officeId);
        final String userHierarchy = user().getOffice().getHierarchy();
        if (office.getHierarchy() == null || !office.getHierarchy().startsWith(userHierarchy)) {
            throw new NoAuthorizationException("The user cannot work with batches of office " + officeId);
        }
    }

    public TransactionBatchConfig config() {
        return this.configRepository.findById(TransactionBatchConfig.SINGLETON_ID).orElseGet(this::defaultConfig);
    }

    public void touch(final TransactionBatch batch) {
        batch.setLastModifiedBy(user().getId());
        batch.setLastModifiedOnUtc(DateUtils.getAuditOffsetDateTime());
    }

    public void touch(final TransactionBatchItem item) {
        final OffsetDateTime now = DateUtils.getAuditOffsetDateTime();
        item.setLastModifiedBy(user().getId());
        item.setLastModifiedOnUtc(now);
    }

    private TransactionBatchConfig defaultConfig() {
        final TransactionBatchConfig config = new TransactionBatchConfig();
        config.setId(TransactionBatchConfig.SINGLETON_ID);
        config.setCodePrefix("100");
        config.setImportRowCap(5000);
        config.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        config.setLastModifiedOnUtc(config.getCreatedOnUtc());
        return this.configRepository.save(config);
    }
}
