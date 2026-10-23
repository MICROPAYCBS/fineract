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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/** A TRUST treasury account: the institution holding it and its trustee. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_trust_account")
public class TreasuryTrustAccount extends AbstractPersistableCustom<Long> {

    @Column(name = "treasury_account_id", nullable = false, unique = true)
    private Long treasuryAccountId;

    @Column(name = "bank_counterparty_id")
    private Long bankCounterpartyId;

    @Column(name = "trustee_name", length = 150)
    private String trusteeName;

    @Column(name = "trust_deed_reference", length = 100)
    private String trustDeedReference;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "last_modified_on_utc")
    private OffsetDateTime lastModifiedOnUtc;
}
