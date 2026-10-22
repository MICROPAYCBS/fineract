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
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * Reads a SWIFT MT940 customer statement: {@code :20:} reference, {@code :25:} account, {@code :60F:}/{@code :60M:}
 * opening balance, {@code :61:} lines with their {@code :86:} details, and {@code :62F:}/{@code :62M:} closing balance.
 * A file split over several pages (several {@code :20:} blocks) is read as one statement, from the first opening
 * balance to the last closing balance. SWIFT block wrappers ({@code {1:...}{4:} ... {@code -}}) are ignored.
 */
public final class Mt940StatementParser {

    private static final Pattern TAG = Pattern.compile("^:(\\d{2}[A-Z]?):(.*)$");
    private static final Pattern BALANCE = Pattern.compile("^([CD])(\\d{6})([A-Z]{3})(\\d+,\\d*)$");
    private static final Pattern LINE = Pattern.compile(
            "^(\\d{6})(\\d{4})?(RC|RD|C|D)([A-Z])?(\\d+,\\d*)([NSF][A-Z0-9]{3})([^\\n]*?)(?://([^\\n]*))?(?:\\n(.*))?$", Pattern.DOTALL);

    private Mt940StatementParser() {}

    private record Field(String tag, String value, int line) {
    }

    public static ParsedStatement parse(final String content) {
        final List<Field> fields = fields(content);
        String reference = null;
        String account = null;
        String currency = null;
        BigDecimal opening = null;
        BigDecimal closing = null;
        final List<ParsedStatement.Line> lines = new ArrayList<>();
        StringBuilder details = null;
        ParsedStatement.Line pending = null;

        for (final Field field : fields) {
            switch (field.tag()) {
                case "20" -> reference = reference == null ? field.value().trim() : reference;
                case "25" -> account = account == null ? field.value().trim() : account;
                case "60F", "60M" -> {
                    final Balance balance = balance(field);
                    currency = sameCurrency(currency, balance.currency(), field.line());
                    if (opening == null) {
                        opening = balance.amount();
                    }
                }
                case "61" -> {
                    if (pending != null) {
                        lines.add(withDetails(pending, details));
                    }
                    pending = line(field, lines.size() + 1);
                    details = null;
                }
                case "86" -> {
                    if (pending != null) {
                        details = details == null ? new StringBuilder() : details.append(' ');
                        details.append(oneLine(field.value()));
                    }
                }
                case "62F", "62M" -> {
                    if (pending != null) {
                        lines.add(withDetails(pending, details));
                        pending = null;
                        details = null;
                    }
                    final Balance balance = balance(field);
                    currency = sameCurrency(currency, balance.currency(), field.line());
                    closing = balance.amount();
                }
                default -> {
                    // :28C: statement number, :64: and :65: available balances and others are not needed
                }
            }
        }
        if (pending != null) {
            lines.add(withDetails(pending, details));
        }
        if (opening == null) {
            throw new StatementParseException("mt940.no.opening.balance", "The MT940 file has no :60F: opening balance");
        }
        if (closing == null) {
            throw new StatementParseException("mt940.no.closing.balance", "The MT940 file has no :62F: closing balance");
        }
        if (lines.isEmpty()) {
            throw new StatementParseException("empty", "The MT940 file has no :61: statement lines");
        }
        return new ParsedStatement(reference, account, currency, opening, closing, lines);
    }

    private static List<Field> fields(final String content) {
        final List<Field> fields = new ArrayList<>();
        final String[] rawLines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        String tag = null;
        StringBuilder value = null;
        int start = 0;
        for (int i = 0; i < rawLines.length; i++) {
            final String raw = rawLines[i];
            final String trimmed = raw.trim();
            if (trimmed.startsWith("{") || trimmed.equals("-}") || trimmed.equals("-")) {
                continue;
            }
            final Matcher matcher = TAG.matcher(raw);
            if (matcher.matches()) {
                if (tag != null) {
                    fields.add(new Field(tag, value.toString(), start));
                }
                tag = matcher.group(1);
                value = new StringBuilder(matcher.group(2));
                start = i + 1;
            } else if (tag != null && !trimmed.isEmpty()) {
                value.append('\n').append(raw);
            }
        }
        if (tag != null) {
            fields.add(new Field(tag, value.toString(), start));
        }
        if (fields.stream().noneMatch(field -> field.tag().equals("20"))) {
            throw new StatementParseException("mt940.not.mt940", "The file is not an MT940 statement: it has no :20: field");
        }
        return fields;
    }

