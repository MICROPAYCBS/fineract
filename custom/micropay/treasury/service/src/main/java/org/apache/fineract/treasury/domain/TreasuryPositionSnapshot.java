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

/**
 * Day-end position of one treasury account. A rerun for the same business date overwrites the row.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_position_snapshot")
public class TreasuryPositionSnapshot extends AbstractPersistableCustom<Long> {

    @Column(name = "treasury_account_id", nullable = false)
    private Long treasuryAccountId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "gl_balance", nullable = false, precision = 19, scale = 6)
    private BigDecimal glBalance;

    @Column(name = "pending_in", nullable = false, precision = 19, scale = 6)
    private BigDecimal pendingIn;

    @Column(name = "pending_out", nullable = false, precision = 19, scale = 6)
    private BigDecimal pendingOut;

    @Enumerated(EnumType.STRING)
    @Column(name = "level_status", nullable = false, length = 20)
    private TreasuryLevelStatus levelStatus;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;
}
