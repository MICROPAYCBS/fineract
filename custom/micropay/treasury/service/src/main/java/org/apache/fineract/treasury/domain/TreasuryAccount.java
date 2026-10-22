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
 * A place treasury cash sits: a bank or central bank account, a mobile money wallet, a vault or an agent float pool.
 * Each is linked to one asset GL account in one office and currency; its balance is always read from the GL, never kept
 * here.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_account")
public class TreasuryAccount extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private TreasuryAccountType accountType;

    @Column(name = "gl_account_id", nullable = false)
    private Long glAccountId;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** Plain text until counterparties are registered (phase 2). */
    @Column(name = "counterparty_name", length = 150)
    private String counterpartyName;

    @Column(name = "external_account_number", length = 100)
    private String externalAccountNumber;

    @Column(name = "min_balance", precision = 19, scale = 6)
    private BigDecimal minBalance;

    @Column(name = "max_balance", precision = 19, scale = 6)
    private BigDecimal maxBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TreasuryAccountStatus status;

    public boolean isActive() {
        return this.status == TreasuryAccountStatus.ACTIVE;
    }
}
