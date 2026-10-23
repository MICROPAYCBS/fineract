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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.treasury.fx.NopCapitalBasis;
import org.apache.fineract.treasury.setup.ClientType;
import org.apache.fineract.treasury.trust.EMoneySourceType;

/**
 * The tenant's own treasury settings, one row; a null value falls back to the deployment property
 * ({@code fineract.module.treasury.*}).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_tenant_setting")
public class TreasuryTenantSetting extends AbstractPersistableCustom<Long> {

    @Column(name = "base_currency", length = 3)
    private String baseCurrency;

    @Column(name = "fx_max_rate_age_days")
    private Integer fxMaxRateAgeDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "nop_capital_basis", length = 20)
    private NopCapitalBasis nopCapitalBasis;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", length = 20)
    private ClientType clientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "emoney_source", length = 20)
    private EMoneySourceType emoneySource;

    /** Savings product ids, comma-separated, whose balances are e-money of customers, agents and others. */
    @Column(name = "emoney_customer_products", length = 200)
    private String emoneyCustomerProducts;

    @Column(name = "emoney_agent_products", length = 200)
    private String emoneyAgentProducts;

    @Column(name = "emoney_other_products", length = 200)
    private String emoneyOtherProducts;

    /** The GL account bank interest on the trust accounts is credited to. */
    @Column(name = "trust_interest_gl_account_id")
    private Long trustInterestGlAccountId;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "last_modified_on_utc")
    private OffsetDateTime lastModifiedOnUtc;
}
