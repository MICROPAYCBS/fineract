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
import org.apache.fineract.treasury.liquidity.LadderMetric;
import org.apache.fineract.treasury.liquidity.RatioPart;

/**
 * One term of a ratio's numerator or denominator: GL account balances (one account, a GL code prefix, or a code range),
 * an asset class, or a ladder metric; added with its sign and weight (percent).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_ratio_component")
public class TreasuryRatioComponent extends AbstractPersistableCustom<Long> {

    @Column(name = "ratio_id", nullable = false)
    private Long ratioId;

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

    @Column(name = "gl_account_id")
    private Long glAccountId;

    @Column(name = "gl_code_prefix", length = 45)
    private String glCodePrefix;

    @Column(name = "gl_code_from", length = 45)
    private String glCodeFrom;

    @Column(name = "gl_code_to", length = 45)
    private String glCodeTo;

    @Column(name = "asset_class", length = 50)
    private String assetClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "ladder_metric", length = 30)
    private LadderMetric ladderMetric;

    @Column(name = "metric_days")
    private Integer metricDays;

    /** +1 or -1. */
    @Column(name = "sign", nullable = false)
    private int sign = 1;

    /** Percent of the amount that counts (for example NSFR stable funding factors); 100 by default. */
    @Column(name = "weight", nullable = false, precision = 19, scale = 6)
    private BigDecimal weight = BigDecimal.valueOf(100);
}
