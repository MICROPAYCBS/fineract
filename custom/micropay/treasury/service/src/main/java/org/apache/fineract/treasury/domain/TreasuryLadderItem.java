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
import org.apache.fineract.treasury.liquidity.LadderItemDirection;

/**
 * A configured cash flow of the ladder (salaries, rent, tax): once on {@code oneOffDate}, or every month on
 * {@code dayOfMonth} (the month's last day when shorter) within the active window.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_ladder_item")
public class TreasuryLadderItem extends AbstractPersistableCustom<Long> {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 20)
    private LadderItemDirection direction;

    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** Null for an institution-wide item. */
    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "one_off_date")
    private LocalDate oneOffDate;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Column(name = "active_from", nullable = false)
    private LocalDate activeFrom;

    @Column(name = "active_to")
    private LocalDate activeTo;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "last_edited_by")
    private Long lastEditedBy;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;
}
