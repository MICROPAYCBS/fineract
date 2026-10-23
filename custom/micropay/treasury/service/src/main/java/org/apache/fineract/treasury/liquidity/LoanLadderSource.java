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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Repayments still due on active loans: each unpaid installment's principal, and its interest, fees and penalties, on
 * its due date (overdue ones in the first bucket). Behavioural adjustments, per loan product or general:
 *
 * <ul>
 * <li>{@code ARREARS_HAIRCUT}: a loan in arrears (days since its oldest unpaid installment fell due) has the rate of
 * its band taken off every flow it still owes;</li>
 * <li>{@code PREPAYMENT}: for loans not in arrears, the rate of each principal flow due after the first month is
 * brought forward into the first bucket.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class LoanLadderSource implements LadderSource {

    static final String PRINCIPAL = "LOAN_PRINCIPAL";
    static final String INTEREST = "LOAN_INTEREST_FEES";
    /** Principal due within this many days is not prepaid. */
    static final int PREPAYMENT_AFTER_DAYS = 30;

    private static final String SQL = """
            SELECT l.id AS loan_id, l.product_id, p.name AS product_name, s.duedate,
                   s.principal_amount - COALESCE(s.principal_completed_derived, 0) - COALESCE(s.principal_writtenoff_derived, 0) AS principal,
                   COALESCE(s.interest_amount, 0) - COALESCE(s.interest_completed_derived, 0) - COALESCE(s.interest_waived_derived, 0)
                     - COALESCE(s.interest_writtenoff_derived, 0)
                   + COALESCE(s.fee_charges_amount, 0) - COALESCE(s.fee_charges_completed_derived, 0) - COALESCE(s.fee_charges_waived_derived, 0)
                     - COALESCE(s.fee_charges_writtenoff_derived, 0)
                   + COALESCE(s.penalty_charges_amount, 0) - COALESCE(s.penalty_charges_completed_derived, 0)
                     - COALESCE(s.penalty_charges_waived_derived, 0) - COALESCE(s.penalty_charges_writtenoff_derived, 0) AS interest
            FROM m_loan l
            JOIN m_product_loan p ON p.id = l.product_id
            JOIN m_loan_repayment_schedule s ON s.loan_id = l.id
            LEFT JOIN m_client c ON c.id = l.client_id
            LEFT JOIN m_group g ON g.id = l.group_id
            WHERE l.loan_status_id = 300 AND l.currency_code = :currency AND s.completed_derived = :notCompleted
              AND (:officeId IS NULL OR COALESCE(c.office_id, g.office_id) = :officeId)
            ORDER BY l.id, s.duedate
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /** One unpaid installment. */
    public record Installment(Long loanId, Long productId, String productName, LocalDate dueDate, BigDecimal principal,
            BigDecimal interest) {
    }

    @Override
    public LadderSourceCode code() {
        return LadderSourceCode.LOANS;
    }

    @Override
    public List<LadderFlow> flows(final LadderContext context) {
        final MapSqlParameterSource params = new MapSqlParameterSource().addValue("currency", context.currencyCode())
                .addValue("notCompleted", Boolean.FALSE).addValue("officeId", context.officeId(), java.sql.Types.BIGINT);
        // Streamed a loan at a time (the rows come ordered by loan), the flows summed as they are made
        final Map<LadderFlow, BigDecimal> totals = new LinkedHashMap<>();
        final List<Installment> loan = new ArrayList<>();
        StreamingQueries.query(this.jdbcTemplate, SQL, params, (RowCallbackHandler) rs -> {
            final Installment installment = new Installment(rs.getLong("loan_id"), rs.getLong("product_id"), rs.getString("product_name"),
                    rs.getObject("duedate", LocalDate.class), rs.getBigDecimal("principal"), rs.getBigDecimal("interest"));
            if (!loan.isEmpty() && !loan.getFirst().loanId().equals(installment.loanId())) {
                add(totals, flows(context, loan));
                loan.clear();
            }
            loan.add(installment);
        });
        add(totals, flows(context, loan));
        return totals.entrySet().stream().map(e -> withAmount(e.getKey(), e.getValue())).toList();
    }

    /** Adds the flows to the totals, keyed by the flow without its amount (and by its sign). */
    static void add(final Map<LadderFlow, BigDecimal> totals, final List<LadderFlow> flows) {
        for (final LadderFlow flow : flows) {
            totals.merge(withAmount(flow, BigDecimal.valueOf(flow.amount().signum())), flow.amount(), BigDecimal::add);
        }
    }

    private static LadderFlow withAmount(final LadderFlow f, final BigDecimal amount) {
        return new LadderFlow(f.source(), f.lineCode(), f.productId(), f.productName(), f.date(), amount, f.behavioural());
    }

    /** The flows of the installments, grouped by loan, with the behavioural adjustments. */
    public static List<LadderFlow> flows(final LadderContext context, final List<Installment> installments) {
        final Map<Long, List<Installment>> byLoan = new LinkedHashMap<>();
        installments.forEach(i -> byLoan.computeIfAbsent(i.loanId(), k -> new ArrayList<>()).add(i));
        final List<LadderFlow> flows = new ArrayList<>();
        for (final List<Installment> loan : byLoan.values()) {
            final long arrearsDays = loan.stream().filter(i -> i.dueDate().isBefore(context.asOf()) && owed(i).signum() > 0)
                    .map(i -> ChronoUnit.DAYS.between(i.dueDate(), context.asOf())).max(Long::compare).orElse(0L);
            final Installment first = loan.getFirst();
            final Optional<BigDecimal> haircut = context.assumptions()
                    .band(AssumptionType.ARREARS_HAIRCUT, LadderSourceCode.LOANS, first.productId(), arrearsDays)
                    .map(Assumptions.Row::rate);
            final Optional<BigDecimal> prepayment = arrearsDays > 0 ? Optional.empty()
                    : context.assumptions().rate(AssumptionType.PREPAYMENT, LadderSourceCode.LOANS, first.productId(), null);
            for (final Installment installment : loan) {
                for (final boolean principal : new boolean[] { true, false }) {
                    final BigDecimal amount = principal ? installment.principal() : installment.interest();
                    if (amount == null || amount.signum() <= 0) {
                        continue;
                    }
                    final String line = principal ? PRINCIPAL : INTEREST;
                    flows.add(LadderFlow.contractual(LadderSourceCode.LOANS, line, installment.productId(), installment.productName(),
                            installment.dueDate(), amount));
                    if (haircut.isPresent() && haircut.get().signum() > 0) {
                        flows.add(LadderFlow.behavioural(LadderSourceCode.LOANS, line, installment.productId(), installment.productName(),
                                installment.dueDate(), context.percent(amount, haircut.get()).negate()));
                    } else if (principal && prepayment.isPresent() && prepayment.get().signum() > 0
                            && ChronoUnit.DAYS.between(context.asOf(), installment.dueDate()) > PREPAYMENT_AFTER_DAYS) {
                        final BigDecimal early = context.percent(amount, prepayment.get());
                        flows.add(LadderFlow.behavioural(LadderSourceCode.LOANS, line, installment.productId(), installment.productName(),
                                installment.dueDate(), early.negate()));
                        flows.add(LadderFlow.behavioural(LadderSourceCode.LOANS, line, installment.productId(), installment.productName(),
                                null, early));
                    }
                }
            }
        }
        return flows;
    }

    private static BigDecimal owed(final Installment installment) {
        return (installment.principal() == null ? BigDecimal.ZERO : installment.principal())
                .add(installment.interest() == null ? BigDecimal.ZERO : installment.interest());
    }
}
