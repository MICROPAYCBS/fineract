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
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/** One evaluation of the limits: when, for what scope, what triggered it and what it found; its lines are kept. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_limit_evaluation")
public class TreasuryLimitEvaluation extends AbstractPersistableCustom<Long> {

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "office_id")
    private Long officeId;

    /** RUN, JOB, MANUAL or DEAL. */
    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    @Column(name = "source_run_id")
    private Long sourceRunId;

    @Column(name = "source_deal_id")
    private Long sourceDealId;

    @Column(name = "limits_evaluated", nullable = false)
    private int limitsEvaluated;

    @Column(name = "breaches_created", nullable = false)
    private int breachesCreated;

    @Column(name = "breaches_escalated", nullable = false)
    private int breachesEscalated;

    @Column(name = "breaches_repeated", nullable = false)
    private int breachesRepeated;

    @Column(name = "breaches_cleared", nullable = false)
    private int breachesCleared;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;
}
