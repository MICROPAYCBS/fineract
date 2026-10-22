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

/**
 * A line of a run: one source and line code (and product) in one bucket, with its contractual and behavioural amounts
 * (signed, inflows positive) and their total as inflow or outflow. Each bucket also has a TOTAL line with the bucket's
 * net and cumulative gap.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_run_line")
public class TreasuryRunLine extends AbstractPersistableCustom<Long> {

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "bucket_seq", nullable = false)
    private int bucketSeq;

    @Column(name = "bucket_code", nullable = false, length = 20)
    private String bucketCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private LadderSourceCode source;

    @Column(name = "line_code", nullable = false, length = 50)
    private String lineCode;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", length = 150)
    private String productName;

    @Column(name = "inflow", nullable = false, precision = 19, scale = 6)
    private BigDecimal inflow;

    @Column(name = "outflow", nullable = false, precision = 19, scale = 6)
    private BigDecimal outflow;

    @Column(name = "contractual", nullable = false, precision = 19, scale = 6)
    private BigDecimal contractual;

    @Column(name = "behavioural", nullable = false, precision = 19, scale = 6)
    private BigDecimal behavioural;

    /** TOTAL lines: the bucket's net flow. */
    @Column(name = "net", precision = 19, scale = 6)
    private BigDecimal net;

    /** TOTAL lines: the net flows of this bucket and every earlier one. */
    @Column(name = "cumulative_gap", precision = 19, scale = 6)
    private BigDecimal cumulativeGap;
}
