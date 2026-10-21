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
package org.apache.fineract.scoring.input;

import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_ACTIVE_LOANS;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_AGE_YEARS;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_BLACKLISTED_FOR_LOANS;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_CLASS_NOT_LOAN_ELIGIBLE;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_CLOSED_LOANS;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_CURRENT_DAYS_IN_ARREARS;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_HAS_EXPIRED_ID;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_IS_STAFF;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_LEGAL_FORM;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_MAX_DAYS_LATE_HISTORY;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_MONTHLY_INCOME;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_MONTHS_ACTIVE;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_OUTSTANDING_PRINCIPAL;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.LOAN_NUMBER_OF_REPAYMENTS;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.LOAN_PRINCIPAL;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.LOAN_PRINCIPAL_TO_MONTHLY_INCOME;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.SAVINGS_BALANCE;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.SAVINGS_MONTHLY_DEPOSITS_6M;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.SAVINGS_TRANSACTIONS_6M;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.scoring.exception.ScoringConfigurationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the scoring inputs for one loan application straight from the tenant database. Plain SQL keeps the reads cheap
 * and avoids touching the loan aggregate inside the approval transaction. Every value lands in the decision's input
 * snapshot, so the result can be explained later even after the client's data changes.
 */
@Component
@RequiredArgsConstructor
public class ScoringInputCollector {

    private static final int LOAN_STATUS_ACTIVE = 300;
    private static final int LOAN_STATUS_CLOSED_OBLIGATIONS_MET = 600;
    private static final int SAVINGS_STATUS_ACTIVE = 300;
    private static final int SAVINGS_TRANSACTION_DEPOSIT = 1;
    private static final int IDENTIFIER_STATUS_ACTIVE = 200;
    private static final int LEGAL_FORM_PERSON = 1;
    private static final int LOOKBACK_DAYS = 180;
    private static final BigDecimal LOOKBACK_MONTHS = BigDecimal.valueOf(6);

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;

    /**
     * The loan being scored. Principal and term come from the loan aggregate when scoring inside a loan transaction,
     * because the approval's changes may not be flushed to the database yet.
     */
    public record LoanFacts(Long loanId, Long clientId, Long productId, BigDecimal principal, Integer numberOfRepayments) {

        /** A client scored on its own, for a product but without a loan. */
        public static LoanFacts forClient(final Long clientId, final Long productId) {
            return new LoanFacts(null, clientId, productId, null, null);
        }
    }

