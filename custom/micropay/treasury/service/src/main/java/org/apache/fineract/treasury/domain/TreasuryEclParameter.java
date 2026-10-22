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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * Probability of default and loss given default, in percent, for a counterparty rating (or {@code DEFAULT}) and an IFRS
 * 9 stage. Stage 1 uses the 12-month PD, stages 2 and 3 the lifetime PD.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_ecl_parameter")
public class TreasuryEclParameter extends AbstractPersistableCustom<Long> {

    public static final String DEFAULT_RATING = "DEFAULT";

    @Column(name = "rating_code", nullable = false, length = 50)
    private String ratingCode;

    @Column(name = "stage", nullable = false)
    private int stage;

    @Column(name = "pd_12_month", nullable = false, precision = 19, scale = 6)
    private BigDecimal pd12Month;

    @Column(name = "pd_lifetime", nullable = false, precision = 19, scale = 6)
    private BigDecimal pdLifetime;

    @Column(name = "lgd", nullable = false, precision = 19, scale = 6)
    private BigDecimal lgd;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_on_utc", nullable = false)
    private OffsetDateTime updatedOnUtc;
}
