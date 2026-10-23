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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.treasury.stress.ActivationStatus;
import org.apache.fineract.treasury.stress.CfpStage;

/** A trigger that fired: from a breach, a stress run or by hand, with its stage and actions. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_cfp_activation")
public class TreasuryCfpActivation extends AbstractPersistableCustom<Long> {

    @Column(name = "trigger_id", nullable = false)
    private Long triggerId;

    @Column(name = "trigger_code", nullable = false, length = 50)
    private String triggerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 10)
    private CfpStage stage;

    /** BREACH, RUN or MANUAL. */
    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "scenario_code", length = 50)
    private String scenarioCode;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "occurrences", nullable = false)
    private int occurrences;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActivationStatus status;

    @Column(name = "opened_by")
    private Long openedBy;

    @Column(name = "opened_on_utc", nullable = false)
    private OffsetDateTime openedOnUtc;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_on_utc")
    private OffsetDateTime closedOnUtc;

    @Column(name = "close_comment", length = 2000)
    private String closeComment;
}
