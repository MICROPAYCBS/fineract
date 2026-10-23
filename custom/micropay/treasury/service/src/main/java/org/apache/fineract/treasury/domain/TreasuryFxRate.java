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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.treasury.fx.FxRateSource;
import org.apache.fineract.treasury.fx.FxRateStatus;
import org.apache.fineract.treasury.fx.FxRateType;

/**
 * An exchange rate: units of the base currency for one unit of the quote currency on a date, of a type and from a
 * source.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_fx_rate")
public class TreasuryFxRate extends AbstractPersistableCustom<Long> {

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "quote_currency", nullable = false, length = 3)
    private String quoteCurrency;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 10)
    private FxRateType rateType;

    @Column(name = "rate", nullable = false, precision = 24, scale = 10)
    private BigDecimal rate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private FxRateSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FxRateStatus status;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;

    /** The rate of the same pair, date, type and source that replaced it. */
    @Column(name = "superseded_by")
    private Long supersededBy;
}
