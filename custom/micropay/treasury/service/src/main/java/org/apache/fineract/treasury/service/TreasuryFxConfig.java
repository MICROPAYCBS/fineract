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
package org.apache.fineract.treasury.service;

import java.util.Optional;
import org.apache.fineract.treasury.domain.TreasuryTenantSetting;
import org.apache.fineract.treasury.domain.TreasuryTenantSettingRepository;
import org.apache.fineract.treasury.fx.NopCapitalBasis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The tenant's base (reporting) currency, {@code fineract.module.treasury.base-currency} (default UGX), and the oldest
 * a closing rate may be for revaluation, {@code fineract.module.treasury.fx.max-rate-age-days} (default 3 days).
 */
/**
 * The base currency, the FX rate age limit and the NOP capital basis: the tenant's own setting
 * ({@code m_treasury_tenant_setting}) when set, else the deployment property. Read from the tenant's database on each
 * call, so nothing is shared between tenants.
 */
@Component
public class TreasuryFxConfig {

    private final String defaultBaseCurrency;
    private final int defaultMaxRateAgeDays;
    private final TreasuryTenantSettingRepository repository;

    @Autowired
    public TreasuryFxConfig(@Value("${fineract.module.treasury.base-currency:UGX}") final String baseCurrency,
            @Value("${fineract.module.treasury.fx.max-rate-age-days:3}") final int maxRateAgeDays,
            final TreasuryTenantSettingRepository repository) {
        this.defaultBaseCurrency = baseCurrency.trim().toUpperCase();
        this.defaultMaxRateAgeDays = maxRateAgeDays;
        this.repository = repository;
    }

    /** The deployment defaults only, without tenant settings. */
    public TreasuryFxConfig(final String baseCurrency, final int maxRateAgeDays) {
        this(baseCurrency, maxRateAgeDays, null);
    }

    public String baseCurrency() {
        return tenant().map(TreasuryTenantSetting::getBaseCurrency).orElse(this.defaultBaseCurrency);
    }

    public int maxRateAgeDays() {
        return tenant().map(TreasuryTenantSetting::getFxMaxRateAgeDays).orElse(this.defaultMaxRateAgeDays);
    }

    public NopCapitalBasis nopCapitalBasis() {
        return tenant().map(TreasuryTenantSetting::getNopCapitalBasis).orElse(NopCapitalBasis.PRECEDING_QUARTER);
    }

    public String defaultBaseCurrency() {
        return this.defaultBaseCurrency;
    }

    public int defaultMaxRateAgeDays() {
        return this.defaultMaxRateAgeDays;
    }

    private Optional<TreasuryTenantSetting> tenant() {
        return this.repository == null ? Optional.empty() : this.repository.findFirstByOrderByIdAsc();
    }
}
