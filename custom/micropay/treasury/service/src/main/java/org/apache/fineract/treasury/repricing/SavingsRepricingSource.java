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
package org.apache.fineract.treasury.repricing;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.liquidity.LadderContext;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Demand savings per product: ADMINISTERED, their balance spread over the buckets by the {@code REPRICING_DIST} rows of
 * the product (or the general ones); what the rows leave, or all of it without rows, in the first bucket.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class SavingsRepricingSource implements RepricingSource {

    static final String LINE = "DEMAND_SAVINGS";

    private static final String SQL = """
            SELECT a.product_id, p.name AS product_name, SUM(a.account_balance_derived) AS balance
            FROM m_savings_account a
            JOIN m_savings_product p ON p.id = a.product_id
            LEFT JOIN m_client c ON c.id = a.client_id
            LEFT JOIN m_group g ON g.id = a.group_id
            WHERE a.deposit_type_enum = 100 AND a.status_enum = 300 AND a.currency_code = :currency AND a.account_balance_derived > 0
              AND (:officeId IS NULL OR COALESCE(c.office_id, g.office_id) = :officeId)
            GROUP BY a.product_id, p.name
            ORDER BY a.product_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public LadderSourceCode code() {
        return LadderSourceCode.SAVINGS;
    }

    @Override
    public List<RepricingPosition> positions(final LadderContext context) {
        final List<RepricingPosition> positions = new ArrayList<>();
        this.jdbcTemplate.query(SQL, new MapSqlParameterSource().addValue("currency", context.currencyCode()).addValue("officeId",
                context.officeId(), Types.BIGINT), rs -> {
                    final BigDecimal balance = rs.getBigDecimal("balance");
                    positions.addAll(RepricingDistribution.spread(context, LadderSourceCode.SAVINGS, LINE, rs.getLong("product_id"),
                            rs.getString("product_name"), RepricingSide.LIABILITY, RateType.ADMINISTERED, RateType.ADMINISTERED, balance));
                });
        return positions;
    }
}
