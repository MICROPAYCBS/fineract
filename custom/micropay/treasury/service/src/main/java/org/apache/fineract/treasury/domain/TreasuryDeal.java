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
package org.apache.fineract.treasury.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.treasury.deal.DayCount;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.DealStatus;

/**
 * A treasury deal: a placement, fixed deposit, treasury bill, bond, borrowing or credit line drawdown with one
 * counterparty, settled through one treasury account.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_deal")
public class TreasuryDeal extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "deal_type_id", nullable = false)
    private Long dealTypeId;

    @Column(name = "counterparty_id", nullable = false)
    private Long counterpartyId;

    @Column(name = "settlement_account_id", nullable = false)
    private Long settlementAccountId;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "isin", length = 12)
    private String isin;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    /** Bonds only: when it falls after the regular coupon date, the first coupon is a short stub. */
    @Column(name = "issue_date")
    private LocalDate issueDate;

    /** Principal for placements, deposits and borrowings; face value for bills and bonds. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    /** Interest rate, discount yield or coupon rate, percent per year. */
    @Column(name = "rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal rate;

    /** Bonds: clean price per 100 of face. */
    @Column(name = "clean_price", precision = 19, scale = 6)
    private BigDecimal cleanPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_count", nullable = false, length = 20)
    private DayCount dayCount;

    @Column(name = "frequency", nullable = false)
    private int frequency;

    @Column(name = "amortising", nullable = false)
    private boolean amortising;

    @Column(name = "fees", nullable = false, precision = 19, scale = 6)
    private BigDecimal fees;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 20)
    private DealClassification classification;

    @Column(name = "consideration", nullable = false, precision = 19, scale = 6)
    private BigDecimal consideration;

    @Column(name = "accrued_interest_purchased", nullable = false, precision = 19, scale = 6)
    private BigDecimal accruedInterestPurchased;

    @Column(name = "premium", nullable = false, precision = 19, scale = 6)
    private BigDecimal premium;

    @Column(name = "discount", nullable = false, precision = 19, scale = 6)
    private BigDecimal discount;

    /** What the deal adds to the counterparty's exposure. */
    @Column(name = "exposure_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal exposureAmount;

    @Column(name = "limit_exceeded", nullable = false)
    private boolean limitExceeded;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DealStatus status;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "captured_by", nullable = false)
    private Long capturedBy;

    @Column(name = "last_edited_by")
    private Long lastEditedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_on_date")
    private LocalDate approvedOnDate;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_on_date")
    private LocalDate confirmedOnDate;

    @Column(name = "confirmation_reference", length = 100)
    private String confirmationReference;

    @Column(name = "settled_by")
    private Long settledBy;

    @Column(name = "settled_on_date")
    private LocalDate settledOnDate;

    @Column(name = "settlement_transaction_id", length = 50)
    private String settlementTransactionId;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_on_date")
    private LocalDate closedOnDate;

    /** Reason of a rejection or cancellation. */
    @Column(name = "close_reason", length = 500)
    private String closeReason;
}
