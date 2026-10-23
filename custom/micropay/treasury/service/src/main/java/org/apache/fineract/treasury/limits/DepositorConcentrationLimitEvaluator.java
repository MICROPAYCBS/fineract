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
package org.apache.fineract.treasury.limits;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.liquidity.StreamingQueries;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The share of the top N depositors in deposits: the balances of active savings, fixed and recurring deposit accounts
 * in the currency, per client or group, of the office's clients and groups for an office limit. Balances are the
 * accounts' current ones, as the ladder reads them; related parties are not grouped.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class DepositorConcentrationLimitEvaluator implements LimitEvaluator {

    static final String SQL = """
            SELECT CASE WHEN a.client_id IS NOT NULL THEN CONCAT('client ', a.client_id) ELSE CONCAT('group ', a.group_id) END AS depositor,
                   SUM(a.account_balance_derived) AS balance
            FROM m_savings_account a
            LEFT JOIN m_client c ON c.id = a.client_id
            LEFT JOIN m_group g ON g.id = a.group_id
            WHERE a.deposit_type_enum IN (100, 200, 300) AND a.status_enum = 300 AND a.currency_code = :currency
              AND a.account_balance_derived > 0
              AND (:officeId IS NULL OR COALESCE(c.office_id, g.office_id) = :officeId)
            GROUP BY 1
            ORDER BY 2 DESC, 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public LimitMetric metric() {
        return LimitMetric.DEPOSITOR_CONCENTRATION;
    }

    @Override
    public List<Observation> observe(final TreasuryLimit limit, final EvaluationContext context) {
        final List<Holder> holders = StreamingQueries.query(this.jdbcTemplate, SQL, new MapSqlParameterSource()
                .addValue("currency", context.currencyCode()).addValue("officeId", context.officeId(), Types.BIGINT),
                (rs, i) -> new Holder(rs.getString("depositor"), rs.getBigDecimal("balance")));
        return List.of(observe(limit, context, holders, "DEPOSITS", "depositors"));
    }

    /** A holder of deposits or a funding counterparty, largest first. */
    record Holder(String reference, BigDecimal amount) {
    }

    static Observation observe(final TreasuryLimit limit, final EvaluationContext context, final List<Holder> holders,
            final String sourceType, final String noun) {
        final int topN = limit.getTopN() == null ? 1 : limit.getTopN();
        final LimitLevels.Concentration share = LimitLevels.concentration(holders.stream().map(Holder::amount).toList(), topN);
        final List<String> top = holders.stream().filter(h -> h.amount().signum() > 0).limit(Math.min(topN, 5)).map(Holder::reference)
                .toList();
        final String detail = "Top " + topN + " of " + share.holders() + " " + noun + ": " + LimitSubjects.amount(share.top()) + " of "
                + LimitSubjects.amount(share.total()) + " " + context.currencyCode() + (top.isEmpty() ? ""
                        : " (" + String.join(", ", top) + (topN > top.size() && share.holders() > top.size() ? ", ..." : "") + ")");
        if (share.share() == null) {
            return LimitSubjects.unavailable(limit, context, sourceType, null, "No " + noun + " in " + context.currencyCode());
        }
        return LimitSubjects.observe(limit, context, share.share(), sourceType, null,
                detail.length() > 1000 ? detail.substring(0, 1000) : detail);
    }
}
