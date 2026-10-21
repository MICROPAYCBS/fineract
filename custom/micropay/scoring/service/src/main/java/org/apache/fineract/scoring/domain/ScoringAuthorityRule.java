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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * One row of the approval authority matrix: a condition under which a workflow stage is required. A stage with no rules
 * always applies; a stage with rules applies when at least one of them matches. Every condition left empty matches
 * anything, so a rule with only {@code minAmount} set requires the stage from that amount up.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_scoring_authority_rule")
public class ScoringAuthorityRule extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "task_permission_code", nullable = false, length = 100)
    private String taskPermissionCode;

    @Column(name = "stage_code", nullable = false, length = 100)
    private String stageCode;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "loan_product_id")
    private Long loanProductId;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "min_amount", precision = 19, scale = 6)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 6)
    private BigDecimal maxAmount;

    @Column(name = "min_exposure", precision = 19, scale = 6)
    private BigDecimal minExposure;

    @Column(name = "max_exposure", precision = 19, scale = 6)
    private BigDecimal maxExposure;

    /** Comma-separated grades, for example {@code D,E}. */
    @Column(name = "grades", length = 100)
    private String grades;

    /** Comma-separated scoring outcomes, for example {@code REFER,DECLINE}. */
    @Column(name = "outcomes", length = 100)
    private String outcomes;

    /** When set, matches only loans whose scoring decision was (true) or was not (false) overridden. */
    @Column(name = "overridden")
    private Boolean overridden;
}
