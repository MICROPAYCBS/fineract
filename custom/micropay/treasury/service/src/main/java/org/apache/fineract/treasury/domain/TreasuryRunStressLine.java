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

/** A ladder line of a STRESS run before and after the shocks, signed. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_run_stress_line")
public class TreasuryRunStressLine extends AbstractPersistableCustom<Long> {

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "bucket_seq", nullable = false)
    private int bucketSeq;

    @Column(name = "bucket_code", nullable = false, length = 20)
    private String bucketCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private LadderSourceCode source;

    @Column(name = "line_code", nullable = false, length = 50)
    private String lineCode;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", length = 150)
    private String productName;

    @Column(name = "baseline_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal baselineAmount;

    @Column(name = "stressed_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal stressedAmount;

    /** The labels of the shocks that changed it. */
    @Column(name = "shocks", length = 1000)
    private String shocks;
}
