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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The tenant's base (reporting) currency, {@code fineract.module.treasury.base-currency} (default UGX), and the oldest
 * a closing rate may be for revaluation, {@code fineract.module.treasury.fx.max-rate-age-days} (default 3 days).
 */
@Component
public class TreasuryFxConfig {

    private final String baseCurrency;
    private final int maxRateAgeDays;

    public TreasuryFxConfig(@Value("${fineract.module.treasury.base-currency:UGX}") final String baseCurrency,
            @Value("${fineract.module.treasury.fx.max-rate-age-days:3}") final int maxRateAgeDays) {
        this.baseCurrency = baseCurrency.trim().toUpperCase();
        this.maxRateAgeDays = maxRateAgeDays;
    }

    public String baseCurrency() {
        return this.baseCurrency;
    }

    public int maxRateAgeDays() {
        return this.maxRateAgeDays;
    }
}
