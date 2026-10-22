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
import org.apache.fineract.treasury.liquidity.AssumptionType;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;

/**
 * One behavioural assumption: a rate (percent) for a source, optionally for one product (savings or loan product, deal
 * type), one bucket, or a days band (days in arrears; for float demand the lookback days).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_assumption")
public class TreasuryAssumption extends AbstractPersistableCustom<Long> {

    @Column(name = "assumption_set_id", nullable = false)
    private Long assumptionSetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assumption_type", nullable = false, length = 30)
    private AssumptionType assumptionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private LadderSourceCode source;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "bucket_code", length = 20)
    private String bucketCode;

    @Column(name = "min_days")
    private Integer minDays;

    @Column(name = "max_days")
    private Integer maxDays;

    @Column(name = "rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal rate;
}
