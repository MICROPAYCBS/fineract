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
import org.apache.fineract.treasury.limits.BreachStatus;
import org.apache.fineract.treasury.limits.LimitComparison;
import org.apache.fineract.treasury.limits.LimitLevel;
import org.apache.fineract.treasury.limits.LimitMetric;
import org.apache.fineract.treasury.limits.LimitScopeType;
import org.apache.fineract.treasury.limits.LimitUnit;

/**
 * A warning or hard breach of a limit by one subject, with the levels it was compared with, how often it was seen and
 * its handling.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_breach")
public class TreasuryBreach extends AbstractPersistableCustom<Long> {

    @Column(name = "limit_id", nullable = false)
    private Long limitId;

    @Column(name = "limit_code", nullable = false, length = 50)
    private String limitCode;

    @Column(name = "limit_version", nullable = false)
    private int limitVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 40)
    private LimitMetric metric;

    @Column(name = "metric_key", length = 50)
    private String metricKey;

    /** The subject. */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private LimitScopeType scopeType;

    @Column(name = "scope_id")
    private Long scopeId;

    @Column(name = "scope_label", length = 200)
    private String scopeLabel;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    /** Of the observation that raised it. */
    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    /** The last value seen. */
    @Column(name = "observed_value", precision = 19, scale = 6)
    private BigDecimal observedValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "level_hit", nullable = false, length = 10)
    private LimitLevel level;

    /** The level of the last observation; null once cleared. */
    @Enumerated(EnumType.STRING)
    @Column(name = "last_level", length = 10)
    private LimitLevel lastLevel;

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

    /** RUN, DEAL, DEAL_REQUEST, ACCOUNT, DEPOSITS or DEALS. */
    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    /** The last evaluation that saw it. */
    @Column(name = "evaluation_id")
    private Long evaluationId;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BreachStatus status;

    @Column(name = "first_seen_on", nullable = false)
    private LocalDate firstSeenOn;

    @Column(name = "last_seen_on", nullable = false)
    private LocalDate lastSeenOn;

    @Column(name = "occurrences", nullable = false)
    private int occurrences;

    @Column(name = "escalated_on")
    private LocalDate escalatedOn;

    /** Back within the levels; still to be resolved. */
    @Column(name = "cleared_on")
    private LocalDate clearedOn;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "acknowledged_on_utc")
    private OffsetDateTime acknowledgedOnUtc;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_on_date")
    private LocalDate resolvedOnDate;

    @Column(name = "resolved_on_utc")
    private OffsetDateTime resolvedOnUtc;

    @Column(name = "resolution_comment", length = 2000)
    private String resolutionComment;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;
}
