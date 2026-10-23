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
package org.apache.fineract.treasury.trust;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.domain.TreasuryEMoneyBalance;
import org.apache.fineract.treasury.domain.TreasuryEMoneyBalanceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** E-money balances entered or imported for the date, per category, for wallets kept on another platform. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class ImportedEMoneySource implements EMoneySource {

    private final TreasuryEMoneyBalanceRepository repository;

    @Override
    public EMoneySourceType type() {
        return EMoneySourceType.IMPORTED;
    }

    @Override
    public Map<EMoneyCategory, BigDecimal> outstanding(final LocalDate asOf, final String currencyCode) {
        final Map<EMoneyCategory, BigDecimal> outstanding = new EnumMap<>(EMoneyCategory.class);
        for (final TreasuryEMoneyBalance balance : this.repository.findByBalanceDateAndCurrencyCode(asOf, currencyCode)) {
            outstanding.put(balance.getCategory(), balance.getAmount());
        }
        return outstanding;
    }
}
