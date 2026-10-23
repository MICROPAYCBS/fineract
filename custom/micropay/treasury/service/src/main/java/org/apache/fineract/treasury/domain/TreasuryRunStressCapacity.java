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
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/** An item of a STRESS run's counterbalancing capacity: an asset class or a funding line. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_run_stress_capacity")
public class TreasuryRunStressCapacity extends AbstractPersistableCustom<Long> {

    @Column(name = "run_id", nullable = false)
    private Long runId;

    /** ASSET_CLASS or FUNDING_LINE. */
    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "reference", nullable = false, length = 50)
    private String reference;

    @Column(name = "label", length = 150)
    private String label;

    @Column(name = "source_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal sourceAmount;

    @Column(name = "excluded_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal excludedAmount;

    @Column(name = "haircut", nullable = false, precision = 19, scale = 6)
    private BigDecimal haircut;

    @Column(name = "shock_haircut", nullable = false, precision = 19, scale = 6)
    private BigDecimal shockHaircut;

    @Column(name = "eligible_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal eligibleAmount;

    @Column(name = "days_to_access")
    private Integer daysToAccess;

    @Column(name = "frozen", nullable = false)
    private boolean frozen;

    @Column(name = "note", length = 500)
    private String note;
}
