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
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.stress.FundingSourceType;
import org.apache.fineract.treasury.stress.ShockSide;
import org.apache.fineract.treasury.stress.ShockType;

/** A shock of a scenario version, applied in its order. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_scenario_shock")
public class TreasuryScenarioShock extends AbstractPersistableCustom<Long> {

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    @Column(name = "seq", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "shock_type", nullable = false, length = 30)
    private ShockType shockType;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20)
    private LadderSourceCode source;

    @Column(name = "line_code", length = 50)
    private String lineCode;

    @Column(name = "product_id")
    private Long productId;

    /** One bucket; every bucket when null (the first for an added outflow). */
    @Column(name = "bucket_code", length = 20)
    private String bucketCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", length = 10)
    private ShockSide side;

    /** Percent. */
    @Column(name = "factor", precision = 19, scale = 6)
    private BigDecimal factor;

    @Column(name = "asset_class", length = 50)
    private String assetClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "funding_source_type", length = 30)
    private FundingSourceType fundingSourceType;

    @Column(name = "funding_line_id")
    private Long fundingLineId;
}