    public LoanFacts loanFacts(final Long loanId) {
        final List<LoanFacts> rows = jdbcTemplate.query(
                "SELECT id, client_id, product_id, principal_amount, principal_amount_proposed, number_of_repayments FROM m_loan WHERE id = ?",
                (rs, rowNum) -> {
                    BigDecimal principal = rs.getBigDecimal("principal_amount");
                    if (principal == null || principal.signum() == 0) {
                        principal = rs.getBigDecimal("principal_amount_proposed");
                    }
                    return new LoanFacts(rs.getLong("id"), rs.getObject("client_id", Long.class), rs.getLong("product_id"), principal,
                            rs.getObject("number_of_repayments", Integer.class));
                }, loanId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Collects every built-in characteristic plus the datatable columns in {@code datatableCodes}.
     */
    public Map<String, Object> collect(final LoanFacts loan, final Set<String> datatableCodes) {
        final LocalDate today = DateUtils.getBusinessLocalDate();
        final Map<String, Object> inputs = new TreeMap<>();
        collectClient(loan.clientId(), today, inputs);
        collectLoanHistory(loan, today, inputs);
        collectSavings(loan.clientId(), today, inputs);
        collectLoan(loan, inputs);

        final BigDecimal income = (BigDecimal) inputs.get(CLIENT_MONTHLY_INCOME);
        final BigDecimal principal = (BigDecimal) inputs.get(LOAN_PRINCIPAL);
        inputs.put(LOAN_PRINCIPAL_TO_MONTHLY_INCOME,
                income == null || income.signum() <= 0 || principal == null ? null : principal.divide(income, 4, RoundingMode.HALF_UP));

        for (final String code : datatableCodes) {
            inputs.put(code, readDatatableValue(code, loan));
        }
        return inputs;
    }

    private void collectClient(final Long clientId, final LocalDate today, final Map<String, Object> inputs) {
        final Map<String, Object> client = jdbcTemplate.queryForMap(
                "SELECT date_of_birth, activation_date, legal_form_enum, is_staff, customer_class_id FROM m_client WHERE id = ?", clientId);
        final LocalDate dateOfBirth = toLocalDate(client.get("date_of_birth"));
        final LocalDate activationDate = toLocalDate(client.get("activation_date"));
        inputs.put(CLIENT_AGE_YEARS, dateOfBirth == null ? null : BigDecimal.valueOf(ChronoUnit.YEARS.between(dateOfBirth, today)));
        inputs.put(CLIENT_MONTHS_ACTIVE,
                activationDate == null ? null : BigDecimal.valueOf(ChronoUnit.MONTHS.between(activationDate, today)));
        final Object legalForm = client.get("legal_form_enum");
        inputs.put(CLIENT_LEGAL_FORM,
                legalForm == null ? null : (((Number) legalForm).intValue() == LEGAL_FORM_PERSON ? "PERSON" : "ENTITY"));
        inputs.put(CLIENT_IS_STAFF, toBoolean(client.get("is_staff")));

        inputs.put(CLIENT_MONTHLY_INCOME,
                jdbcTemplate.queryForObject(
                        "SELECT SUM(monthly_income) FROM m_client_income_source WHERE client_id = ? AND status = 'ACTIVE'",
                        BigDecimal.class, clientId));
        inputs.put(
                CLIENT_HAS_EXPIRED_ID, count(
                        "SELECT COUNT(*) FROM m_client_identifier WHERE client_id = ? AND status = ?"
                                + " AND expiry_date IS NOT NULL AND expiry_date < ?",
                        clientId, IDENTIFIER_STATUS_ACTIVE, Date.valueOf(today)) > 0);
        inputs.put(CLIENT_BLACKLISTED_FOR_LOANS,
                count("SELECT COUNT(*) FROM m_client_blacklist WHERE client_id = ? AND status = 'ACTIVE'"
                        + " AND block_loan_application = 'Y' AND start_date <= ? AND (end_date IS NULL OR end_date >= ?)", clientId,
                        Date.valueOf(today), Date.valueOf(today)) > 0);
        final Object customerClassId = client.get("customer_class_id");
        inputs.put(CLIENT_CLASS_NOT_LOAN_ELIGIBLE, customerClassId != null
                && count("SELECT COUNT(*) FROM m_customer_class WHERE id = ? AND loan_eligible = 'N'", customerClassId) > 0);
    }

    private void collectLoanHistory(final LoanFacts loan, final LocalDate today, final Map<String, Object> inputs) {
        inputs.put(CLIENT_ACTIVE_LOANS,
                BigDecimal.valueOf(count("SELECT COUNT(*) FROM m_loan WHERE client_id = ? AND loan_status_id = ?" + " AND id <> ?",
                        loan.clientId(), LOAN_STATUS_ACTIVE, excludedLoanId(loan))));
        inputs.put(CLIENT_CLOSED_LOANS, BigDecimal.valueOf(count("SELECT COUNT(*) FROM m_loan WHERE client_id = ? AND loan_status_id = ?",
                loan.clientId(), LOAN_STATUS_CLOSED_OBLIGATIONS_MET)));
        final BigDecimal outstanding = jdbcTemplate.queryForObject(
                "SELECT SUM(principal_outstanding_derived) FROM m_loan WHERE client_id = ? AND loan_status_id = ? AND id <> ?",
                BigDecimal.class, loan.clientId(), LOAN_STATUS_ACTIVE, excludedLoanId(loan));
        inputs.put(CLIENT_OUTSTANDING_PRINCIPAL, outstanding == null ? BigDecimal.ZERO : outstanding);

        final List<LocalDate> overdueSince = jdbcTemplate.query(
                "SELECT a.overdue_since_date_derived FROM m_loan_arrears_aging a JOIN m_loan l ON l.id = a.loan_id"
                        + " WHERE l.client_id = ? AND l.loan_status_id = ? AND a.overdue_since_date_derived IS NOT NULL",
                (rs, rowNum) -> toLocalDate(rs.getObject(1)), loan.clientId(), LOAN_STATUS_ACTIVE);
        inputs.put(CLIENT_CURRENT_DAYS_IN_ARREARS, BigDecimal
                .valueOf(overdueSince.stream().mapToLong(date -> Math.max(0, ChronoUnit.DAYS.between(date, today))).max().orElse(0)));

        final List<Long> daysLate = jdbcTemplate.query(
                "SELECT s.duedate, s.obligations_met_on_date FROM m_loan_repayment_schedule s JOIN m_loan l ON l.id = s.loan_id"
                        + " WHERE l.client_id = ? AND s.obligations_met_on_date IS NOT NULL AND s.obligations_met_on_date > s.duedate",
                (rs, rowNum) -> ChronoUnit.DAYS.between(toLocalDate(rs.getObject(1)), toLocalDate(rs.getObject(2))), loan.clientId());
        // missing, not 0, when no installment was ever paid, so a first-time borrower can score as having no history
        final long paidInstallments = count("SELECT COUNT(*) FROM m_loan_repayment_schedule s JOIN m_loan l ON l.id = s.loan_id"
                + " WHERE l.client_id = ? AND s.obligations_met_on_date IS NOT NULL", loan.clientId());
        inputs.put(CLIENT_MAX_DAYS_LATE_HISTORY,
                paidInstallments == 0 ? null : BigDecimal.valueOf(daysLate.stream().mapToLong(Long::longValue).max().orElse(0)));
    }

    private void collectSavings(final Long clientId, final LocalDate today, final Map<String, Object> inputs) {
        final BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT SUM(account_balance_derived) FROM m_savings_account WHERE client_id = ? AND status_enum = ?", BigDecimal.class,
                clientId, SAVINGS_STATUS_ACTIVE);
        inputs.put(SAVINGS_BALANCE, balance == null ? BigDecimal.ZERO : balance);

        final Date since = Date.valueOf(today.minusDays(LOOKBACK_DAYS));
        final String recentTransactions = " FROM m_savings_account_transaction t JOIN m_savings_account s ON s.id = t.savings_account_id"
                + " WHERE s.client_id = ? AND t.is_reversed = ? AND t.transaction_date >= ?";
        final BigDecimal deposits = jdbcTemplate.queryForObject(
                "SELECT SUM(t.amount)" + recentTransactions + " AND t.transaction_type_enum = ?", BigDecimal.class, clientId, Boolean.FALSE,
                since, SAVINGS_TRANSACTION_DEPOSIT);
        inputs.put(SAVINGS_MONTHLY_DEPOSITS_6M,
                deposits == null ? BigDecimal.ZERO : deposits.divide(LOOKBACK_MONTHS, 2, RoundingMode.HALF_UP));
        inputs.put(SAVINGS_TRANSACTIONS_6M,
                BigDecimal.valueOf(count("SELECT COUNT(*)" + recentTransactions, clientId, Boolean.FALSE, since)));
    }

    private static void collectLoan(final LoanFacts loan, final Map<String, Object> inputs) {
        inputs.put(LOAN_PRINCIPAL, loan.principal());
        inputs.put(LOAN_NUMBER_OF_REPAYMENTS, loan.numberOfRepayments() == null ? null : BigDecimal.valueOf(loan.numberOfRepayments()));
    }

    /**
     * Reads one column of a client or loan datatable. The datatable must be registered against m_client or m_loan; the
     * column is looked up by name in the returned row, so neither name is ever spliced into SQL unchecked. A multi-row
     * datatable contributes its most recent row.
     */
    private Object readDatatableValue(final String code, final LoanFacts loan) {
        final String[] parts = ScoringCharacteristics.parseDatatable(code)
                .orElseThrow(() -> new ScoringConfigurationException("characteristic.invalid", "Unknown characteristic " + code, code));
        final List<String> appTables = jdbcTemplate.queryForList(
                "SELECT application_table_name FROM x_registered_table WHERE registered_table_name = ?", String.class, parts[0]);
        if (appTables.isEmpty()) {
            throw new ScoringConfigurationException("datatable.not.registered", "Datatable " + parts[0] + " is not registered", parts[0]);
        }
        final String appTable = appTables.get(0);
        final Long entityId;
        final String foreignKey;
        if ("m_client".equals(appTable)) {
            entityId = loan.clientId();
            foreignKey = "client_id";
        } else if ("m_loan".equals(appTable)) {
            if (loan.loanId() == null) {
                // a nightly behavioural run scores the client without a loan
                return null;
            }
            entityId = loan.loanId();
            foreignKey = "loan_id";
        } else {
            throw new ScoringConfigurationException("datatable.entity.unsupported",
                    "Datatable " + parts[0] + " belongs to " + appTable + "; only client and loan datatables can be scored", parts[0]);
        }
        final List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM " + sqlGenerator.escape(parts[0]) + " WHERE " + sqlGenerator.escape(foreignKey) + " = ?", entityId);
        if (rows.isEmpty()) {
            return null;
        }
        final Map<String, Object> row = rows.get(rows.size() - 1);
        if (!row.containsKey(parts[1])) {
            throw new ScoringConfigurationException("datatable.column.unknown", "Datatable " + parts[0] + " has no column " + parts[1],
                    parts[0], parts[1]);
        }
        return row.get(parts[1]);
    }

    /** A client scored without a loan (the nightly run) has every loan counted. */
    private static long excludedLoanId(final LoanFacts loan) {
        return loan.loanId() == null ? -1L : loan.loanId();
    }

    private long count(final String sql, final Object... args) {
        final Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
        return result == null ? 0 : result;
    }

    private static Boolean toBoolean(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static LocalDate toLocalDate(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.time.LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        return LocalDate.parse(value.toString().substring(0, 10));
    }
}
