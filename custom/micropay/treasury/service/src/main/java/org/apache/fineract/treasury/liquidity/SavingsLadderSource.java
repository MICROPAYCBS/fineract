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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Demand savings: contractually the whole balance of each product is payable on demand (first bucket). When the
 * assumptions have a {@code CORE_SHARE} or {@code RUN_OFF} rows for the product (or general ones), that is replaced by
 * a behavioural profile: the core share stays to the last bucket, and of the volatile rest each bucket's
 * {@code RUN_OFF} rate leaves in that bucket, the part not run off by the end also in the last bucket.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class SavingsLadderSource implements LadderSource {

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

    public record ProductBalance(Long productId, String productName, BigDecimal balance) {
    }

    @Override
    public LadderSourceCode code() {
        return LadderSourceCode.SAVINGS;
    }

    @Override
    public List<LadderFlow> flows(final LadderContext context) {
        final List<ProductBalance> balances = this.jdbcTemplate.query(SQL,
                new MapSqlParameterSource().addValue("currency", context.currencyCode()).addValue("officeId", context.officeId(),
                        java.sql.Types.BIGINT),
                (rs, i) -> new ProductBalance(rs.getLong("product_id"), rs.getString("product_name"), rs.getBigDecimal("balance")));
        return flows(context, balances);
    }

    public static List<LadderFlow> flows(final LadderContext context, final List<ProductBalance> balances) {
        final List<LadderFlow> flows = new ArrayList<>();
        for (final ProductBalance product : balances) {
            final BigDecimal balance = product.balance();
            if (balance == null || balance.signum() <= 0) {
                continue;
            }
            flows.add(LadderFlow.contractual(LadderSourceCode.SAVINGS, LINE, product.productId(), product.productName(), null,
                    balance.negate()));
            final Optional<BigDecimal> coreShare = context.assumptions().rate(AssumptionType.CORE_SHARE, LadderSourceCode.SAVINGS,
                    product.productId(), null);
            final List<Assumptions.Row> runOff = context.assumptions().rows(AssumptionType.RUN_OFF, LadderSourceCode.SAVINGS,
                    product.productId());
            if (coreShare.isEmpty() && runOff.isEmpty()) {
                continue;
            }
            // the contractual run on day one is replaced by the behavioural profile
            flows.add(LadderFlow.behavioural(LadderSourceCode.SAVINGS, LINE, product.productId(), product.productName(), null, balance));
            final BigDecimal core = context.percent(balance, coreShare.orElse(BigDecimal.ZERO).min(LadderContext.HUNDRED));
            final BigDecimal volatilePart = balance.subtract(core);
            BigDecimal left = volatilePart;
            BigDecimal rateTotal = BigDecimal.ZERO;
            for (final Bucket bucket : context.buckets()) {
                final Optional<Assumptions.Row> row = runOff.stream().filter(r -> bucket.code().equals(r.bucketCode())).findFirst();
                if (row.isEmpty() || row.get().rate().signum() == 0) {
                    continue;
                }
                rateTotal = rateTotal.add(row.get().rate());
                final BigDecimal out = context.percent(volatilePart, row.get().rate()).min(left);
                left = left.subtract(out);
                flows.add(LadderFlow.behavioural(LadderSourceCode.SAVINGS, LINE, product.productId(), product.productName(),
                        context.start(bucket), out.negate()));
            }
            if (rateTotal.compareTo(LadderContext.HUNDRED) > 0) {
                context.warn("Run-off rates of savings product " + product.productId() + " add up to " + rateTotal.toPlainString()
                        + "%, above 100%");
            }
            runOff.stream().filter(r -> context.bucketCode(r.bucketCode()) == null).forEach(
                    r -> context.warn("Run-off rate for bucket " + r.bucketCode() + ", which the rule set does not have, is ignored"));
            flows.add(LadderFlow.behavioural(LadderSourceCode.SAVINGS, LINE, product.productId(), product.productName(),
                    context.start(context.last()), core.add(left).negate()));
        }
        return flows;
    }
}
