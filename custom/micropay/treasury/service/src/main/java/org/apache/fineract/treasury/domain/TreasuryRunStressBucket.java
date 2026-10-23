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

/** A bucket of a STRESS run: baseline and stressed gaps, capacity and the position. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_run_stress_bucket")
public class TreasuryRunStressBucket extends AbstractPersistableCustom<Long> {

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "bucket_seq", nullable = false)
    private int bucketSeq;

    @Column(name = "bucket_code", nullable = false, length = 20)
    private String bucketCode;

    @Column(name = "from_day", nullable = false)
    private int fromDay;

    @Column(name = "to_day")
    private Integer toDay;

    @Column(name = "baseline_net", nullable = false, precision = 19, scale = 6)
    private BigDecimal baselineNet;

    @Column(name = "baseline_cumulative", nullable = false, precision = 19, scale = 6)
    private BigDecimal baselineCumulative;

    @Column(name = "stressed_net", nullable = false, precision = 19, scale = 6)
    private BigDecimal stressedNet;

    @Column(name = "stressed_cumulative", nullable = false, precision = 19, scale = 6)
    private BigDecimal stressedCumulative;

    @Column(name = "liquid_capacity", nullable = false, precision = 19, scale = 6)
    private BigDecimal liquidCapacity;

    @Column(name = "funding_capacity", nullable = false, precision = 19, scale = 6)
    private BigDecimal fundingCapacity;

    @Column(name = "baseline_capacity", nullable = false, precision = 19, scale = 6)
    private BigDecimal baselineCapacity;

    @Column(name = "stressed_position", nullable = false, precision = 19, scale = 6)
    private BigDecimal position;
}
