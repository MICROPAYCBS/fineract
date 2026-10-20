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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * A client's pre-approved limit under one policy, from one behavioural decision. An amount of zero is a real answer:
 * the client was scored and does not qualify.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_scoring_client_limit")
public class ScoringClientLimit extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "loan_product_id", nullable = false)
    private Long loanProductId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Column(name = "grade", length = 10)
    private String grade;

    @Column(name = "decision_id", nullable = false)
    private Long decisionId;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScoringLimitStatus status;

    public boolean isValidOn(final LocalDate date) {
        return this.status == ScoringLimitStatus.ACTIVE && !date.isBefore(this.validFrom) && !date.isAfter(this.validTo);
    }
}
