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
        Lifecycle lifecycle, List<Cashflow> cashflows, List<Event> events, List<Accrual> accruals) {

    /**
     * After settlement: what has been accrued (interest for the days before {@code accruedToDate}), amortised and
     * settled, and how the deal ended.
     */
    public record Lifecycle(LocalDate accruedToDate, BigDecimal interestAccrued, BigDecimal amortisationAccrued, BigDecimal feeDeferred,
            BigDecimal feeAmortised, BigDecimal interestSettled, BigDecimal principalSettled, LocalDate maturedOnDate,
            LocalDate terminatedOnDate, BigDecimal terminationProceeds, BigDecimal breakCost, BigDecimal realisedResult,
            boolean rolloverPreApproved, Long rolledFromDealId, Long rolledToDealId) {
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
