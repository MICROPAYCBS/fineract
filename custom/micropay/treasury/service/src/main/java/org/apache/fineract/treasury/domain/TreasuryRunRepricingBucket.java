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

/**
 * A bucket of a REPRICING run: RSA and RSL by rate type, the gap, the cumulative gap, as a percentage of total assets,
 * and RSA / RSL.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_run_repricing_bucket")
public class TreasuryRunRepricingBucket extends AbstractPersistableCustom<Long> {

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

    @Column(name = "rsa_fixed", nullable = false, precision = 19, scale = 6)
    private BigDecimal rsaFixed;

    @Column(name = "rsa_floating", nullable = false, precision = 19, scale = 6)
    private BigDecimal rsaFloating;

    @Column(name = "rsa_administered", nullable = false, precision = 19, scale = 6)
    private BigDecimal rsaAdministered;

    @Column(name = "rsl_fixed", nullable = false, precision = 19, scale = 6)
    private BigDecimal rslFixed;

    @Column(name = "rsl_floating", nullable = false, precision = 19, scale = 6)
    private BigDecimal rslFloating;

    @Column(name = "rsl_administered", nullable = false, precision = 19, scale = 6)
    private BigDecimal rslAdministered;

    @Column(name = "rsa", nullable = false, precision = 19, scale = 6)
    private BigDecimal rsa;

    @Column(name = "rsl", nullable = false, precision = 19, scale = 6)
    private BigDecimal rsl;

    @Column(name = "gap", nullable = false, precision = 19, scale = 6)
    private BigDecimal gap;

    @Column(name = "cumulative_gap", nullable = false, precision = 19, scale = 6)
    private BigDecimal cumulativeGap;

    /** Of total assets. */
    @Column(name = "gap_percent", precision = 19, scale = 6)
    private BigDecimal gapPercent;

    @Column(name = "cumulative_percent", precision = 19, scale = 6)
    private BigDecimal cumulativePercent;

    @Column(name = "rsa_rsl_ratio", precision = 19, scale = 6)
    private BigDecimal rsaRslRatio;
}
