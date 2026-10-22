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
import java.util.List;

/**
 * Settled asset holdings on a date with their measurement: amortised cost, fair value and its adjustment, OCI reserve,
 * IFRS 9 stage and loss allowance, and the nominal pledged and unencumbered. Amounts are as last posted (the accrual,
 * valuation and ECL jobs); pledges are as at the date.
 *
 * @param totals
 *            one row per currency
 */
public record TreasuryPortfolioData(LocalDate date, String currencyCode, List<Holding> holdings, List<Total> totals) {

    /**
     * @param amortisedCost
     *            clean amortised cost: principal plus premium less discount (bills: their carrying amount)
     * @param grossCarryingAmount
     *            amortised cost with accrued interest and deferred fees, the exposure for expected credit losses
     * @param carryingAmount
     *            what the balance sheet shows: gross carrying amount plus fair value adjustment, less the loss
     *            allowance for holdings at amortised cost
     * @param fairValue
     *            clean fair value of the last valuation; null for holdings not at fair value
     */
    public record Holding(Long dealId, String dealTypeCode, String instrumentKind, String counterpartyShortCode, String currencyCode,
            String instrumentId, String classification, LocalDate maturityDate, BigDecimal nominal, BigDecimal amortisedCost,
            BigDecimal accruedInterest, BigDecimal grossCarryingAmount, BigDecimal fairValue, LocalDate lastValuationDate,
            BigDecimal fairValueAdjustment, BigDecimal ociReserve, int eclStage, BigDecimal eclAllowance, BigDecimal carryingAmount,
            BigDecimal pledged, BigDecimal unencumbered) {
    }

    public record Total(String currencyCode, int holdings, BigDecimal nominal, BigDecimal amortisedCost, BigDecimal grossCarryingAmount,
            BigDecimal fairValueAdjustment, BigDecimal ociReserve, BigDecimal eclAllowance, BigDecimal carryingAmount, BigDecimal pledged,
            BigDecimal unencumbered) {
    }
}
