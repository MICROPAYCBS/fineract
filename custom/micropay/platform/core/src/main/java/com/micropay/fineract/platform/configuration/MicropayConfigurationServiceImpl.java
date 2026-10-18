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
package com.micropay.fineract.platform.configuration;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.LegalTenderCaptureMode;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MicropayConfigurationServiceImpl implements MicropayConfigurationService {

    private final GlobalConfigurationRepositoryWrapper globalConfigurationRepository;

    @Override
    public boolean isCrossBranchServicingEnabled() {
        final GlobalConfigurationPropertyData property = getGlobalConfigurationPropertyData(
                MicropayGlobalConfigurationConstants.ENABLE_CROSS_BRANCH_SERVICING);
        return property.isEnabled();
    }

    @Override
    public boolean isPreventCashierOverdrawEnabled() {
        final GlobalConfigurationPropertyData property = getGlobalConfigurationPropertyData(
                MicropayGlobalConfigurationConstants.PREVENT_CASHIER_OVERDRAW);
        return property.isEnabled();
    }

    @Override
    public boolean isRequireCashierForCashTransactionsEnabled() {
        final GlobalConfigurationPropertyData property = getGlobalConfigurationPropertyData(
                MicropayGlobalConfigurationConstants.REQUIRE_CASHIER_FOR_CASH_TRANSACTIONS);
        return property.isEnabled();
    }

    @Override
    public LegalTenderCaptureMode retrieveLegalTenderCaptureModeForCashTransactions() {
        final GlobalConfigurationPropertyData property = getGlobalConfigurationPropertyData(
                MicropayGlobalConfigurationConstants.CAPTURE_LEGAL_TENDER_FOR_CASH_TRANSACTIONS);
        if (!property.isEnabled()) {
            return LegalTenderCaptureMode.OFF;
        }
        return LegalTenderCaptureMode.fromString(property.getStringValue());
    }

    @Override
    public boolean isStructuredAccountNumberFormatsEnabled() {
        final GlobalConfigurationPropertyData property = getGlobalConfigurationPropertyData(
                MicropayGlobalConfigurationConstants.STRUCTURED_ACCOUNT_NUMBER_FORMATS);
        return property.isEnabled();
    }

    @Override
    public boolean isStructuredGlCodesEnforced() {
        final GlobalConfigurationPropertyData property = getGlobalConfigurationPropertyData(
                MicropayGlobalConfigurationConstants.ENFORCE_STRUCTURED_GL_CODES);
        return property.isEnabled();
    }

    @Override
    public int retrieveStructuredGlCodeLength() {
        final GlobalConfigurationPropertyData property = getGlobalConfigurationPropertyData(
                MicropayGlobalConfigurationConstants.STRUCTURED_GL_CODE_LENGTH);
        if (property.getValue() == null || property.getValue() <= 0) {
            return 6;
        }
        return property.getValue().intValue();
    }

    @Override
    public boolean isAllowDirectLoanRepaymentsEnabled() {
        final GlobalConfigurationPropertyData property = getGlobalConfigurationPropertyData(
                MicropayGlobalConfigurationConstants.ALLOW_DIRECT_LOAN_REPAYMENTS);
        return property.isEnabled();
    }

    @Override
    public boolean isOrganizationWideAuditViewEnabled() {
        final GlobalConfigurationPropertyData property = getGlobalConfigurationPropertyData(
                MicropayGlobalConfigurationConstants.ENABLE_ORGANIZATION_WIDE_AUDIT_VIEW);
        return property.isEnabled();
    }

    @Override
    public Integer retrieveSessionIdleTimeoutMinutes() {
        final GlobalConfigurationPropertyData property = getGlobalConfigurationPropertyData(
                MicropayGlobalConfigurationConstants.SESSION_IDLE_TIMEOUT_MINUTES);
        if (!property.isEnabled()) {
            return 0; // switched off: no idle logout
        }
        if (property.getValue() == null || property.getValue() < 0) {
            return 15;
        }
        return property.getValue().intValue();
    }

    @Override
    public Integer retrieveSessionIdleWarningSeconds() {
        final GlobalConfigurationPropertyData property = getGlobalConfigurationPropertyData(
                MicropayGlobalConfigurationConstants.SESSION_IDLE_WARNING_SECONDS);
        if (!property.isEnabled()) {
            return 0; // switched off: log out without a warning
        }
        if (property.getValue() == null || property.getValue() < 0) {
            return 60;
        }
        return property.getValue().intValue();
    }

    @Override
    public Integer retrieveMaxActiveSessions() {
        final GlobalConfigurationPropertyData property = getGlobalConfigurationPropertyData(
                MicropayGlobalConfigurationConstants.ENFORCE_SINGLE_SESSION);
        if (!property.isEnabled()) {
            return null;
        }
        if (property.getValue() == null || property.getValue() < 1) {
            return 1;
        }
        return property.getValue().intValue();
    }

    @Override
    public Integer getGlBalanceSnapshotDailyRetentionDays() {
        final GlobalConfigurationPropertyData property = getGlobalConfigurationPropertyData(
                MicropayGlobalConfigurationConstants.GL_BALANCE_SNAPSHOT_DAILY_RETENTION_DAYS);
        if (property.isEnabled() && property.getValue() != null) {
            return property.getValue().intValue();
        }
        return null;
    }

    @Override
    public int retrieveChannelLimitIncreaseCoolingHours() {
        final GlobalConfigurationPropertyData property = getGlobalConfigurationPropertyData(
                MicropayGlobalConfigurationConstants.CHANNEL_LIMIT_INCREASE_COOLING_HOURS);
        if (!property.isEnabled() || property.getValue() == null) {
            return 0;
        }
        return Math.max(0, property.getValue().intValue());
    }

    private GlobalConfigurationPropertyData getGlobalConfigurationPropertyData(final String propertyName) {
        return globalConfigurationRepository.findOneByNameWithNotFoundDetection(propertyName).toData();
    }
}
