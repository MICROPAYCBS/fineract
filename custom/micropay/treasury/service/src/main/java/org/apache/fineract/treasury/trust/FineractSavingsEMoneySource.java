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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.service.TreasuryTenantSettingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * E-money held as Fineract savings: the balances at the end of the date (the running balance of each account's last
 * transaction on or before it) of the savings products the tenant setting lists as e-money of customers, agents and
 * others. Read-only.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class FineractSavingsEMoneySource implements EMoneySource {

    static final String SQL = """
            SELECT a.product_id, SUM(COALESCE((SELECT t.running_balance_derived FROM m_savings_account_transaction t
                                               WHERE t.savings_account_id = a.id AND t.is_reversed = false AND t.transaction_date <= :asOf
                                               ORDER BY t.transaction_date DESC, t.id DESC LIMIT 1), 0)) AS balance
            FROM m_savings_account a
            WHERE a.product_id IN (:products) AND a.currency_code = :currency
            GROUP BY a.product_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TreasuryTenantSettingService settings;

    @Override
    public EMoneySourceType type() {
        return EMoneySourceType.FINERACT_SAVINGS;
    }

    @Override
    public Map<EMoneyCategory, BigDecimal> outstanding(final LocalDate asOf, final String currencyCode) {
        final Map<EMoneyCategory, List<Long>> products = TreasuryTenantSettingService.emoneyProducts(this.settings.setting());
        final Map<Long, EMoneyCategory> categoryOf = new HashMap<>();
        products.forEach((category, ids) -> ids.forEach(id -> categoryOf.put(id, category)));
        final Map<EMoneyCategory, BigDecimal> outstanding = new EnumMap<>(EMoneyCategory.class);
        if (categoryOf.isEmpty()) {
            return outstanding;
        }
        products.forEach((category, ids) -> {
            if (!ids.isEmpty()) {
                outstanding.put(category, BigDecimal.ZERO);
            }
        });
        this.jdbcTemplate.query(SQL, new MapSqlParameterSource("asOf", asOf).addValue("currency", currencyCode).addValue("products",
                List.copyOf(categoryOf.keySet())), rs -> {
                    outstanding.merge(categoryOf.get(rs.getLong("product_id")), rs.getBigDecimal("balance"), BigDecimal::add);
                });
        return outstanding;
    }
}
