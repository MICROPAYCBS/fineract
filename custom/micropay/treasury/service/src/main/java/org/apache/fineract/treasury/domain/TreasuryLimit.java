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
import org.apache.fineract.treasury.limits.GapBasis;
import org.apache.fineract.treasury.limits.GapType;
import org.apache.fineract.treasury.limits.LimitComparison;
import org.apache.fineract.treasury.limits.LimitMetric;
import org.apache.fineract.treasury.limits.LimitScopeType;
import org.apache.fineract.treasury.limits.LimitStatus;
import org.apache.fineract.treasury.limits.LimitUnit;

/**
 * A limit: what it measures, what it covers, its levels and who is notified of a breach. Each change is a new version,
 * kept in {@link TreasuryLimitHistory}.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_limit")
public class TreasuryLimit extends AbstractPersistableCustom<Long> {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 40)
    private LimitMetric metric;

    /** The ratio code (RATIO) or the bucket code (BUCKET_GAP). */
    @Column(name = "metric_key", length = 50)
    private String metricKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "gap_type", length = 20)
    private GapType gapType;

    /** The basis of a gap in percent. */
    @Enumerated(EnumType.STRING)
    @Column(name = "gap_basis", length = 30)
    private GapBasis gapBasis;

    /** How many of the largest depositors or funders a concentration adds up. */
    @Column(name = "top_n")
    private Integer topN;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private LimitScopeType scopeType;

    /** The office, counterparty or account. */
    @Column(name = "scope_id")
    private Long scopeId;

    /** Every currency when null; needed for amounts. */
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison", length = 10)
    private LimitComparison comparison;

    @Column(name = "warning_level", precision = 19, scale = 6)
    private BigDecimal warningLevel;

    @Column(name = "hard_level", precision = 19, scale = 6)
    private BigDecimal hardLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 10)
    private LimitUnit unit;

    /** The levels of what is measured: the ratio's, the counterparty's, the account's. */
    @Column(name = "use_own_levels", nullable = false)
    private boolean useOwnLevels;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LimitStatus status;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** The role whose users are notified. */
    @Column(name = "notify_role_id")
    private Long notifyRoleId;

    /** Starts at 1; each update adds one. */
    @Column(name = "version", nullable = false)
    private int limitVersion;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "last_modified_on_utc")
    private OffsetDateTime lastModifiedOnUtc;

    @Column(name = "activated_by")
    private Long activatedBy;

    @Column(name = "activated_on_date")
    private LocalDate activatedOnDate;
}
