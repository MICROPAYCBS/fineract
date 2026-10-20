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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * Append-only record of one scoring run: the scorecard version used, the score, grade, PD and outcome, the reason
 * codes, every rule result and a snapshot of the inputs as they were at decision time.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_scoring_decision")
public class ScoringDecision extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "loan_id")
    private Long loanId;

    @Column(name = "loan_product_id")
    private Long loanProductId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_trigger", nullable = false, length = 20)
    private ScoringTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_mode", nullable = false, length = 20)
    private ScoringMode mode;

    @Column(name = "scorecard_id", nullable = false)
    private Long scorecardId;

    @Column(name = "scorecard_code", nullable = false, length = 100)
    private String scorecardCode;

    @Column(name = "scorecard_version", nullable = false)
    private Integer scorecardVersion;

    @Column(name = "score", nullable = false, precision = 19, scale = 6)
    private BigDecimal score;

    @Column(name = "grade", length = 10)
    private String grade;

    @Column(name = "expected_pd", precision = 9, scale = 6)
    private BigDecimal expectedPd;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private ScoringOutcome outcome;

    @Column(name = "reason_codes", length = 1000)
    private String reasonCodes;

    @Column(name = "rule_results")
    private String ruleResults;

    @Column(name = "points_breakdown")
    private String pointsBreakdown;

    @Column(name = "inputs_snapshot")
    private String inputsSnapshot;
}
