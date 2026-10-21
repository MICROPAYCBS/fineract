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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Maps a score range [minScore, maxScore] to a grade, the expected probability of default for that grade and the
 * default outcome (the cut-off).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_scoring_grade_band")
public class ScorecardGradeBand extends AbstractPersistableCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scorecard_id", nullable = false)
    private Scorecard scorecard;

    @Column(name = "grade", nullable = false, length = 10)
    private String grade;

    @Column(name = "min_score", nullable = false, precision = 19, scale = 6)
    private BigDecimal minScore;

    @Column(name = "max_score", nullable = false, precision = 19, scale = 6)
    private BigDecimal maxScore;

    @Column(name = "expected_pd", precision = 9, scale = 6)
    private BigDecimal expectedPd;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private ScoringOutcome outcome;

    public static ScorecardGradeBand create(final String grade, final BigDecimal minScore, final BigDecimal maxScore,
            final BigDecimal expectedPd, final ScoringOutcome outcome) {
        final ScorecardGradeBand band = new ScorecardGradeBand();
        band.setGrade(grade);
        band.setMinScore(minScore);
        band.setMaxScore(maxScore);
        band.setExpectedPd(expectedPd);
        band.setOutcome(outcome);
        return band;
    }
}
