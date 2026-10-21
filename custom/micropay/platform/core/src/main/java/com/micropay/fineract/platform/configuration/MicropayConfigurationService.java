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

import org.apache.fineract.organisation.monetary.domain.LegalTenderCaptureMode;

/**
 * Typed access to the Micropay global configuration entries. This is the Micropay counterpart of
 * {@link org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService}.
 */
public interface MicropayConfigurationService {

    boolean isCrossBranchServicingEnabled();

    boolean isPreventCashierOverdrawEnabled();

    boolean isRequireCashierForCashTransactionsEnabled();

    LegalTenderCaptureMode retrieveLegalTenderCaptureModeForCashTransactions();

    boolean isStructuredAccountNumberFormatsEnabled();

    boolean isStructuredGlCodesEnforced();

    int retrieveStructuredGlCodeLength();

    boolean isAllowDirectLoanRepaymentsEnabled();

    boolean isOrganizationWideAuditViewEnabled();

    Integer retrieveSessionIdleTimeoutMinutes();

    Integer retrieveSessionIdleWarningSeconds();

    /**
     * Maximum number of concurrent two-factor sessions allowed per user, or {@code null} when single-session
     * enforcement is disabled (unlimited sessions).
     */
    Integer retrieveMaxActiveSessions();

    Integer getGlBalanceSnapshotDailyRetentionDays();

    int retrieveChannelLimitIncreaseCoolingHours();
}
