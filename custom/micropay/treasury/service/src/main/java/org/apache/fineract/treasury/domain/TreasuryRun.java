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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.treasury.liquidity.RunType;

/**
 * A stored compliance run: its type, as-of date, currency and scope (an office, or the institution), and the rule set
 * and assumption set versions it used, so a past result can be traced. A rerun for the same type, date, currency and
 * scope replaces it.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_run")
public class TreasuryRun extends AbstractPersistableCustom<Long> {

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 20)
    private RunType runType;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** Null for the whole institution. */
    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "rule_set_id", nullable = false)
    private Long ruleSetId;

    @Column(name = "rule_set_version", nullable = false)
    private int ruleSetVersion;

    @Column(name = "assumption_set_id")
    private Long assumptionSetId;

    @Column(name = "assumption_set_version")
    private Integer assumptionSetVersion;

    /** Flows after a bounded last bucket, left out of the ladder. */
    @Column(name = "beyond_horizon", nullable = false, precision = 19, scale = 6)
    private java.math.BigDecimal beyondHorizon;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** Warnings of the run (assumptions for unknown buckets, run-off above 100%). */
    @Column(name = "notes", length = 2000)
    private String notes;

    /** RATIOS: the ladder run it is computed from; RETURN: the ratios run. */
    @Column(name = "source_run_id")
    private Long sourceRunId;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_on_date")
    private LocalDate submittedOnDate;

    /**
     * RATIOS: the ratios, components and asset classes as they were configured; STRESS: the scenario and capacity; as
     * JSON.
     */
    @Column(name = "config_snapshot")
    private String configSnapshot;

    /** STRESS: the scenario version applied. */
    @Column(name = "scenario_id")
    private Long scenarioId;

    @Column(name = "scenario_code", length = 50)
    private String scenarioCode;

    @Column(name = "scenario_version")
    private Integer scenarioVersion;

    /** STRESS: days until the stressed position turns negative; null beyond the horizon. */
    @Column(name = "survival_days", precision = 19, scale = 6)
    private BigDecimal survivalDays;

    @Column(name = "survival_beyond_horizon", nullable = false)
    private boolean survivalBeyondHorizon;

    @Column(name = "baseline_survival_days", precision = 19, scale = 6)
    private BigDecimal baselineSurvivalDays;

    /**
     * REPRICING: GL total assets and liabilities in the currency and scope, and the core capital EAR was measured
     * against.
     */
    @Column(name = "total_assets", precision = 19, scale = 6)
    private BigDecimal totalAssets;

    @Column(name = "total_liabilities", precision = 19, scale = 6)
    private BigDecimal totalLiabilities;

    @Column(name = "core_capital", precision = 19, scale = 6)
    private BigDecimal coreCapital;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;

    /**
     * The scope, null-free, for the unique key on (type, date, currency, office, scenario): a run replaces the scope's
     * earlier one, so two runs of one scope made at the same time cannot both be saved.
     */
    @Column(name = "office_key", nullable = false)
    private long officeKey;

    @Column(name = "scenario_key", nullable = false, length = 50)
    private String scenarioKey = "";

    @PrePersist
    @PreUpdate
    void scopeKeys() {
        this.officeKey = this.officeId == null ? 0L : this.officeId;
        this.scenarioKey = this.scenarioCode == null ? "" : this.scenarioCode;
    }
}
