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
import org.apache.fineract.treasury.liquidity.ConfigStatus;

/**
 * A versioned set of behavioural assumptions: run-off, core share, prepayment, rollover, arrears haircuts, drawdown,
 * float demand.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_assumption_set")
public class TreasuryAssumptionSet extends AbstractPersistableCustom<Long> {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConfigStatus status;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "last_edited_by")
    private Long lastEditedBy;

    @Column(name = "activated_by")
    private Long activatedBy;

    @Column(name = "activated_on_date")
    private LocalDate activatedOnDate;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;
}
