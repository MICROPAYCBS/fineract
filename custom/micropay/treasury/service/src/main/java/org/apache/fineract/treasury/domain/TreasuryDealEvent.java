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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * One step in a deal's history.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_deal_event")
public class TreasuryDealEvent extends AbstractPersistableCustom<Long> {

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private TreasuryDealEventType eventType;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "transaction_id", length = 50)
    private String transactionId;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;
}
