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

/**
 * Names of the {@code c_configuration} entries introduced by Micropay. They complement
 * {@link org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants}.
 */
public final class MicropayGlobalConfigurationConstants {

    private MicropayGlobalConfigurationConstants() {}

    public static final String ENABLE_APPROVAL_WORKFLOWS = "enable-approval-workflows";
    public static final String ENABLE_CROSS_BRANCH_SERVICING = "enable-cross-branch-servicing";
    public static final String SESSION_IDLE_TIMEOUT_MINUTES = "session-idle-timeout-minutes";
    public static final String SESSION_IDLE_WARNING_SECONDS = "session-idle-warning-seconds";
    public static final String ENFORCE_SINGLE_SESSION = "enforce-single-session";
    public static final String ORGANISATION_DISPLAY_NAME = "organisation-display-name";
    public static final String PREVENT_CASHIER_OVERDRAW = "prevent-cashier-overdraw";
    public static final String REQUIRE_CASHIER_FOR_CASH_TRANSACTIONS = "require-cashier-for-cash-transactions";
    public static final String CAPTURE_LEGAL_TENDER_FOR_CASH_TRANSACTIONS = "capture-legal-tender-for-cash-transactions";
    public static final String STRUCTURED_ACCOUNT_NUMBER_FORMATS = "structured-account-number-formats";
    public static final String ENFORCE_STRUCTURED_GL_CODES = "enforce-structured-gl-codes";
    public static final String STRUCTURED_GL_CODE_LENGTH = "structured-gl-code-length";
    public static final String ALLOW_DIRECT_LOAN_REPAYMENTS = "allow-direct-loan-repayments";
    public static final String ENABLE_REQUIRE_DEPARTMENT_ON_MANUAL_JOURNAL_PL_LINES = "enable-require-department-on-manual-journal-pl-lines";
    public static final String ENABLE_ORGANIZATION_WIDE_AUDIT_VIEW = "enable-organization-wide-audit-view";
    public static final String GL_BALANCE_SNAPSHOT_DAILY_RETENTION_DAYS = "gl-balance-snapshot-daily-retention-days";
    public static final String CHANNEL_LIMIT_INCREASE_COOLING_HOURS = "channel-limit-increase-cooling-hours";
}
