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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Settled treasury deals: the principal still to come of each, FIXED (deals carry no rate type), repricing when it
 * falls due, at maturity for bullet deals; bills and bonds at their face value outstanding. Asset deals are RSA,
 * liability deals (borrowings) RSL.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class DealRepricingSource implements RepricingSource {

    static final String LINE = "DEAL_PRINCIPAL";

    private static final String SQL = """
            SELECT d.deal_type_id, t.code AS type_code, t.direction, f.due_date, f.amount - f.settled_amount AS amount
            FROM m_treasury_deal_cashflow f
            JOIN m_treasury_deal d ON d.id = f.deal_id
            JOIN m_treasury_deal_type t ON t.id = d.deal_type_id
            WHERE d.status = 'SETTLED' AND f.flow_type = 'PRINCIPAL' AND f.status IN ('EXPECTED', 'PARTIAL') AND d.currency_code = :currency
              AND d.value_date <= :asOf AND (:officeId IS NULL OR d.office_id = :officeId)
            ORDER BY d.id, f.sequence_number
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public LadderSourceCode code() {
        return LadderSourceCode.DEALS;
    }

    @Override
    public List<RepricingPosition> positions(final LadderContext context) {
        return this.jdbcTemplate.query(SQL,
                new MapSqlParameterSource().addValue("currency", context.currencyCode()).addValue("asOf", context.asOf())
                        .addValue("officeId", context.officeId(), Types.BIGINT),
                (rs, i) -> new RepricingPosition(LadderSourceCode.DEALS, LINE, rs.getLong("deal_type_id"), rs.getString("type_code"),
                        "ASSET".equals(rs.getString("direction")) ? RepricingSide.ASSET : RepricingSide.LIABILITY, RateType.FIXED,
                        rs.getObject("due_date", LocalDate.class), null, rs.getBigDecimal("amount")));
    }
}
