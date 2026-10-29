/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.transactionbatch.domain.TransactionBatchConfig;
import org.apache.fineract.transactionbatch.domain.TransactionBatchConfigRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchSetting;
import org.apache.fineract.transactionbatch.domain.TransactionBatchSettingRepository;
import org.apache.fineract.transactionbatch.service.TransactionBatchValidator.CurrencySetting;
import org.apache.fineract.transactionbatch.service.TransactionBatchValidator.Settings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchSettingService {

    private final TransactionBatchValidator validator;
    private final TransactionBatchAccountRules accountRules;
    private final TransactionBatchConfigRepository configRepository;
    private final TransactionBatchSettingRepository settingRepository;
    private final TransactionBatchAccess access;

    @Transactional
    public CommandProcessingResult update(final JsonCommand command) {
        final Settings settings = this.validator.parseSettings(command.json());
        final TransactionBatchConfig config = this.access.config();
        config.setCodePrefix(settings.codePrefix());
        config.setImportRowCap(settings.importRowCap());
        final OffsetDateTime now = DateUtils.getAuditOffsetDateTime();
        config.setLastModifiedBy(this.access.user().getId());
        config.setLastModifiedOnUtc(now);
        this.configRepository.save(config);
        this.settingRepository.deleteAll();
        final List<TransactionBatchSetting> rows = new ArrayList<>();
        for (final CurrencySetting currency : settings.currencies()) {
            this.accountRules.requireOrganisationCurrency(currency.currencyCode());
            this.accountRules.requirePostableGl(currency.clearingGlAccountId());
            this.accountRules.requirePostableGl(currency.suspenseGlAccountId());
            final TransactionBatchSetting row = new TransactionBatchSetting();
            row.setCurrencyCode(currency.currencyCode());
            row.setClearingGlAccountId(currency.clearingGlAccountId());
            row.setSuspenseGlAccountId(currency.suspenseGlAccountId());
            rows.add(row);
        }
        this.settingRepository.saveAll(rows);
        return new CommandProcessingResultBuilder().withEntityId(TransactionBatchConfig.SINGLETON_ID).build();
    }
}
