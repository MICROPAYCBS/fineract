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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.treasury.limits.BreachAction;
import org.apache.fineract.treasury.limits.LimitComparison;
import org.apache.fineract.treasury.limits.LimitLevel;
import org.apache.fineract.treasury.limits.LimitMetric;
import org.apache.fineract.treasury.limits.LimitScopeType;
import org.apache.fineract.treasury.limits.LimitUnit;

/** What one evaluation observed for a limit and subject, the levels used and what it did to the breach log. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_limit_evaluation_line")
public class TreasuryLimitEvaluationLine extends AbstractPersistableCustom<Long> {

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    @Column(name = "seq", nullable = false)
    private int sequenceNumber;

    @Column(name = "limit_id", nullable = false)
    private Long limitId;

    @Column(name = "limit_code", nullable = false, length = 50)
    private String limitCode;

    @Column(name = "limit_version", nullable = false)
    private int limitVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 40)
    private LimitMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private LimitScopeType scopeType;

    @Column(name = "scope_id")
    private Long scopeId;

    @Column(name = "scope_label", length = 200)
    private String scopeLabel;

    @Column(name = "observed_value", precision = 19, scale = 6)
    private BigDecimal observedValue;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "level_hit", length = 10)
    private LimitLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private BreachAction action;

    @Column(name = "breach_id")
    private Long breachId;

    @Column(name = "source_type", length = 20)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "detail", length = 1000)
    private String detail;
}
