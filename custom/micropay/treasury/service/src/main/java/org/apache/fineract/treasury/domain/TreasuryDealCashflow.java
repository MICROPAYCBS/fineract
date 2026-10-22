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
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.treasury.deal.CashflowStatus;
import org.apache.fineract.treasury.deal.DealCalculator.FlowType;

/**
 * An expected cash flow of a deal: received for asset deals, paid for liability deals.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_deal_cashflow")
public class TreasuryDealCashflow extends AbstractPersistableCustom<Long> {

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false, length = 20)
    private FlowType flowType;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Column(name = "principal_outstanding", nullable = false, precision = 19, scale = 6)
    private BigDecimal principalOutstanding;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CashflowStatus status = CashflowStatus.EXPECTED;

    @Column(name = "settled_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal settledAmount = BigDecimal.ZERO;

    @Column(name = "settled_on_date")
    private LocalDate settledOnDate;

    @Column(name = "settlement_count", nullable = false)
    private int settlementCount;

    @Column(name = "transaction_id", length = 50)
    private String transactionId;

    public BigDecimal remaining() {
        return this.amount.subtract(this.settledAmount);
    }
}
