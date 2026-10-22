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
import org.apache.fineract.treasury.deal.PriceSource;

/**
 * A market price of an instrument on a date: a clean price per 100 of face (bonds, and bills when quoted that way) or a
 * yield in percent (bills). One price per instrument and date; a later entry for the same date replaces it.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_price")
public class TreasuryPrice extends AbstractPersistableCustom<Long> {

    /** The ISIN, or the deal's external reference for instruments without one. */
    @Column(name = "instrument_id", nullable = false, length = 100)
    private String instrumentId;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "clean_price", precision = 19, scale = 6)
    private BigDecimal cleanPrice;

    @Column(name = "yield_rate", precision = 19, scale = 6)
    private BigDecimal yieldRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private PriceSource source;

    @Column(name = "entered_by", nullable = false)
    private Long enteredBy;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;
}
