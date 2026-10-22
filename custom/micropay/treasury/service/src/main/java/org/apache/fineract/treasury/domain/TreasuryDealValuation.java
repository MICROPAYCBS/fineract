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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * One revaluation of a holding at fair value: the clean fair value from a market price against the amortised cost on
 * the valuation date, and the change of the fair value adjustment posted.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_deal_valuation")
public class TreasuryDealValuation extends AbstractPersistableCustom<Long> {

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    /** Amortised cost and fair value are as at this date: through the day before it. */
    @Column(name = "valuation_date", nullable = false)
    private LocalDate valuationDate;

    @Column(name = "price_date")
    private LocalDate priceDate;

    @Column(name = "clean_price", precision = 19, scale = 6)
    private BigDecimal cleanPrice;

    @Column(name = "yield_rate", precision = 19, scale = 6)
    private BigDecimal yieldRate;

    @Column(name = "fair_value", nullable = false, precision = 19, scale = 6)
    private BigDecimal fairValue;

    @Column(name = "amortised_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal amortisedCost;

    /** Fair value less amortised cost: the adjustment the GL should hold after this valuation. */
    @Column(name = "adjustment", nullable = false, precision = 19, scale = 6)
    private BigDecimal adjustment;

    @Column(name = "adjustment_change", nullable = false, precision = 19, scale = 6)
    private BigDecimal adjustmentChange;

    @Column(name = "transaction_id", length = 50)
    private String transactionId;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;
}
