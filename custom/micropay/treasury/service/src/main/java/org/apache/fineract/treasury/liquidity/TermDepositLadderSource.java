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
package org.apache.fineract.treasury.liquidity;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Active fixed and recurring deposits, paid out at maturity: the maturity amount (the balance when there is none) on
 * the maturity date. {@code ROLLOVER} (per savings product or general) is the share expected to be rolled over, added
 * back on the same date.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TermDepositLadderSource implements LadderSource {

    static final String FIXED = "FIXED_DEPOSIT";
    static final String RECURRING = "RECURRING_DEPOSIT";

    private static final String SQL = """
            SELECT a.product_id, p.name AS product_name, a.deposit_type_enum, t.maturity_date,
                   COALESCE(t.maturity_amount, a.account_balance_derived) AS amount
            FROM m_savings_account a
            JOIN m_savings_product p ON p.id = a.product_id
            JOIN m_deposit_account_term_and_preclosure t ON t.savings_account_id = a.id
            LEFT JOIN m_client c ON c.id = a.client_id
            LEFT JOIN m_group g ON g.id = a.group_id
            WHERE a.deposit_type_enum IN (200, 300) AND a.status_enum = 300 AND a.currency_code = :currency
              AND (:officeId IS NULL OR COALESCE(c.office_id, g.office_id) = :officeId)
            ORDER BY a.id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public record Deposit(Long productId, String productName, boolean recurring, LocalDate maturityDate, BigDecimal amount) {
    }

    @Override
    public LadderSourceCode code() {
        return LadderSourceCode.TERM_DEPOSITS;
    }

    @Override
    public List<LadderFlow> flows(final LadderContext context) {
        final List<Deposit> deposits = this.jdbcTemplate.query(SQL,
                new MapSqlParameterSource().addValue("currency", context.currencyCode()).addValue("officeId", context.officeId(),
                        java.sql.Types.BIGINT),
                (rs, i) -> new Deposit(rs.getLong("product_id"), rs.getString("product_name"), rs.getInt("deposit_type_enum") == 300,
                        rs.getObject("maturity_date", LocalDate.class), rs.getBigDecimal("amount")));
        return flows(context, deposits);
    }

    public static List<LadderFlow> flows(final LadderContext context, final List<Deposit> deposits) {
        final List<LadderFlow> flows = new ArrayList<>();
        for (final Deposit deposit : deposits) {
            if (deposit.amount() == null || deposit.amount().signum() <= 0) {
                continue;
            }
            final String line = deposit.recurring() ? RECURRING : FIXED;
            flows.add(LadderFlow.contractual(LadderSourceCode.TERM_DEPOSITS, line, deposit.productId(), deposit.productName(),
                    deposit.maturityDate(), deposit.amount().negate()));
            final Optional<BigDecimal> rollover = context.assumptions().rate(AssumptionType.ROLLOVER, LadderSourceCode.TERM_DEPOSITS,
                    deposit.productId(), null);
            if (rollover.isPresent() && rollover.get().signum() > 0) {
                flows.add(LadderFlow.behavioural(LadderSourceCode.TERM_DEPOSITS, line, deposit.productId(), deposit.productName(),
                        deposit.maturityDate(), context.percent(deposit.amount(), rollover.get())));
            }
        }
        return flows;
    }
}
