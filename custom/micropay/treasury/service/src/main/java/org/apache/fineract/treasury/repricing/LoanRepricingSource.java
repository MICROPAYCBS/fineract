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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.liquidity.AssumptionType;
import org.apache.fineract.treasury.liquidity.Assumptions;
import org.apache.fineract.treasury.liquidity.LadderContext;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Active loans' principal outstanding (the unpaid principal of their schedule).
 *
 * <ul>
 * <li>Fixed-rate loans reprice as their principal falls due: each installment's principal on its due date (overdue in
 * the first bucket).</li>
 * <li>Floating-rate loans ({@code is_floating_interest_rate}) reprice whole at the next reset: the first active period
 * of their product's floating rate starting after the as-of date (upstream {@code m_floating_rates_periods}), the first
 * bucket when there is none.</li>
 * <li>Non-performing loans ({@code is_npa}) are NON_SENSITIVE; so is the {@code REPRICING_NON_SENSITIVE} rate of the
 * principal of a loan in the days-in-arrears band of an assumption row (the oldest unpaid installment's days past
 * due).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class LoanRepricingSource implements RepricingSource {

    static final String PRINCIPAL = "LOAN_PRINCIPAL";

    private static final String SQL = """
            SELECT l.id AS loan_id, l.product_id, p.name AS product_name, l.is_npa, l.is_floating_interest_rate,
                   nr.next_reset,
                   s.duedate,
                   s.principal_amount - COALESCE(s.principal_completed_derived, 0) - COALESCE(s.principal_writtenoff_derived, 0) AS principal,
                   s.principal_amount - COALESCE(s.principal_completed_derived, 0) - COALESCE(s.principal_writtenoff_derived, 0)
                   + COALESCE(s.interest_amount, 0) - COALESCE(s.interest_completed_derived, 0) - COALESCE(s.interest_waived_derived, 0)
                     - COALESCE(s.interest_writtenoff_derived, 0) AS owed
            FROM m_loan l
            JOIN m_product_loan p ON p.id = l.product_id
            JOIN m_loan_repayment_schedule s ON s.loan_id = l.id
            LEFT JOIN m_client c ON c.id = l.client_id
            LEFT JOIN m_group g ON g.id = l.group_id
            LEFT JOIN (SELECT pf.loan_product_id, MIN(fp.from_date) AS next_reset FROM m_product_loan_floating_rates pf
                       JOIN m_floating_rates_periods fp ON fp.floating_rates_id = pf.floating_rates_id
                       WHERE fp.is_active = true AND fp.from_date > :asOf GROUP BY pf.loan_product_id) nr ON nr.loan_product_id = l.product_id
            WHERE l.loan_status_id = 300 AND l.currency_code = :currency AND s.completed_derived = :notCompleted
              AND (:officeId IS NULL OR COALESCE(c.office_id, g.office_id) = :officeId)
            ORDER BY l.id, s.duedate
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /** An unpaid installment of a loan, with what the loan is. */
    public record Installment(Long loanId, Long productId, String productName, boolean npa, boolean floating, LocalDate nextReset,
            LocalDate dueDate, BigDecimal principal, BigDecimal owed) {
    }

    @Override
    public LadderSourceCode code() {
        return LadderSourceCode.LOANS;
    }

    @Override
    public List<RepricingPosition> positions(final LadderContext context) {
        final MapSqlParameterSource params = new MapSqlParameterSource().addValue("currency", context.currencyCode())
                .addValue("notCompleted", Boolean.FALSE).addValue("asOf", context.asOf())
                .addValue("officeId", context.officeId(), Types.BIGINT);
        // Streamed a loan at a time (the rows come ordered by loan), the positions summed as they are made
        final Map<RepricingPosition, BigDecimal> totals = new LinkedHashMap<>();
        final List<Installment> loan = new ArrayList<>();
        this.jdbcTemplate.query(SQL, params, (RowCallbackHandler) rs -> {
            final Installment installment = new Installment(rs.getLong("loan_id"), rs.getLong("product_id"), rs.getString("product_name"),
                    rs.getBoolean("is_npa"), rs.getBoolean("is_floating_interest_rate"), rs.getObject("next_reset", LocalDate.class),
                    rs.getObject("duedate", LocalDate.class), rs.getBigDecimal("principal"), rs.getBigDecimal("owed"));
            if (!loan.isEmpty() && !loan.getFirst().loanId().equals(installment.loanId())) {
                add(totals, positions(context, loan));
                loan.clear();
            }
            loan.add(installment);
        });
        add(totals, positions(context, loan));
        return totals.entrySet().stream().map(e -> withAmount(e.getKey(), e.getValue())).toList();
    }

    /** Adds the positions to the totals, keyed by the position without its amount (and by its sign). */
    static void add(final Map<RepricingPosition, BigDecimal> totals, final List<RepricingPosition> positions) {
        for (final RepricingPosition position : positions) {
            totals.merge(withAmount(position, BigDecimal.valueOf(position.amount().signum())), position.amount(), BigDecimal::add);
        }
    }

    private static RepricingPosition withAmount(final RepricingPosition p, final BigDecimal amount) {
        return new RepricingPosition(p.source(), p.lineCode(), p.productId(), p.productName(), p.side(), p.rateType(), p.repricingDate(),
                p.bucketCode(), amount);
    }

    public static List<RepricingPosition> positions(final LadderContext context, final List<Installment> installments) {
        final Map<Long, List<Installment>> byLoan = new LinkedHashMap<>();
        installments.forEach(i -> byLoan.computeIfAbsent(i.loanId(), k -> new ArrayList<>()).add(i));
        final List<RepricingPosition> positions = new ArrayList<>();
        for (final List<Installment> loan : byLoan.values()) {
            final Installment first = loan.getFirst();
            final long arrearsDays = loan.stream()
                    .filter(i -> i.dueDate().isBefore(context.asOf()) && i.owed() != null && i.owed().signum() > 0)
                    .map(i -> ChronoUnit.DAYS.between(i.dueDate(), context.asOf())).max(Long::compare).orElse(0L);
            final BigDecimal nonSensitive = first.npa() ? LadderContext.HUNDRED
                    : context.assumptions()
                            .band(AssumptionType.REPRICING_NON_SENSITIVE, LadderSourceCode.LOANS, first.productId(), arrearsDays)
                            .map(Assumptions.Row::rate).orElse(BigDecimal.ZERO);
            for (final Installment installment : loan) {
                if (installment.principal() == null || installment.principal().signum() <= 0) {
                    continue;
                }
                final BigDecimal out = context.percent(installment.principal(), nonSensitive);
                final BigDecimal in = installment.principal().subtract(out);
                if (out.signum() != 0) {
                    positions.add(new RepricingPosition(LadderSourceCode.LOANS, PRINCIPAL, installment.productId(),
                            installment.productName(), RepricingSide.ASSET, RateType.NON_SENSITIVE, null, null, out));
                }
                if (in.signum() != 0) {
                    positions.add(first.floating()
                            ? new RepricingPosition(LadderSourceCode.LOANS, PRINCIPAL, installment.productId(), installment.productName(),
                                    RepricingSide.ASSET, RateType.FLOATING, first.nextReset(), null, in)
                            : new RepricingPosition(LadderSourceCode.LOANS, PRINCIPAL, installment.productId(), installment.productName(),
                                    RepricingSide.ASSET, RateType.FIXED, installment.dueDate(), null, in));
                }
            }
        }
        return positions;
    }
}
