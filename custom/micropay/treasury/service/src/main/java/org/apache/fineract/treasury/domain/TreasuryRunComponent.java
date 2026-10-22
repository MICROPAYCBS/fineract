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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.treasury.liquidity.ComponentType;
import org.apache.fineract.treasury.liquidity.RatioPart;

/**
 * A component of a ratio of a run as computed: the source amount, what was excluded (pledged, beyond the maturity
 * cut-off), the haircut and weight applied, and the eligible amount that entered the ratio.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_run_component")
public class TreasuryRunComponent extends AbstractPersistableCustom<Long> {

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "ratio_code", nullable = false, length = 50)
    private String ratioCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "part", nullable = false, length = 20)
    private RatioPart part;

    @Column(name = "seq", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 20)
    private ComponentType componentType;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    /** What the component reads: GL account id, code prefix or range, asset class, or metric and days. */
    @Column(name = "reference", length = 200)
    private String reference;

    @Column(name = "source_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal sourceAmount;

    @Column(name = "excluded_pledged", nullable = false, precision = 19, scale = 6)
    private BigDecimal excludedPledged;

    @Column(name = "excluded_maturity", nullable = false, precision = 19, scale = 6)
    private BigDecimal excludedMaturity;

    @Column(name = "haircut", nullable = false, precision = 19, scale = 6)
    private BigDecimal haircut;

    @Column(name = "weight", nullable = false, precision = 19, scale = 6)
    private BigDecimal weight;

    @Column(name = "sign", nullable = false)
    private int sign;

    @Column(name = "eligible_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal eligibleAmount;
}
