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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.treasury.limits.LimitLevel;
import org.apache.fineract.treasury.limits.LimitMetric;
import org.apache.fineract.treasury.limits.LimitStatus;
import org.apache.fineract.treasury.stress.CfpStage;
import org.apache.fineract.treasury.stress.TriggerCondition;

/** A contingency funding plan trigger and the stage it starts. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_cfp_trigger")
public class TreasuryCfpTrigger extends AbstractPersistableCustom<Long> {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 20)
    private TriggerCondition conditionType;

    @Column(name = "limit_id")
    private Long limitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", length = 40)
    private LimitMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(name = "level_hit", length = 10)
    private LimitLevel level;

    @Column(name = "survival_days", precision = 19, scale = 6)
    private BigDecimal survivalDays;

    @Column(name = "scenario_code", length = 50)
    private String scenarioCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 10)
    private CfpStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LimitStatus status;

    @Column(name = "notify_role_id")
    private Long notifyRoleId;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "last_modified_on_utc")
    private OffsetDateTime lastModifiedOnUtc;
}
