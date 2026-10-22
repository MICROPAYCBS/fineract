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
 * One accrual run of one deal: what it posted for the days from {@code fromDate} up to, not including, {@code toDate}.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_deal_accrual")
public class TreasuryDealAccrual extends AbstractPersistableCustom<Long> {

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "interest", nullable = false, precision = 19, scale = 6)
    private BigDecimal interest;

    @Column(name = "amortisation", nullable = false, precision = 19, scale = 6)
    private BigDecimal amortisation;

    @Column(name = "fee_amortisation", nullable = false, precision = 19, scale = 6)
    private BigDecimal feeAmortisation;

    @Column(name = "transaction_id", length = 50)
    private String transactionId;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;
}
