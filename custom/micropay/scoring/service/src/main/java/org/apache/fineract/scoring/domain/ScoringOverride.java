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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * A person changing the outcome of a scoring decision, with a reason from the {@code ScoringOverrideReason} code. The
 * latest override of a loan applies until the loan is scored again on submission or on request; the re-scoring that
 * runs at approval keeps it.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_scoring_override")
public class ScoringOverride extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "decision_id", nullable = false)
    private Long decisionId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_outcome", nullable = false, length = 20)
    private ScoringOutcome fromOutcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_outcome", nullable = false, length = 20)
    private ScoringOutcome toOutcome;

    @Column(name = "reason_cv_id", nullable = false)
    private Long reasonCodeValueId;

    @Column(name = "comment", length = 1000)
    private String comment;
}
