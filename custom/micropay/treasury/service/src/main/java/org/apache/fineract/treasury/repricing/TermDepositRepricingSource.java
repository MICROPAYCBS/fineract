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

import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.liquidity.LadderContext;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.liquidity.StreamingQueries;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Active fixed and recurring deposits: their balance, FIXED, repricing at maturity. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TermDepositRepricingSource implements RepricingSource {

    private static final String SQL = """
            SELECT a.product_id, p.name AS product_name, a.deposit_type_enum, t.maturity_date, a.account_balance_derived AS balance
            FROM m_savings_account a
            JOIN m_savings_product p ON p.id = a.product_id
            JOIN m_deposit_account_term_and_preclosure t ON t.savings_account_id = a.id
            LEFT JOIN m_client c ON c.id = a.client_id
            LEFT JOIN m_group g ON g.id = a.group_id
            WHERE a.deposit_type_enum IN (200, 300) AND a.status_enum = 300 AND a.currency_code = :currency AND a.account_balance_derived > 0
              AND (:officeId IS NULL OR COALESCE(c.office_id, g.office_id) = :officeId)
            ORDER BY a.id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public LadderSourceCode code() {
        return LadderSourceCode.TERM_DEPOSITS;
    }

    @Override
    public List<RepricingPosition> positions(final LadderContext context) {
        return StreamingQueries.query(this.jdbcTemplate, SQL,
                new MapSqlParameterSource().addValue("currency", context.currencyCode()).addValue("officeId", context.officeId(),
                        Types.BIGINT),
                (rs, i) -> new RepricingPosition(LadderSourceCode.TERM_DEPOSITS,
                        rs.getInt("deposit_type_enum") == 300 ? "RECURRING_DEPOSIT" : "FIXED_DEPOSIT", rs.getLong("product_id"),
                        rs.getString("product_name"), RepricingSide.LIABILITY, RateType.FIXED,
                        rs.getObject("maturity_date", LocalDate.class), null, rs.getBigDecimal("balance")));
    }
}
