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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.treasury.deal.PledgePurpose;

/**
 * Part of a holding (by nominal: principal, or face value for bills and bonds) pledged to a counterparty. A memo: it
 * posts nothing to the GL, and takes the pledged nominal out of what counts as unencumbered.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_pledge")
public class TreasuryPledge extends AbstractPersistableCustom<Long> {

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    @Column(name = "pledgee_counterparty_id", nullable = false)
    private Long pledgeeCounterpartyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private PledgePurpose purpose;

    /** The borrowing the pledge secures, when there is one. */
    @Column(name = "linked_deal_id")
    private Long linkedDealId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "expected_end_date")
    private LocalDate expectedEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TreasuryPledgeStatus status;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "pledged_by", nullable = false)
    private Long pledgedBy;

    @Column(name = "released_by")
    private Long releasedBy;

    /** The pledge counts up to, not including, this date. */
    @Column(name = "released_on_date")
    private LocalDate releasedOnDate;

    @Column(name = "release_reason", length = 500)
    private String releaseReason;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;

    /** Whether the pledge encumbers the holding on the date. */
    public boolean activeOn(final LocalDate date) {
        return !this.startDate.isAfter(date) && (this.releasedOnDate == null || this.releasedOnDate.isAfter(date));
    }
}
