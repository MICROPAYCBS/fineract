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
 * A deal with its type, counterparty and settlement account, and, when retrieved one by one, its cash flows and
 * history.
 */
public record TreasuryDealData(Long id, Long dealTypeId, String dealTypeCode, String instrumentKind, String direction, Long counterpartyId,
        String counterpartyShortCode, Long settlementAccountId, String settlementAccountCode, Long officeId, String currencyCode,
        String externalReference, String isin, LocalDate tradeDate, LocalDate valueDate, LocalDate maturityDate, LocalDate issueDate,
        BigDecimal amount, BigDecimal rate, BigDecimal cleanPrice, String dayCount, int frequency, boolean amortising, BigDecimal fees,
        String classification, BigDecimal consideration, BigDecimal accruedInterestPurchased, BigDecimal premium, BigDecimal discount,
        BigDecimal exposureAmount, boolean limitExceeded, String status, String notes, Long capturedBy, Long approvedBy,
        LocalDate approvedOnDate, Long confirmedBy, LocalDate confirmedOnDate, String confirmationReference, Long settledBy,
        LocalDate settledOnDate, String settlementTransactionId, Long closedBy, LocalDate closedOnDate, String closeReason,
        Lifecycle lifecycle, Holding holding, List<Cashflow> cashflows, List<Event> events, List<Accrual> accruals, List<Pledge> pledges,
        List<Valuation> valuations, List<Ecl> ecl) {

    /**
     * After settlement: what has been accrued (interest for the days before {@code accruedToDate}), amortised and
     * settled, and how the deal ended.
     */
    public record Lifecycle(LocalDate accruedToDate, BigDecimal interestAccrued, BigDecimal amortisationAccrued, BigDecimal feeDeferred,
            BigDecimal feeAmortised, BigDecimal interestSettled, BigDecimal principalSettled, LocalDate maturedOnDate,
            LocalDate terminatedOnDate, BigDecimal terminationProceeds, BigDecimal breakCost, BigDecimal realisedResult,
            boolean rolloverPreApproved, Long rolledFromDealId, Long rolledToDealId, LocalDate rolloverWindowFrom,
            LocalDate rolloverWindowTo) {
    }

    /**
     * The deal as a holding, on the business date: fair value adjustment (bills and bonds at FVOCI or FVTPL), OCI
     * reserve (FVOCI: fair value adjustment plus loss allowance), IFRS 9 stage and loss allowance, and the nominal
     * pledged and unencumbered. Interest on a stage 3 deal still accrues on its gross carrying amount
     * ({@code stage3InterestOnGross}).
     */
    public record Holding(boolean revalued, BigDecimal fairValueAdjustment, BigDecimal lastFairValue, LocalDate lastValuationDate,
            LocalDate lastPriceDate, BigDecimal ociReserve, int eclStage, String eclStageReason, LocalDate eclStageDate,
            BigDecimal eclManualPd, BigDecimal eclManualLgd, BigDecimal eclManualAmount, String eclManualReason, BigDecimal eclAllowance,
            LocalDate eclCalculatedOnDate, boolean stage3InterestOnGross, BigDecimal pledged, BigDecimal unencumbered) {
    }

    public record Pledge(Long id, Long pledgeeCounterpartyId, String purpose, Long linkedDealId, BigDecimal amount, LocalDate startDate,
            LocalDate expectedEndDate, String status, String reason, Long pledgedBy, Long releasedBy, LocalDate releasedOnDate,
            String releaseReason) {
    }

    public record Valuation(int sequence, LocalDate valuationDate, LocalDate priceDate, BigDecimal cleanPrice, BigDecimal yield,
            BigDecimal fairValue, BigDecimal amortisedCost, BigDecimal adjustment, BigDecimal adjustmentChange, String transactionId) {
    }

    public record Ecl(int sequence, LocalDate calculationDate, int stage, String trigger, BigDecimal exposure, BigDecimal pd,
            BigDecimal lgd, String basis, BigDecimal ecl, BigDecimal eclChange, String transactionId) {
    }

    public record Accrual(int sequence, LocalDate fromDate, LocalDate toDate, BigDecimal interest, BigDecimal amortisation,
            BigDecimal feeAmortisation, String transactionId) {
    }

    public record Cashflow(Long id, int sequence, String flowType, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate,
            BigDecimal amount, BigDecimal principalOutstanding, String status, BigDecimal settledAmount, LocalDate settledOnDate,
            String transactionId) {
    }

    public record Event(String eventType, LocalDate businessDate, Long userId, String note, String transactionId) {
    }
}
