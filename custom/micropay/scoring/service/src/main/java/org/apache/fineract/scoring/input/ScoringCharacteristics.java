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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Catalogue of built-in scoring inputs. A scorecard may also use any column of a client or loan datatable with the code
 * {@code DT:<datatable>.<column>}.
 */
public final class ScoringCharacteristics {

    private ScoringCharacteristics() {}

    public static final String DATATABLE_PREFIX = "DT:";

    public static final String CLIENT_AGE_YEARS = "CLIENT_AGE_YEARS";
    public static final String CLIENT_MONTHS_ACTIVE = "CLIENT_MONTHS_ACTIVE";
    public static final String CLIENT_LEGAL_FORM = "CLIENT_LEGAL_FORM";
    public static final String CLIENT_IS_STAFF = "CLIENT_IS_STAFF";
    public static final String CLIENT_MONTHLY_INCOME = "CLIENT_MONTHLY_INCOME";
    public static final String CLIENT_HAS_EXPIRED_ID = "CLIENT_HAS_EXPIRED_ID";
    public static final String CLIENT_BLACKLISTED_FOR_LOANS = "CLIENT_BLACKLISTED_FOR_LOANS";
    public static final String CLIENT_CLASS_NOT_LOAN_ELIGIBLE = "CLIENT_CLASS_NOT_LOAN_ELIGIBLE";
    public static final String CLIENT_ACTIVE_LOANS = "CLIENT_ACTIVE_LOANS";
    public static final String CLIENT_CLOSED_LOANS = "CLIENT_CLOSED_LOANS";
    public static final String CLIENT_OUTSTANDING_PRINCIPAL = "CLIENT_OUTSTANDING_PRINCIPAL";
    public static final String CLIENT_CURRENT_DAYS_IN_ARREARS = "CLIENT_CURRENT_DAYS_IN_ARREARS";
    public static final String CLIENT_MAX_DAYS_LATE_HISTORY = "CLIENT_MAX_DAYS_LATE_HISTORY";
    public static final String SAVINGS_BALANCE = "SAVINGS_BALANCE";
    public static final String SAVINGS_MONTHLY_DEPOSITS_6M = "SAVINGS_MONTHLY_DEPOSITS_6M";
    public static final String SAVINGS_TRANSACTIONS_6M = "SAVINGS_TRANSACTIONS_6M";
    public static final String LOAN_PRINCIPAL = "LOAN_PRINCIPAL";
    public static final String LOAN_NUMBER_OF_REPAYMENTS = "LOAN_NUMBER_OF_REPAYMENTS";
    public static final String LOAN_PRINCIPAL_TO_MONTHLY_INCOME = "LOAN_PRINCIPAL_TO_MONTHLY_INCOME";

    public record Characteristic(String code, String dataType, String description) {
    }

    public static final List<Characteristic> BUILT_IN = List.of( //
            new Characteristic(CLIENT_AGE_YEARS, "NUMBER", "Age in whole years at the business date"),
            new Characteristic(CLIENT_MONTHS_ACTIVE, "NUMBER", "Whole months since the client was activated"),
            new Characteristic(CLIENT_LEGAL_FORM, "TEXT", "PERSON or ENTITY"),
            new Characteristic(CLIENT_IS_STAFF, "BOOLEAN", "Client has the staff flag; Micropay agents carry it"),
            new Characteristic(CLIENT_MONTHLY_INCOME, "NUMBER", "Sum of the client's active income sources, per month"),
            new Characteristic(CLIENT_HAS_EXPIRED_ID, "BOOLEAN", "An active identifier has an expiry date before the business date"),
            new Characteristic(CLIENT_BLACKLISTED_FOR_LOANS, "BOOLEAN", "Active blacklist entry that blocks loan applications"),
            new Characteristic(CLIENT_CLASS_NOT_LOAN_ELIGIBLE, "BOOLEAN", "The client's customer class is not loan eligible"),
            new Characteristic(CLIENT_ACTIVE_LOANS, "NUMBER", "Active loans, not counting the loan being scored"),
            new Characteristic(CLIENT_CLOSED_LOANS, "NUMBER", "Loans closed with obligations met"),
            new Characteristic(CLIENT_OUTSTANDING_PRINCIPAL, "NUMBER", "Outstanding principal on active loans"),
            new Characteristic(CLIENT_CURRENT_DAYS_IN_ARREARS, "NUMBER", "Days the oldest overdue installment on an active loan is late"),
            new Characteristic(CLIENT_MAX_DAYS_LATE_HISTORY, "NUMBER",
                    "Most days any paid installment was paid after its due date; missing when none was ever paid"),
            new Characteristic(SAVINGS_BALANCE, "NUMBER", "Balance of active savings accounts"),
            new Characteristic(SAVINGS_MONTHLY_DEPOSITS_6M, "NUMBER", "Deposits in the last 180 days divided by 6"),
            new Characteristic(SAVINGS_TRANSACTIONS_6M, "NUMBER", "Savings transactions in the last 180 days"),
            new Characteristic(LOAN_PRINCIPAL, "NUMBER", "Approved principal, or the proposed principal before approval"),
            new Characteristic(LOAN_NUMBER_OF_REPAYMENTS, "NUMBER", "Number of repayments of the loan"),
            new Characteristic(LOAN_PRINCIPAL_TO_MONTHLY_INCOME, "NUMBER", "Loan principal divided by monthly income"));

    public static boolean isBuiltIn(final String code) {
        return BUILT_IN.stream().anyMatch(characteristic -> characteristic.code().equals(code));
    }

    public static boolean isDatatable(final String code) {
        return code != null && code.startsWith(DATATABLE_PREFIX);
    }

    /**
     * Splits {@code DT:<datatable>.<column>} into its datatable and column, or empty when the code is malformed.
     */
    public static Optional<String[]> parseDatatable(final String code) {
        if (!isDatatable(code)) {
            return Optional.empty();
        }
        final String reference = code.substring(DATATABLE_PREFIX.length());
        final int dot = reference.indexOf('.');
        if (dot <= 0 || dot == reference.length() - 1) {
            return Optional.empty();
        }
        final String datatable = reference.substring(0, dot);
        final String column = reference.substring(dot + 1);
        if (!isSafeIdentifier(datatable) || !isSafeIdentifier(column)) {
            return Optional.empty();
        }
        return Optional.of(new String[] { datatable, column });
    }

    public static boolean isKnown(final String code) {
        return isBuiltIn(code) || parseDatatable(code).isPresent();
    }

    private static boolean isSafeIdentifier(final String identifier) {
        return identifier.matches("[A-Za-z0-9_]{1,64}");
    }

    public static BigDecimal toNumber(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal number) {
            return number;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof Boolean flag) {
            return flag ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        final String text = String.valueOf(value).trim();
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
