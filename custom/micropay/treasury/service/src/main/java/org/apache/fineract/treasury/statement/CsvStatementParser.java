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
package org.apache.fineract.treasury.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;

/**
 * Reads a CSV statement with a {@link CsvStatementFormat}. Quoted fields (with doubled quotes inside) and blank rows
 * are handled; thousands separators and currency symbols around a number are ignored. CSV exports carry no opening or
 * closing balance, so both are left {@code null} for the caller to take from the request or the balance column.
 */
public final class CsvStatementParser {

    private CsvStatementParser() {}

    public static ParsedStatement parse(final String content, final CsvStatementFormat format) {
        final List<List<String>> rows = rows(content, format.delimiter());
        if (rows.size() <= format.headerRows()) {
            throw new StatementParseException("empty", "The statement has no lines after its " + format.headerRows() + " header rows");
        }
        final List<String> header = format.headerRows() > 0 ? rows.get(format.headerRows() - 1) : List.of();
        final int dateIndex = column(format.dateColumn(), header, "date", true);
        final int valueDateIndex = column(format.valueDateColumn(), header, "valueDate", false);
        final int descriptionIndex = column(format.descriptionColumn(), header, "description", false);
        final int referenceIndex = column(format.referenceColumn(), header, "reference", false);
        final int debitIndex = column(format.debitColumn(), header, "debit", false);
        final int creditIndex = column(format.creditColumn(), header, "credit", false);
        final int amountIndex = column(format.amountColumn(), header, "amount", false);
        final int balanceIndex = column(format.balanceColumn(), header, "balance", false);
        if (amountIndex < 0 && (debitIndex < 0 || creditIndex < 0)) {
            throw new StatementParseException("format.no.amount", "The format needs an amount column, or both a debit and a credit column");
        }
        final DateTimeFormatter dates = dateFormatter(format.dateFormat());

        final List<ParsedStatement.Line> lines = new ArrayList<>();
        for (int i = format.headerRows(); i < rows.size(); i++) {
            final List<String> row = rows.get(i);
            final int lineNumber = i + 1;
            if (row.stream().allMatch(StringUtils::isBlank)) {
                continue;
            }
            final LocalDate date = date(cell(row, dateIndex), dates, lineNumber, "date");
            final String valueDateText = cell(row, valueDateIndex);
            final LocalDate valueDate = StringUtils.isBlank(valueDateText) ? date : date(valueDateText, dates, lineNumber, "value date");
            BigDecimal amount;
            if (amountIndex >= 0) {
                amount = number(cell(row, amountIndex), format.decimalSeparator(), lineNumber, "amount");
                if (amount == null) {
                    throw StatementParseException.atLine(lineNumber, "amount.missing", "the amount is empty");
                }
                if (format.invertAmount()) {
                    amount = amount.negate();
                }
            } else {
                final BigDecimal debit = number(cell(row, debitIndex), format.decimalSeparator(), lineNumber, "debit");
                final BigDecimal credit = number(cell(row, creditIndex), format.decimalSeparator(), lineNumber, "credit");
                if (debit == null && credit == null) {
                    throw StatementParseException.atLine(lineNumber, "amount.missing", "both debit and credit are empty");
                }
                if (debit != null && credit != null && debit.signum() != 0 && credit.signum() != 0) {
                    throw StatementParseException.atLine(lineNumber, "amount.both", "both debit and credit are filled in");
                }
                // a debit on the statement is money out of the account, a credit is money in
                amount = credit != null && credit.signum() != 0 ? credit.abs() : debit == null ? BigDecimal.ZERO : debit.abs().negate();
            }
            if (amount.signum() == 0) {
                throw StatementParseException.atLine(lineNumber, "amount.zero", "the amount is zero");
            }
            lines.add(new ParsedStatement.Line(lineNumber, date, valueDate, amount, StringUtils.trimToNull(cell(row, descriptionIndex)),
                    StringUtils.trimToNull(cell(row, referenceIndex)),
                    number(cell(row, balanceIndex), format.decimalSeparator(), lineNumber, "balance")));
        }
        if (lines.isEmpty()) {
            throw new StatementParseException("empty", "The statement has no lines");
        }
        return new ParsedStatement(null, null, null, null, null, lines);
    }

    /** RFC 4180 rows: quoted fields may hold the delimiter, line breaks and doubled quotes. */
    static List<List<String>> rows(final String content, final char delimiter) {
        final List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        final StringBuilder field = new StringBuilder();
        boolean quoted = false;
        final String text = content.startsWith("﻿") ? content.substring(1) : content;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static int column(final String reference, final List<String> header, final String name, final boolean required) {
        if (StringUtils.isBlank(reference)) {
            if (required) {
                throw new StatementParseException("format.no." + name, "The format has no " + name + " column");
            }
            return -1;
        }
        final String trimmed = reference.trim();
        if (StringUtils.isNumeric(trimmed)) {
            final int index = Integer.parseInt(trimmed) - 1;
            if (index < 0) {
                throw new StatementParseException("format.bad.column", "Column " + trimmed + " for " + name + " must be 1 or more");
            }
            return index;
        }
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).trim().equalsIgnoreCase(trimmed)) {
                return i;
            }
        }
        throw new StatementParseException("column.not.found", "The " + name + " column \"" + trimmed + "\" is not in the header row", name,
                trimmed);
    }

    private static String cell(final List<String> row, final int index) {
        return index < 0 || index >= row.size() ? null : row.get(index);
    }

    private static DateTimeFormatter dateFormatter(final String pattern) {
        try {
            return DateTimeFormatter.ofPattern(StringUtils.defaultIfBlank(pattern, "yyyy-MM-dd"), Locale.ENGLISH);
        } catch (IllegalArgumentException e) {
            throw new StatementParseException("format.bad.date.format", "The date format \"" + pattern + "\" is not valid", pattern, e);
        }
    }

    private static LocalDate date(final String text, final DateTimeFormatter formatter, final int lineNumber, final String what) {
        if (StringUtils.isBlank(text)) {
            throw StatementParseException.atLine(lineNumber, "date.missing", "the " + what + " is empty");
        }
        try {
            return LocalDate.parse(text.trim(), formatter);
        } catch (DateTimeParseException e) {
            throw StatementParseException.atLine(lineNumber, "date.invalid",
                    "the " + what + " \"" + text.trim() + "\" does not match the format", e);
        }
    }

    /**
     * Keeps digits, the decimal separator and a leading or trailing minus (or brackets for negatives); drops thousands
     * separators, spaces and currency symbols.
     */
    static BigDecimal number(final String text, final char decimalSeparator, final int lineNumber, final String what) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        final String trimmed = text.trim();
        final boolean negative = trimmed.startsWith("-") || trimmed.endsWith("-") || (trimmed.startsWith("(") && trimmed.endsWith(")"));
        final StringBuilder digits = new StringBuilder();
        for (final char c : trimmed.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (c == decimalSeparator) {
                digits.append('.');
            }
        }
        if (digits.isEmpty() || ".".contentEquals(digits)) {
            throw StatementParseException.atLine(lineNumber, "number.invalid", "the " + what + " \"" + trimmed + "\" is not a number");
        }
        try {
            final BigDecimal value = new BigDecimal(digits.toString());
            return negative ? value.negate() : value;
        } catch (NumberFormatException e) {
            throw StatementParseException.atLine(lineNumber, "number.invalid", "the " + what + " \"" + trimmed + "\" is not a number", e);
        }
    }
}
