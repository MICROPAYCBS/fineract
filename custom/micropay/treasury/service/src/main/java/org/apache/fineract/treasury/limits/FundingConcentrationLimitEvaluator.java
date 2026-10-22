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

import java.sql.Types;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The share of the top N funding counterparties in borrowings: the principal outstanding (amount less principal repaid)
 * of settled liability deals in the currency with a value date on or before the as-of date, per counterparty; of the
 * office's deals for an office limit.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class FundingConcentrationLimitEvaluator implements LimitEvaluator {

    static final String SQL = """
            SELECT cp.short_code AS funder, SUM(d.amount - COALESCE(d.principal_settled, 0)) AS outstanding
            FROM m_treasury_deal d
            JOIN m_treasury_deal_type t ON t.id = d.deal_type_id
            JOIN m_treasury_counterparty cp ON cp.id = d.counterparty_id
            WHERE t.direction = 'LIABILITY' AND d.status = 'SETTLED' AND d.currency_code = :currency AND d.value_date <= :asOf
              AND (:officeId IS NULL OR d.office_id = :officeId)
            GROUP BY cp.short_code
            ORDER BY 2 DESC, 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public LimitMetric metric() {
        return LimitMetric.FUNDING_CONCENTRATION;
    }

    @Override
    public List<Observation> observe(final TreasuryLimit limit, final EvaluationContext context) {
        final List<DepositorConcentrationLimitEvaluator.Holder> funders = this.jdbcTemplate.query(SQL,
                new MapSqlParameterSource().addValue("currency", context.currencyCode()).addValue("asOf", context.asOfDate())
                        .addValue("officeId", context.officeId(), Types.BIGINT),
                (rs, i) -> new DepositorConcentrationLimitEvaluator.Holder(rs.getString("funder"), rs.getBigDecimal("outstanding")));
        return List.of(DepositorConcentrationLimitEvaluator.observe(limit, context, funders, "DEALS", "funding counterparties"));
    }
}
