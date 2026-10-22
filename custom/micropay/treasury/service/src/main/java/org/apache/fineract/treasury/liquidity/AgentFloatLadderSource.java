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
import java.math.RoundingMode;
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
 * Expected net agent float demand: with a {@code FLOAT_DEMAND} assumption (its {@code minDays} the lookback, its rate a
 * scaling percentage, 100 for history as it is), the top-ups less sweeps executed over the lookback days up to the
 * as-of date, as a daily average, are taken out over the days of every bounded bucket. No contractual flows.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class AgentFloatLadderSource implements LadderSource {

    static final String LINE = "AGENT_FLOAT_DEMAND";

    private static final String SQL = """
            SELECT COALESCE(SUM(CASE WHEN r.request_type = 'TOPUP' THEN r.amount ELSE -r.amount END), 0)
            FROM m_treasury_float_request r
            JOIN m_treasury_agent_float f ON f.id = r.agent_float_id
            JOIN m_treasury_account a ON a.id = f.treasury_account_id
            WHERE r.status = 'EXECUTED' AND r.executed_on_date > :from AND r.executed_on_date <= :to AND a.currency_code = :currency
              AND (:officeId IS NULL OR a.office_id = :officeId)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public LadderSourceCode code() {
        return LadderSourceCode.AGENT_FLOAT;
    }

    @Override
    public List<LadderFlow> flows(final LadderContext context) {
        final Optional<Assumptions.Row> demand = context.assumptions().rows(AssumptionType.FLOAT_DEMAND, LadderSourceCode.AGENT_FLOAT, null)
                .stream().findFirst();
        if (demand.isEmpty() || demand.get().minDays() == null || demand.get().minDays() <= 0) {
            return List.of();
        }
        final int lookback = demand.get().minDays();
        final BigDecimal net = this.jdbcTemplate.queryForObject(SQL,
                new MapSqlParameterSource().addValue("from", context.asOf().minusDays(lookback)).addValue("to", context.asOf())
                        .addValue("currency", context.currencyCode()).addValue("officeId", context.officeId(), java.sql.Types.BIGINT),
                BigDecimal.class);
        return flows(context, net == null ? BigDecimal.ZERO : net, lookback, demand.get().rate());
    }

    /**
     * @param netTopUps
     *            top-ups less sweeps over the lookback days
     */
    public static List<LadderFlow> flows(final LadderContext context, final BigDecimal netTopUps, final int lookbackDays,
            final BigDecimal scale) {
        final List<LadderFlow> flows = new ArrayList<>();
        if (netTopUps.signum() == 0) {
            return flows;
        }
        final BigDecimal daily = netTopUps.multiply(scale).divide(LadderContext.HUNDRED.multiply(BigDecimal.valueOf(lookbackDays)), 12,
                RoundingMode.HALF_UP);
        for (final Bucket bucket : context.buckets()) {
            if (bucket.days() == 0) {
                continue;
            }
            final LocalDate date = context.start(bucket);
            flows.add(LadderFlow.behavioural(LadderSourceCode.AGENT_FLOAT, LINE, null, null, date,
                    context.round(daily.multiply(BigDecimal.valueOf(bucket.days()))).negate()));
        }
        return flows;
    }
}
