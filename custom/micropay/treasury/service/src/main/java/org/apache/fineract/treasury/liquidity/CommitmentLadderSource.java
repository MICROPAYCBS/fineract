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
 * Committed lines: loans approved and not yet disbursed, paid out on their expected disbursement date (overdue ones in
 * the first bucket). {@code DRAWDOWN} (per loan product or general) is the share expected to be drawn; the rest is
 * added back. Undrawn credit lines the institution has received have no register yet (the funding lines of P3.4).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class CommitmentLadderSource implements LadderSource {

    static final String LINE = "UNDISBURSED_LOANS";

    private static final String SQL = """
            SELECT l.id, l.product_id, p.name AS product_name, l.expected_disbursedon_date,
                   COALESCE(l.approved_principal, l.principal_amount) AS amount
            FROM m_loan l
            JOIN m_product_loan p ON p.id = l.product_id
            LEFT JOIN m_client c ON c.id = l.client_id
            LEFT JOIN m_group g ON g.id = l.group_id
            WHERE l.loan_status_id = 200 AND l.currency_code = :currency
              AND (:officeId IS NULL OR COALESCE(c.office_id, g.office_id) = :officeId)
            ORDER BY l.id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public record Commitment(Long productId, String productName, LocalDate expectedDate, BigDecimal amount) {
    }

    @Override
    public LadderSourceCode code() {
        return LadderSourceCode.COMMITMENTS;
    }

    @Override
    public List<LadderFlow> flows(final LadderContext context) {
        final List<Commitment> commitments = this.jdbcTemplate.query(SQL,
                new MapSqlParameterSource().addValue("currency", context.currencyCode()).addValue("officeId", context.officeId(),
                        java.sql.Types.BIGINT),
                (rs, i) -> new Commitment(rs.getLong("product_id"), rs.getString("product_name"),
                        rs.getObject("expected_disbursedon_date", LocalDate.class), rs.getBigDecimal("amount")));
        return flows(context, commitments);
    }

    public static List<LadderFlow> flows(final LadderContext context, final List<Commitment> commitments) {
        final List<LadderFlow> flows = new ArrayList<>();
        for (final Commitment commitment : commitments) {
            if (commitment.amount() == null || commitment.amount().signum() <= 0) {
                continue;
            }
            flows.add(LadderFlow.contractual(LadderSourceCode.COMMITMENTS, LINE, commitment.productId(), commitment.productName(),
                    commitment.expectedDate(), commitment.amount().negate()));
            final Optional<BigDecimal> drawn = context.assumptions().rate(AssumptionType.DRAWDOWN, LadderSourceCode.COMMITMENTS,
                    commitment.productId(), null);
            if (drawn.isPresent()) {
                flows.add(LadderFlow.behavioural(LadderSourceCode.COMMITMENTS, LINE, commitment.productId(), commitment.productName(),
                        commitment.expectedDate(), context.percent(commitment.amount(), LadderContext.HUNDRED.subtract(drawn.get()))));
            }
        }
        return flows;
    }
}