    private record Balance(BigDecimal amount, String currency) {
    }

    private static Balance balance(final Field field) {
        final Matcher matcher = BALANCE.matcher(field.value().trim());
        if (!matcher.matches()) {
            throw StatementParseException.atLine(field.line(), "mt940.balance.invalid",
                    "the :" + field.tag() + ": balance \"" + field.value().trim() + "\" is not D/C, YYMMDD, currency and amount");
        }
        date(matcher.group(2), field.line());
        final BigDecimal amount = amount(matcher.group(4));
        return new Balance("D".equals(matcher.group(1)) ? amount.negate() : amount, matcher.group(3));
    }

    private static ParsedStatement.Line line(final Field field, final int lineNumber) {
        final Matcher matcher = LINE.matcher(field.value());
        if (!matcher.matches()) {
            throw StatementParseException.atLine(field.line(), "mt940.line.invalid",
                    "the :61: line \"" + field.value().replace('\n', ' ').trim() + "\" cannot be read");
        }
        final LocalDate valueDate = date(matcher.group(1), field.line());
        final LocalDate entryDate = matcher.group(2) == null ? valueDate : entryDate(valueDate, matcher.group(2), field.line());
        final BigDecimal amount = amount(matcher.group(5));
        if (amount.signum() == 0) {
            throw StatementParseException.atLine(field.line(), "amount.zero", "the :61: amount is zero");
        }
        // C credits the account; D debits it; RC and RD reverse a credit or a debit
        final String mark = matcher.group(3);
        final boolean moneyIn = "C".equals(mark) || "RD".equals(mark);
        final String customerReference = StringUtils.trimToNull(matcher.group(7));
        final String bankReference = StringUtils.trimToNull(matcher.group(8));
        final String reference = customerReference == null || "NONREF".equalsIgnoreCase(customerReference) ? bankReference
                : customerReference;
        final String supplementary = StringUtils.trimToNull(matcher.group(9) == null ? null : oneLine(matcher.group(9)));
        return new ParsedStatement.Line(lineNumber, entryDate, valueDate, moneyIn ? amount : amount.negate(), supplementary, reference,
                null);
    }

    private static ParsedStatement.Line withDetails(final ParsedStatement.Line line, final StringBuilder details) {
        if (details == null || details.isEmpty()) {
            return line;
        }
        final String description = line.description() == null ? details.toString() : line.description() + " " + details;
        return new ParsedStatement.Line(line.lineNumber(), line.transactionDate(), line.valueDate(), line.amount(),
                StringUtils.abbreviate(description, 500), line.reference(), line.balance());
    }

    private static String sameCurrency(final String current, final String next, final int line) {
        if (current != null && !Objects.equals(current, next)) {
            throw StatementParseException.atLine(line, "mt940.currency.mixed",
                    "the balance is in " + next + " but the statement is in " + current);
        }
        return next;
    }

    private static LocalDate date(final String yymmdd, final int line) {
        try {
            return LocalDate.of(2000 + Integer.parseInt(yymmdd.substring(0, 2)), Integer.parseInt(yymmdd.substring(2, 4)),
                    Integer.parseInt(yymmdd.substring(4, 6)));
        } catch (DateTimeException | NumberFormatException | StringIndexOutOfBoundsException e) {
            throw StatementParseException.atLine(line, "date.invalid", "\"" + yymmdd + "\" is not a YYMMDD date", e);
        }
    }

    /** The entry date carries no year: take the value date's year, or the next or previous one across a year end. */
    static LocalDate entryDate(final LocalDate valueDate, final String mmdd, final int line) {
        final LocalDate sameYear = date(String.format("%02d", valueDate.getYear() % 100) + mmdd, line);
        final long days = ChronoUnit.DAYS.between(valueDate, sameYear);
        if (days > 180) {
            return sameYear.minusYears(1);
        }
        if (days < -180) {
            return sameYear.plusYears(1);
        }
        return sameYear;
    }

    /** Continuation lines joined with single spaces. */
    private static String oneLine(final String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static BigDecimal amount(final String swiftAmount) {
        final String text = swiftAmount.replace(',', '.');
        return new BigDecimal(text.endsWith(".") ? text + "0" : text);
    }
}
