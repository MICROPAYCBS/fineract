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
package org.apache.fineract.treasury.service;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.ACCOUNT_TYPE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.COUNTERPARTY_NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.CURRENCY_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.EXTERNAL_ACCOUNT_NUMBER_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.GL_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MAX_BALANCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MIN_BALANCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.OFFICE_ID_PARAM;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.treasury.data.TreasuryAccountRequest;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.domain.TreasuryAccountType;
import org.apache.fineract.treasury.domain.TreasuryAgentFloatRepository;
import org.apache.fineract.treasury.domain.TreasuryAgentFloatStatus;
import org.apache.fineract.treasury.domain.TreasuryTransferRepository;
import org.apache.fineract.treasury.domain.TreasuryTransferStatus;
import org.apache.fineract.treasury.exception.TreasuryAccountException;
import org.apache.fineract.treasury.exception.TreasuryAccountNotFoundException;
import org.apache.fineract.treasury.serialization.TreasuryAccountDataValidator;
import org.apache.fineract.treasury.service.TreasuryReferenceDataLookup.GlAccountInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryAccountWritePlatformServiceImpl implements TreasuryAccountWritePlatformService {

    private final TreasuryAccountDataValidator validator;
    private final TreasuryAccountRepository accountRepository;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final TreasuryTransferRepository transferRepository;
    private final TreasuryAgentFloatRepository agentFloatRepository;

    /**
     * An account with transfers not yet received or cancelled, or with active agent floats, cannot be closed or moved
     * to another GL account, office or currency: the pending legs were worked out against the current ones.
     */
    private void requireNoOpenWork(final TreasuryAccount account, final String action) {
        final long openTransfers = this.transferRepository.countByAccountAndStatusIn(account.getId(),
                List.of(TreasuryTransferStatus.REQUESTED, TreasuryTransferStatus.APPROVED, TreasuryTransferStatus.SENT));
        if (openTransfers > 0) {
            throw new TreasuryAccountException("open.transfers",
                    "Treasury account " + account.getCode() + " has " + openTransfers + " open transfers and cannot " + action,
                    account.getId(), openTransfers);
        }
        final long activeFloats = this.agentFloatRepository.countByTreasuryAccountIdAndStatus(account.getId(),
                TreasuryAgentFloatStatus.ACTIVE);
        if (activeFloats > 0) {
            throw new TreasuryAccountException("active.agent.floats",
                    "Treasury account " + account.getCode() + " backs " + activeFloats + " active agent floats and cannot " + action,
                    account.getId(), activeFloats);
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult createAccount(final JsonCommand command) {
        final TreasuryAccountRequest request = this.validator.validateForCreate(command.json());
        if (this.accountRepository.existsByCode(request.getCode())) {
            throw new TreasuryAccountException("duplicate.code", "A treasury account with code " + request.getCode() + " already exists",
                    request.getCode());
        }
        final TreasuryAccount account = new TreasuryAccount();
        account.setCode(request.getCode());
        account.setName(request.getName());
        account.setAccountType(TreasuryAccountType.valueOf(request.getAccountType()));
        account.setGlAccountId(request.getGlAccountId());
        account.setOfficeId(request.getOfficeId());
        account.setCurrencyCode(request.getCurrencyCode());
        account.setCounterpartyName(request.getCounterpartyName());
        account.setExternalAccountNumber(request.getExternalAccountNumber());
        account.setMinBalance(request.getMinBalance());
        account.setMaxBalance(request.getMaxBalance());
        account.setStatus(TreasuryAccountStatus.ACTIVE);
        validateReferences(account);
        this.accountRepository.saveAndFlush(account);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(account.getId())
                .withOfficeId(account.getOfficeId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateAccount(final Long accountId, final JsonCommand command) {
        final TreasuryAccount account = findActive(accountId, "update");
        final TreasuryAccountRequest request = this.validator.validateForUpdate(command.json());
        final Map<String, Object> changes = new LinkedHashMap<>();
        if (request.has(NAME_PARAM) && changed(account.getName(), request.getName(), NAME_PARAM, changes)) {
            account.setName(request.getName());
        }
        if (request.has(ACCOUNT_TYPE_PARAM)
                && changed(account.getAccountType().name(), request.getAccountType(), ACCOUNT_TYPE_PARAM, changes)) {
            account.setAccountType(TreasuryAccountType.valueOf(request.getAccountType()));
        }
        if (request.has(GL_ACCOUNT_ID_PARAM) && changed(account.getGlAccountId(), request.getGlAccountId(), GL_ACCOUNT_ID_PARAM, changes)) {
            account.setGlAccountId(request.getGlAccountId());
        }
        if (request.has(OFFICE_ID_PARAM) && changed(account.getOfficeId(), request.getOfficeId(), OFFICE_ID_PARAM, changes)) {
            account.setOfficeId(request.getOfficeId());
        }
        if (request.has(CURRENCY_CODE_PARAM)
                && changed(account.getCurrencyCode(), request.getCurrencyCode(), CURRENCY_CODE_PARAM, changes)) {
            account.setCurrencyCode(request.getCurrencyCode());
        }
        if (request.has(COUNTERPARTY_NAME_PARAM)
                && changed(account.getCounterpartyName(), request.getCounterpartyName(), COUNTERPARTY_NAME_PARAM, changes)) {
            account.setCounterpartyName(request.getCounterpartyName());
        }
        if (request.has(EXTERNAL_ACCOUNT_NUMBER_PARAM) && changed(account.getExternalAccountNumber(), request.getExternalAccountNumber(),
                EXTERNAL_ACCOUNT_NUMBER_PARAM, changes)) {
            account.setExternalAccountNumber(request.getExternalAccountNumber());
        }
        if (request.has(MIN_BALANCE_PARAM) && amountChanged(account.getMinBalance(), request.getMinBalance(), MIN_BALANCE_PARAM, changes)) {
            account.setMinBalance(request.getMinBalance());
        }
        if (request.has(MAX_BALANCE_PARAM) && amountChanged(account.getMaxBalance(), request.getMaxBalance(), MAX_BALANCE_PARAM, changes)) {
            account.setMaxBalance(request.getMaxBalance());
        }
        this.validator.validateBalanceRange(account.getMinBalance(), account.getMaxBalance());
        if (changes.containsKey(GL_ACCOUNT_ID_PARAM) || changes.containsKey(OFFICE_ID_PARAM) || changes.containsKey(CURRENCY_CODE_PARAM)) {
            requireNoOpenWork(account, "have its GL account, office or currency changed");
            validateReferences(account);
        }
        if (!changes.isEmpty()) {
            this.accountRepository.saveAndFlush(account);
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(accountId)
                .withOfficeId(account.getOfficeId()).with(changes).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult closeAccount(final Long accountId, final JsonCommand command) {
        final TreasuryAccount account = findActive(accountId, "close");
        requireNoOpenWork(account, "be closed");
        account.setStatus(TreasuryAccountStatus.CLOSED);
        this.accountRepository.saveAndFlush(account);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(accountId)
                .withOfficeId(account.getOfficeId()).with(Map.of("status", TreasuryAccountStatus.CLOSED.name())).build();
    }

    /**
     * The GL account must be an enabled asset detail account, the office must exist, the currency must be one the
     * organisation uses, and no other active treasury account may use the same GL account, office and currency (its
     * cash would be counted twice in the positions).
     */
    void validateReferences(final TreasuryAccount account) {
        final GlAccountInfo glAccount = this.referenceDataLookup.findGlAccount(account.getGlAccountId())
                .orElseThrow(() -> new TreasuryAccountException("gl.account.not.found",
                        "GL account " + account.getGlAccountId() + " does not exist", account.getGlAccountId()));
        if (!glAccount.isAssetDetail()) {
            throw new TreasuryAccountException("gl.account.not.asset.detail",
                    "GL account " + account.getGlAccountId() + " must be an asset detail account", account.getGlAccountId());
        }
        if (glAccount.disabled()) {
            throw new TreasuryAccountException("gl.account.disabled", "GL account " + account.getGlAccountId() + " is disabled",
                    account.getGlAccountId());
        }
        if (!this.referenceDataLookup.officeExists(account.getOfficeId())) {
            throw new TreasuryAccountException("office.not.found", "Office " + account.getOfficeId() + " does not exist",
                    account.getOfficeId());
        }
        if (!this.referenceDataLookup.isOrganisationCurrency(account.getCurrencyCode())) {
            throw new TreasuryAccountException("currency.not.organisation.currency",
                    "Currency " + account.getCurrencyCode() + " is not an organisation currency", account.getCurrencyCode());
        }
        final boolean glAccountTaken = this.accountRepository
                .findByGlAccountIdAndOfficeIdAndCurrencyCodeAndStatus(account.getGlAccountId(), account.getOfficeId(),
                        account.getCurrencyCode(), TreasuryAccountStatus.ACTIVE)
                .stream().anyMatch(other -> !Objects.equals(other.getId(), account.getId()));
        if (glAccountTaken) {
            throw new TreasuryAccountException("gl.account.in.use",
                    "GL account " + account.getGlAccountId() + " is already used by an active treasury account in office "
                            + account.getOfficeId() + " and currency " + account.getCurrencyCode(),
                    account.getGlAccountId(), account.getOfficeId(), account.getCurrencyCode());
        }
    }

    private TreasuryAccount findActive(final Long accountId, final String action) {
        final TreasuryAccount account = this.accountRepository.findById(accountId)
                .orElseThrow(() -> new TreasuryAccountNotFoundException(accountId));
        if (!account.isActive()) {
            throw new TreasuryAccountException("closed", "Treasury account " + accountId + " is closed and cannot be " + action + "d",
                    accountId);
        }
        return account;
    }

    private static boolean changed(final Object current, final Object requested, final String parameterName,
            final Map<String, Object> changes) {
        if (Objects.equals(current, requested)) {
            return false;
        }
        changes.put(parameterName, requested);
        return true;
    }

    private static boolean amountChanged(final BigDecimal current, final BigDecimal requested, final String parameterName,
            final Map<String, Object> changes) {
        if (current == null ? requested == null : requested != null && current.compareTo(requested) == 0) {
            return false;
        }
        changes.put(parameterName, requested);
        return true;
    }
}
