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
import org.apache.fineract.treasury.stress.FundingLineStatus;
import org.apache.fineract.treasury.stress.FundingSourceType;

/**
 * A contingency funding source of the plan: a line received, a repo window, an asset sale, a shareholder or a deposit
 * drive.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_funding_line")
public class TreasuryFundingLine extends AbstractPersistableCustom<Long> {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private FundingSourceType sourceType;

    @Column(name = "counterparty_id")
    private Long counterpartyId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "committed_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal committedAmount;

    @Column(name = "drawn_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal drawnAmount;

    /** Entered; committed less drawn when null. */
    @Column(name = "available_amount", precision = 19, scale = 6)
    private BigDecimal availableAmount;

    @Column(name = "days_to_access", nullable = false)
    private int daysToAccess;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "owner_name", length = 150)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FundingLineStatus status;

    /** Counts as counterbalancing capacity in stress. */
    @Column(name = "include_in_capacity", nullable = false)
    private boolean includeInCapacity;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "last_modified_on_utc")
    private OffsetDateTime lastModifiedOnUtc;
}
