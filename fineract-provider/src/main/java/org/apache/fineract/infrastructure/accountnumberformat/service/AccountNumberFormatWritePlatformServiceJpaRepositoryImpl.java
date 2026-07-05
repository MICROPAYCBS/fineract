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
package org.apache.fineract.infrastructure.accountnumberformat.service;

import jakarta.persistence.PersistenceException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.fineract.infrastructure.accountnumberformat.data.AccountNumberFormatDataValidator;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormat;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatEnumerations.AccountNumberPrefixType;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatRepositoryWrapper;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberSequenceScope;
import org.apache.fineract.infrastructure.accountnumberformat.domain.CheckDigitAlgorithm;
import org.apache.fineract.infrastructure.accountnumberformat.domain.EntityAccountType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountNumberFormatWritePlatformServiceJpaRepositoryImpl implements AccountNumberFormatWritePlatformService {

    private static final Logger LOG = LoggerFactory.getLogger(AccountNumberFormatWritePlatformServiceJpaRepositoryImpl.class);
    private final AccountNumberFormatRepositoryWrapper accountNumberFormatRepository;
    private final AccountNumberFormatDataValidator accountNumberFormatDataValidator;

    @Autowired
    AccountNumberFormatWritePlatformServiceJpaRepositoryImpl(final AccountNumberFormatRepositoryWrapper accountNumberFormatRepository,
            final AccountNumberFormatDataValidator accountNumberFormatDataValidator) {
        this.accountNumberFormatRepository = accountNumberFormatRepository;
        this.accountNumberFormatDataValidator = accountNumberFormatDataValidator;
    }

    @Override
    @Transactional
    public CommandProcessingResult createAccountNumberFormat(JsonCommand command) {
        try {
            this.accountNumberFormatDataValidator.validateForCreate(command.json());

            final Integer accountTypeId = command.integerValueSansLocaleOfParameterNamed(AccountNumberFormatConstants.accountTypeParamName);
            final EntityAccountType entityAccountType = EntityAccountType.fromInt(accountTypeId);

            final Integer prefixTypeId = command.integerValueSansLocaleOfParameterNamed(AccountNumberFormatConstants.prefixTypeParamName);
            AccountNumberPrefixType accountNumberPrefixType = null;
            if (prefixTypeId != null) {
                accountNumberPrefixType = AccountNumberPrefixType.fromInt(prefixTypeId);
            }

            String prefixCharacter = command.stringValueOfParameterNamed(AccountNumberFormatConstants.prefixCharacterParamName);
            final String formatPattern = command.stringValueOfParameterNamed(AccountNumberFormatConstants.formatPatternParamName);
            final Integer sequenceScopeId = command.integerValueSansLocaleOfParameterNamed(AccountNumberFormatConstants.sequenceScopeParamName);
            final Integer checkDigitAlgorithmId = command
                    .integerValueSansLocaleOfParameterNamed(AccountNumberFormatConstants.checkDigitAlgorithmParamName);
            final Boolean structuredEnabled = command.booleanObjectValueOfParameterNamed(AccountNumberFormatConstants.structuredEnabledParamName);

            AccountNumberFormat accountNumberFormat = new AccountNumberFormat(entityAccountType, accountNumberPrefixType, prefixCharacter);
            applyStructuredFields(accountNumberFormat, formatPattern, sequenceScopeId, checkDigitAlgorithmId, structuredEnabled);

            this.accountNumberFormatRepository.saveAndFlush(accountNumberFormat);

            return new CommandProcessingResultBuilder() //
                    .withEntityId(accountNumberFormat.getId()) //
                    .build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.empty();
        } catch (final PersistenceException ee) {
            Throwable throwable = ExceptionUtils.getRootCause(ee.getCause());
            handleDataIntegrityIssues(command, throwable, ee);
            return CommandProcessingResult.empty();
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult updateAccountNumberFormat(Long accountNumberFormatId, JsonCommand command) {
        try {

            final AccountNumberFormat accountNumberFormatForUpdate = this.accountNumberFormatRepository
                    .findOneWithNotFoundDetection(accountNumberFormatId);
            EntityAccountType accountType = accountNumberFormatForUpdate.getAccountType();

            this.accountNumberFormatDataValidator.validateForUpdate(command.json(), accountType);

            final Map<String, Object> actualChanges = new LinkedHashMap<>(9);

            if (command.isChangeInIntegerSansLocaleParameterNamed(AccountNumberFormatConstants.prefixTypeParamName,
                    accountNumberFormatForUpdate.getPrefixEnum())) {
                final Integer newValue = command.integerValueSansLocaleOfParameterNamed(AccountNumberFormatConstants.prefixTypeParamName);
                final AccountNumberPrefixType accountNumberPrefixType = AccountNumberPrefixType.fromInt(newValue);
                actualChanges.put(AccountNumberFormatConstants.prefixTypeParamName, accountNumberPrefixType);
                accountNumberFormatForUpdate.setPrefix(accountNumberPrefixType);
            }

            if (command.isChangeInStringParameterNamed(AccountNumberFormatConstants.prefixCharacterParamName,
                    accountNumberFormatForUpdate.getPrefixCharacter())) {
                final String newValue = command.stringValueOfParameterNamed(AccountNumberFormatConstants.prefixCharacterParamName);
                actualChanges.put(AccountNumberFormatConstants.prefixCharacterParamName, newValue);
                accountNumberFormatForUpdate.setPrefixCharacter(newValue);
            }

            if (command.isChangeInStringParameterNamed(AccountNumberFormatConstants.formatPatternParamName,
                    accountNumberFormatForUpdate.getFormatPattern())) {
                final String newValue = command.stringValueOfParameterNamed(AccountNumberFormatConstants.formatPatternParamName);
                actualChanges.put(AccountNumberFormatConstants.formatPatternParamName, newValue);
                accountNumberFormatForUpdate.setFormatPattern(newValue);
            }

            if (command.isChangeInIntegerSansLocaleParameterNamed(AccountNumberFormatConstants.sequenceScopeParamName,
                    accountNumberFormatForUpdate.getSequenceScopeEnum())) {
                final Integer newValue = command.integerValueSansLocaleOfParameterNamed(AccountNumberFormatConstants.sequenceScopeParamName);
                actualChanges.put(AccountNumberFormatConstants.sequenceScopeParamName, newValue);
                accountNumberFormatForUpdate.setSequenceScope(AccountNumberSequenceScope.fromInt(newValue));
            }

            if (command.isChangeInIntegerSansLocaleParameterNamed(AccountNumberFormatConstants.checkDigitAlgorithmParamName,
                    accountNumberFormatForUpdate.getCheckDigitAlgorithmEnum())) {
                final Integer newValue = command
                        .integerValueSansLocaleOfParameterNamed(AccountNumberFormatConstants.checkDigitAlgorithmParamName);
                actualChanges.put(AccountNumberFormatConstants.checkDigitAlgorithmParamName, newValue);
                accountNumberFormatForUpdate.setCheckDigitAlgorithm(CheckDigitAlgorithm.fromInt(newValue));
            }

            if (command.isChangeInBooleanParameterNamed(AccountNumberFormatConstants.structuredEnabledParamName,
                    accountNumberFormatForUpdate.getStructuredEnabled())) {
                final Boolean newValue = command.booleanObjectValueOfParameterNamed(AccountNumberFormatConstants.structuredEnabledParamName);
                actualChanges.put(AccountNumberFormatConstants.structuredEnabledParamName, newValue);
                accountNumberFormatForUpdate.setStructuredEnabled(newValue);
            }

            if (!actualChanges.isEmpty()) {
                this.accountNumberFormatRepository.saveAndFlush(accountNumberFormatForUpdate);
            }

            return new CommandProcessingResultBuilder() //
                    .withCommandId(command.commandId()) //
                    .withEntityId(accountNumberFormatId) //
                    .with(actualChanges) //
                    .build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.empty();
        } catch (final PersistenceException ee) {
            Throwable throwable = ExceptionUtils.getRootCause(ee.getCause());
            handleDataIntegrityIssues(command, throwable, ee);
            return CommandProcessingResult.empty();
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteAccountNumberFormat(Long accountNumberFormatId) {
        AccountNumberFormat accountNumberFormat = this.accountNumberFormatRepository.findOneWithNotFoundDetection(accountNumberFormatId);
        this.accountNumberFormatRepository.delete(accountNumberFormat);

        return new CommandProcessingResultBuilder() //
                .withEntityId(accountNumberFormatId) //
                .build();
    }

    /*
     * Guaranteed to throw an exception no matter what the data integrity issue is.
     */
    private void handleDataIntegrityIssues(final JsonCommand command, final Throwable realCause, final Exception dve) {
        if (realCause.getMessage().contains(AccountNumberFormatConstants.ACCOUNT_TYPE_UNIQUE_CONSTRAINT_NAME)) {
            final Integer accountTypeId = command.integerValueSansLocaleOfParameterNamed(AccountNumberFormatConstants.accountTypeParamName);
            final EntityAccountType entityAccountType = EntityAccountType.fromInt(accountTypeId);
            throw new PlatformDataIntegrityException(AccountNumberFormatConstants.EXCEPTION_DUPLICATE_ACCOUNT_TYPE,
                    "Account Format preferences for Account type `" + entityAccountType.getCode() + "` already exists", "externalId",
                    entityAccountType.getValue(), entityAccountType.getCode());
        }
        LOG.error("Error occured.", dve);
        throw ErrorHandler.getMappable(dve, "error.msg.account.number.format.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource.");
    }

    private void applyStructuredFields(final AccountNumberFormat accountNumberFormat, final String formatPattern,
            final Integer sequenceScopeId, final Integer checkDigitAlgorithmId, final Boolean structuredEnabled) {
        if (formatPattern != null) {
            accountNumberFormat.setFormatPattern(formatPattern);
        }
        if (sequenceScopeId != null) {
            accountNumberFormat.setSequenceScope(AccountNumberSequenceScope.fromInt(sequenceScopeId));
        }
        if (checkDigitAlgorithmId != null) {
            accountNumberFormat.setCheckDigitAlgorithm(CheckDigitAlgorithm.fromInt(checkDigitAlgorithmId));
        }
        if (structuredEnabled != null) {
            accountNumberFormat.setStructuredEnabled(structuredEnabled);
        }
    }
}
