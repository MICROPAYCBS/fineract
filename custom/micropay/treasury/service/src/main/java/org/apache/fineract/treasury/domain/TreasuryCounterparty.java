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
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * A bank, DFI, central bank, mobile network operator or government issuer treasury deals with, and its exposure limit.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_counterparty")
public class TreasuryCounterparty extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "short_code", nullable = false, length = 30, unique = true)
    private String shortCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "counterparty_type", nullable = false, length = 30)
    private TreasuryCounterpartyType counterpartyType;

    /** Free text, for example "AA- (GCR)". */
    @Column(name = "rating", length = 50)
    private String rating;

    /** Optional value of code {@code TreasuryCounterpartyRating}. */
    @Column(name = "rating_code_value_id")
    private Long ratingCodeValueId;

    /** Largest exposure allowed, in {@link #limitCurrencyCode}; no limit when {@code null}. */
    @Column(name = "exposure_limit", precision = 19, scale = 6)
    private BigDecimal exposureLimit;

    @Column(name = "limit_currency_code", length = 3)
    private String limitCurrencyCode;

    /** Utilisation, percent of the limit, from which deals carry a warning. */
    @Column(name = "warning_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal warningPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TreasuryCounterpartyStatus status;

    public boolean isActive() {
        return this.status == TreasuryCounterpartyStatus.ACTIVE;
    }
}
