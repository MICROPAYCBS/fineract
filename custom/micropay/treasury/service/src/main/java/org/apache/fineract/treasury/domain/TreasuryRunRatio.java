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
import org.apache.fineract.treasury.liquidity.RatioDirection;
import org.apache.fineract.treasury.liquidity.RatioStatus;

/** A ratio of a RATIOS run as computed: its definition at the time, numerator, denominator, result and status. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_run_ratio")
public class TreasuryRunRatio extends AbstractPersistableCustom<Long> {

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "seq", nullable = false)
    private int sequenceNumber;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private RatioDirection direction;

    @Column(name = "numerator", nullable = false, precision = 19, scale = 6)
    private BigDecimal numerator;

    @Column(name = "denominator", nullable = false, precision = 19, scale = 6)
    private BigDecimal denominator;

    /** Percent; null when the denominator is zero. */
    @Column(name = "result", precision = 19, scale = 6)
    private BigDecimal result;

    @Column(name = "limit_value", nullable = false, precision = 19, scale = 6)
    private BigDecimal limitValue;

    @Column(name = "warning_value", precision = 19, scale = 6)
    private BigDecimal warningValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RatioStatus status;

    @Column(name = "in_return", nullable = false)
    private boolean inReturn;

    @Column(name = "numerator_label", length = 150)
    private String numeratorLabel;

    @Column(name = "denominator_label", length = 150)
    private String denominatorLabel;
}
