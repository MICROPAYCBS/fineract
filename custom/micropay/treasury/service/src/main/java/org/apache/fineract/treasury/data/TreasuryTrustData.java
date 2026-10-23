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
package org.apache.fineract.treasury.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * The e-money trust: accounts, holdings, imported balances, the reconciliation statement, the asset breakdown,
 * interest.
 */
public final class TreasuryTrustData {

    private TreasuryTrustData() {}

    public record Account(Long treasuryAccountId, String code, String name, String currencyCode, Long officeId, Long glAccountId,
            Long bankCounterpartyId, String bankName, String trusteeName, String trustDeedReference, BigDecimal glBalance) {
    }

    public record Holding(Long dealId, Long trustAccountId, String instrumentKind, String assetType, Long counterpartyId,
            String counterpartyName, String currencyCode, BigDecimal amount, String status) {
    }

    public record EMoneyBalance(Long id, LocalDate balanceDate, String currencyCode, String category, BigDecimal amount, String note) {
    }

    public record Asset(String assetType, Long institutionId, String institutionName, Long trustAccountId, Long dealId, String label,
            BigDecimal amount, BigDecimal eligible, BigDecimal encumbered) {
    }

    public record Institution(Long institutionId, String institutionName, BigDecimal amount, BigDecimal percentOfEmoney) {
    }

    /** The daily reconciliation statement of e-money against the trust (NPS Regulations 2021 reg 22(5)). */
    public record Statement(Long runId, LocalDate asOfDate, String currencyCode, String status, String emoneySource,
            Map<String, BigDecimal> emoney, BigDecimal emoneyTotal, BigDecimal trustTotal, BigDecimal eligibleTotal,
            BigDecimal encumberedTotal, BigDecimal ineligibleTotal, BigDecimal difference, BigDecimal shortfall, BigDecimal coveragePercent,
            boolean concentrationApplies, BigDecimal concentrationThreshold, BigDecimal maxConcentrationPercent,
            List<Institution> institutions, List<Asset> assets, Long createdBy, OffsetDateTime createdOnUtc, Long submittedBy,
            LocalDate submittedOnDate) {
    }

    public record BreakdownRow(Long institutionId, String institutionName, String assetType, BigDecimal amount, BigDecimal eligible) {
    }

    /** The trust balances in liquid assets by asset type and institution (reg 22(6)). */
    public record Breakdown(LocalDate asOfDate, String currencyCode, Long runId, Map<String, BigDecimal> byAssetType,
            List<BreakdownRow> rows) {
    }

    public record InterestRow(LocalDate interestDate, String source, Long dealId, Long glAccountId, BigDecimal amount) {
    }

    /** Interest earned on the trust over a period (reg 14(2), 14(7)). */
    public record Interest(LocalDate fromDate, LocalDate toDate, String currencyCode, BigDecimal accrued, Map<String, BigDecimal> bySource,
            BigDecimal averageTrustBalance, BigDecimal annualRatePercent, int days, List<InterestRow> rows, String note) {
    }
}
