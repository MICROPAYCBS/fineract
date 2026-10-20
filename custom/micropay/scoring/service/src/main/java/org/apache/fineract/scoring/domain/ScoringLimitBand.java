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
package org.apache.fineract.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * The limit for one grade: {@code multiplier} times the policy's base characteristic, capped at {@code maxAmount}, or
 * {@code maxAmount} itself when there is no multiplier. A grade with no band gets no limit.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_scoring_limit_band")
public class ScoringLimitBand extends AbstractPersistableCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private ScoringLimitPolicy policy;

    @Column(name = "grade", nullable = false, length = 10)
    private String grade;

    @Column(name = "multiplier", precision = 19, scale = 6)
    private BigDecimal multiplier;

    @Column(name = "max_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal maxAmount;

    public static ScoringLimitBand create(final String grade, final BigDecimal multiplier, final BigDecimal maxAmount) {
        final ScoringLimitBand band = new ScoringLimitBand();
        band.setGrade(grade);
        band.setMultiplier(multiplier);
        band.setMaxAmount(maxAmount);
        return band;
    }
}
