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

import static org.apache.fineract.treasury.api.TreasuryApiConstants.ALLOW_INSUFFICIENT_BALANCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.ALLOW_LIMIT_REFERRAL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.AUTO_SETTLE_CASHFLOWS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.CASH_IN_TRANSIT_GL_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.FLOAT_PAYMENT_TYPE_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.RECON_REFERENCE_RULE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.RECON_SAME_DATE_RULE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.RECON_WINDOW_DAYS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.RECON_WINDOW_RULE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.ROLLOVER_WINDOW_DAYS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.STALE_PRICE_DAYS_PARAM;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.treasury.data.TreasurySettingData;
import org.apache.fineract.treasury.domain.TreasurySetting;
import org.apache.fineract.treasury.domain.TreasurySettingRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator.SettingRequest;
import org.apache.fineract.treasury.service.TreasuryReferenceDataLookup.GlAccountInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-currency treasury settings: the cash-in-transit GL account, the agent float payment type and whether a transfer
 * may be sent from an account without the balance.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasurySettingService {

    private static final String ENTITY = "setting";

    private final TreasurySettingRepository settingRepository;
    private final TreasuryWorkflowDataValidator validator;
    private final TreasuryReferenceDataLookup referenceDataLookup;

    @Transactional(readOnly = true)
    public List<TreasurySettingData> retrieveSettings() {
        return this.settingRepository.findAllByOrderByCurrencyCodeAsc().stream().map(TreasurySettingData::from).toList();
    }

    /** Creates the currency's settings on first use; only the fields sent are changed. */
    @Transactional
    public CommandProcessingResult updateSettings(final JsonCommand command) {
        final SettingRequest request = this.validator.validateSetting(command.json());
        if (!this.referenceDataLookup.isOrganisationCurrency(request.currencyCode())) {
            throw new TreasuryWorkflowException(ENTITY, "currency.not.organisation.currency",
                    "Currency " + request.currencyCode() + " is not an organisation currency", request.currencyCode());
        }
        final TreasurySetting setting = this.settingRepository.findByCurrencyCode(request.currencyCode()).orElseGet(() -> {
            final TreasurySetting created = new TreasurySetting();
            created.setCurrencyCode(request.currencyCode());
            return created;
        });
        final Map<String, Object> changes = new LinkedHashMap<>();
        if (request.has(CASH_IN_TRANSIT_GL_ACCOUNT_ID_PARAM)
                && !Objects.equals(setting.getCashInTransitGlAccountId(), request.cashInTransitGlAccountId())) {
            if (request.cashInTransitGlAccountId() != null) {
                requireAssetDetail(request.cashInTransitGlAccountId());
            }
            setting.setCashInTransitGlAccountId(request.cashInTransitGlAccountId());
            changes.put(CASH_IN_TRANSIT_GL_ACCOUNT_ID_PARAM, request.cashInTransitGlAccountId());
        }
        if (request.has(FLOAT_PAYMENT_TYPE_ID_PARAM) && !Objects.equals(setting.getFloatPaymentTypeId(), request.floatPaymentTypeId())) {
            if (request.floatPaymentTypeId() != null && !this.referenceDataLookup.isActivePaymentType(request.floatPaymentTypeId())) {
                throw new TreasuryWorkflowException(ENTITY, "payment.type.not.found",
                        "Payment type " + request.floatPaymentTypeId() + " does not exist or is inactive", request.floatPaymentTypeId());
            }
            // a cash payment type would make the savings deposit ask for a legal tender breakdown
            if (request.floatPaymentTypeId() != null && this.referenceDataLookup.isCashPaymentType(request.floatPaymentTypeId())) {
                throw new TreasuryWorkflowException(ENTITY, "payment.type.is.cash",
                        "The agent float payment type must not be a cash payment type", request.floatPaymentTypeId());
            }
            setting.setFloatPaymentTypeId(request.floatPaymentTypeId());
            changes.put(FLOAT_PAYMENT_TYPE_ID_PARAM, request.floatPaymentTypeId());
        }
        if (request.has(ALLOW_INSUFFICIENT_BALANCE_PARAM) && setting.isAllowInsufficientBalance() != request.allowInsufficientBalance()) {
            setting.setAllowInsufficientBalance(request.allowInsufficientBalance());
            changes.put(ALLOW_INSUFFICIENT_BALANCE_PARAM, request.allowInsufficientBalance());
        }
        if (request.has(RECON_REFERENCE_RULE_PARAM) && setting.isReconReferenceRule() != request.reconReferenceRule()) {
            setting.setReconReferenceRule(request.reconReferenceRule());
            changes.put(RECON_REFERENCE_RULE_PARAM, request.reconReferenceRule());
        }
        if (request.has(RECON_SAME_DATE_RULE_PARAM) && setting.isReconSameDateRule() != request.reconSameDateRule()) {
            setting.setReconSameDateRule(request.reconSameDateRule());
            changes.put(RECON_SAME_DATE_RULE_PARAM, request.reconSameDateRule());
        }
        if (request.has(RECON_WINDOW_RULE_PARAM) && setting.isReconWindowRule() != request.reconWindowRule()) {
            setting.setReconWindowRule(request.reconWindowRule());
            changes.put(RECON_WINDOW_RULE_PARAM, request.reconWindowRule());
        }
        if (request.has(RECON_WINDOW_DAYS_PARAM) && setting.getReconWindowDays() != request.reconWindowDays()) {
            setting.setReconWindowDays(request.reconWindowDays());
            changes.put(RECON_WINDOW_DAYS_PARAM, request.reconWindowDays());
        }
        if (request.has(ALLOW_LIMIT_REFERRAL_PARAM) && setting.isAllowLimitReferral() != request.allowLimitReferral()) {
            setting.setAllowLimitReferral(request.allowLimitReferral());
            changes.put(ALLOW_LIMIT_REFERRAL_PARAM, request.allowLimitReferral());
        }
        if (request.has(AUTO_SETTLE_CASHFLOWS_PARAM) && setting.isAutoSettleCashflows() != request.autoSettleCashflows()) {
            setting.setAutoSettleCashflows(request.autoSettleCashflows());
            changes.put(AUTO_SETTLE_CASHFLOWS_PARAM, request.autoSettleCashflows());
        }
        if (request.has(ROLLOVER_WINDOW_DAYS_PARAM) && setting.getRolloverWindowDays() != request.rolloverWindowDays()) {
            setting.setRolloverWindowDays(request.rolloverWindowDays());
            changes.put(ROLLOVER_WINDOW_DAYS_PARAM, request.rolloverWindowDays());
        }
        if (request.has(STALE_PRICE_DAYS_PARAM) && setting.getStalePriceDays() != request.stalePriceDays()) {
            setting.setStalePriceDays(request.stalePriceDays());
            changes.put(STALE_PRICE_DAYS_PARAM, request.stalePriceDays());
        }
        if (setting.getId() == null || !changes.isEmpty()) {
            this.settingRepository.saveAndFlush(setting);
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(setting.getId()).with(changes).build();
    }

    /** Settings of the currency, or all defaults (no accounts set, insufficient balance blocked). */
    @Transactional(readOnly = true)
    public TreasurySetting settingsFor(final String currencyCode) {
        return this.settingRepository.findByCurrencyCode(currencyCode).orElseGet(() -> {
            final TreasurySetting defaults = new TreasurySetting();
            defaults.setCurrencyCode(currencyCode);
            return defaults;
        });
    }

    public Long requireCashInTransitGlAccount(final String currencyCode) {
        final Long glAccountId = settingsFor(currencyCode).getCashInTransitGlAccountId();
        if (glAccountId == null) {
            throw new TreasuryWorkflowException(ENTITY, "cash.in.transit.not.set", "No cash-in-transit GL account is set for "
                    + currencyCode + "; set one with PUT /treasury/settings before sending transfers", currencyCode);
        }
        return glAccountId;
    }

    public Long requireFloatPaymentType(final String currencyCode) {
        final Long paymentTypeId = settingsFor(currencyCode).getFloatPaymentTypeId();
        if (paymentTypeId == null) {
            throw new TreasuryWorkflowException(ENTITY, "float.payment.type.not.set", "No agent float payment type is set for "
                    + currencyCode + "; set one with PUT /treasury/settings before using agent floats", currencyCode);
        }
        return paymentTypeId;
    }

    private void requireAssetDetail(final Long glAccountId) {
        final GlAccountInfo glAccount = this.referenceDataLookup.findGlAccount(glAccountId)
                .orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "gl.account.not.found",
                        "GL account " + glAccountId + " does not exist", glAccountId));
        if (!glAccount.isAssetDetail() || glAccount.disabled()) {
            throw new TreasuryWorkflowException(ENTITY, "gl.account.not.asset.detail",
                    "The cash-in-transit GL account " + glAccountId + " must be an enabled asset detail account", glAccountId);
        }
    }
}
