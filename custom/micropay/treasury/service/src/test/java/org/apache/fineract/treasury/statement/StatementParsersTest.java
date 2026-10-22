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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatementParsersTest {

    private static final CsvStatementFormat STANBIC = new CsvStatementFormat(',', 2, "dd/MM/yyyy", '.', "Date", "Value Date", "Description",
            "Reference", "Debit", "Credit", null, "Balance", false);
    private static final CsvStatementFormat WALLET = new CsvStatementFormat(';', 0, "yyyy-MM-dd", ',', "1", null, "2", "3", null, null, "4",
            null, false);

    @Test
    void csvWithDebitAndCreditColumns() {
        final ParsedStatement statement = CsvStatementParser.parse(read("stanbic_debit_credit.csv"), STANBIC);

        assertEquals(3, statement.lines().size());
        final ParsedStatement.Line first = statement.lines().get(0);
        assertEquals(LocalDate.of(2026, 10, 15), first.transactionDate());
        assertEquals(0, new BigDecimal("1000000").compareTo(first.amount()), "a credit is money in");
        assertEquals("Opening transfer, Head Office", first.description());
        assertEquals("HO-001", first.reference());
        assertEquals(0, new BigDecimal("6000000").compareTo(first.balance()));
        final ParsedStatement.Line charge = statement.lines().get(1);
        assertEquals(0, new BigDecimal("-5500").compareTo(charge.amount()), "a debit is money out");
        assertEquals(LocalDate.of(2026, 10, 16), charge.valueDate());
        assertEquals("Vault top-up \"Kampala\"", statement.lines().get(2).description());
        assertEquals(LocalDate.of(2026, 10, 15), statement.periodFrom());
        assertEquals(LocalDate.of(2026, 10, 16), statement.periodTo());
        assertNull(statement.openingBalance());
    }

    @Test
    void csvWithSignedAmountsAndDecimalComma() {
        final ParsedStatement statement = CsvStatementParser.parse(read("wallet_signed.csv"), WALLET);

        assertEquals(0, new BigDecimal("1500.50").compareTo(statement.lines().get(0).amount()));
        assertEquals(0, new BigDecimal("-700.25").compareTo(statement.lines().get(1).amount()));
        assertEquals(LocalDate.of(2026, 10, 14), statement.lines().get(0).valueDate(), "value date defaults to the date");
    }

    @Test
    void csvInvertedAmounts() {
        final CsvStatementFormat inverted = new CsvStatementFormat(';', 0, "yyyy-MM-dd", ',', "1", null, "2", "3", null, null, "4", null,
                true);

        assertEquals(0, new BigDecimal("-1500.50")
                .compareTo(CsvStatementParser.parse(read("wallet_signed.csv"), inverted).lines().get(0).amount()));
    }

    @Test
    void csvBadDateNamesTheLine() {
        final StatementParseException e = assertThrows(StatementParseException.class,
                () -> CsvStatementParser.parse(read("bad_date.csv"), new CsvStatementFormat(',', 1, "dd/MM/yyyy", '.', "Date", "Value Date",
                        "Description", "Reference", "Debit", "Credit", null, "Balance", false)));
        assertEquals("error.msg.treasury.statement.date.invalid", e.getGlobalisationMessageCode());
        assertEquals("Line 2: the date \"2026-10-15\" does not match the format", e.getDefaultUserMessage());
    }

    @Test
    void csvLineWithBothDebitAndCreditIsRefused() {
        final StatementParseException e = assertThrows(StatementParseException.class,
                () -> CsvStatementParser.parse(read("bad_both_sides.csv"),
                        new CsvStatementFormat(',', 1, "dd/MM/yyyy", '.', "Date", null, null, null, "Debit", "Credit", null, null, false)));
        assertEquals("error.msg.treasury.statement.amount.both", e.getGlobalisationMessageCode());
    }

    @Test
    void csvUnknownHeaderColumnIsRefused() {
        final StatementParseException e = assertThrows(StatementParseException.class, () -> CsvStatementParser.parse(
                read("stanbic_debit_credit.csv"),
                new CsvStatementFormat(',', 2, "dd/MM/yyyy", '.', "Posting Date", null, null, null, "Debit", "Credit", null, null, false)));
        assertEquals("error.msg.treasury.statement.column.not.found", e.getGlobalisationMessageCode());
    }

    @Test
    void csvNumbersInBracketsAreNegative() {
        assertEquals(0, new BigDecimal("-1234.5").compareTo(CsvStatementParser.number("(1,234.50)", '.', 1, "amount")));
        assertEquals(0, new BigDecimal("-12").compareTo(CsvStatementParser.number("12-", '.', 1, "amount")));
        assertThrows(StatementParseException.class, () -> CsvStatementParser.number("n/a", '.', 1, "amount"));
    }

    @Test
    void mt940IsRead() {
        final ParsedStatement statement = Mt940StatementParser.parse(read("sample.sta"));

        assertEquals("STMT-2026-10-16", statement.statementReference());
        assertEquals("9030012345678", statement.accountIdentification());
        assertEquals("UGX", statement.currencyCode());
        assertEquals(0, new BigDecimal("5000000").compareTo(statement.openingBalance()));
        assertEquals(0, new BigDecimal("6244500").compareTo(statement.closingBalance()));
        final List<ParsedStatement.Line> lines = statement.lines();
        assertEquals(3, lines.size());
        assertEquals(0, new BigDecimal("1000000").compareTo(lines.get(0).amount()));
        assertEquals("HO-001", lines.get(0).reference());
        assertEquals("Funds from Head Office Transfer from MICROPAY HQ to branch account", lines.get(0).description());
        assertEquals(0, new BigDecimal("-5500").compareTo(lines.get(1).amount()));
        assertEquals("CHG-77", lines.get(1).reference(), "NONREF falls back to the bank reference");
        assertEquals(0, new BigDecimal("250000").compareTo(lines.get(2).amount()), "RD reverses a debit: money in");
        assertEquals(LocalDate.of(2026, 10, 16), lines.get(2).transactionDate());
    }

    @Test
    void mt940BalancesAddUp() {
        final StatementBalanceCheck.Balances balances = StatementBalanceCheck.check(Mt940StatementParser.parse(read("sample.sta")), null,
                null);

        assertEquals(0, new BigDecimal("6244500").compareTo(balances.closing()));
    }

    @Test
    void mt940WrongClosingBalanceIsRefused() {
        final StatementParseException e = assertThrows(StatementParseException.class,
                () -> StatementBalanceCheck.check(Mt940StatementParser.parse(read("bad_closing.sta")), null, null));
        assertEquals("error.msg.treasury.statement.closing.balance.mismatch", e.getGlobalisationMessageCode());
    }

    @Test
    void mt940MixedCurrenciesAreRefused() {
        assertEquals("error.msg.treasury.statement.mt940.currency.mixed",
                assertThrows(StatementParseException.class, () -> Mt940StatementParser.parse(read("mixed_currency.sta")))
                        .getGlobalisationMessageCode());
    }

    @Test
    void mt940UnreadableLineIsRefused() {
        assertEquals("error.msg.treasury.statement.mt940.line.invalid",
                assertThrows(StatementParseException.class, () -> Mt940StatementParser.parse(read("bad_line.sta")))
                        .getGlobalisationMessageCode());
    }

    @Test
    void csvIsNotMt940() {
        assertEquals("error.msg.treasury.statement.mt940.not.mt940",
                assertThrows(StatementParseException.class, () -> Mt940StatementParser.parse(read("wallet_signed.csv")))
                        .getGlobalisationMessageCode());
    }

    @Test
    void mt940EntryDateAcrossTheYearEnd() {
        assertEquals(LocalDate.of(2027, 1, 2), Mt940StatementParser.entryDate(LocalDate.of(2026, 12, 31), "0102", 1));
        assertEquals(LocalDate.of(2025, 12, 31), Mt940StatementParser.entryDate(LocalDate.of(2026, 1, 1), "1231", 1));
    }

    @Test
    void csvBalancesComeFromTheRunningBalance() {
        final StatementBalanceCheck.Balances balances = StatementBalanceCheck
                .check(CsvStatementParser.parse(read("stanbic_debit_credit.csv"), STANBIC), null, null);

        assertEquals(0, new BigDecimal("5000000").compareTo(balances.opening()));
        assertEquals(0, new BigDecimal("5744500").compareTo(balances.closing()));
    }

    @Test
    void csvWrongRunningBalanceNamesTheLine() {
        final StatementParseException e = assertThrows(StatementParseException.class,
                () -> StatementBalanceCheck.check(CsvStatementParser.parse(read("bad_running_balance.csv"), new CsvStatementFormat(',', 1,
                        "dd/MM/yyyy", '.', "Date", null, "Description", "Reference", "Debit", "Credit", null, "Balance", false)), null,
                        null));
        assertEquals("error.msg.treasury.statement.running.balance.mismatch", e.getGlobalisationMessageCode());
        assertEquals("Line 3: the balance 5060.00 should be 5050.00 (previous balance plus -50.00)", e.getDefaultUserMessage());
    }

    @Test
    void csvWithoutBalancesNeedsThemFromTheRequest() {
        final ParsedStatement statement = CsvStatementParser.parse(read("wallet_signed.csv"), WALLET);

        assertEquals("error.msg.treasury.statement.balances.missing",
                assertThrows(StatementParseException.class, () -> StatementBalanceCheck.check(statement, null, null))
                        .getGlobalisationMessageCode());
        assertEquals(0, new BigDecimal("10800.25")
                .compareTo(StatementBalanceCheck.check(statement, new BigDecimal("10000"), new BigDecimal("10800.25")).closing()));
        assertThrows(StatementParseException.class,
                () -> StatementBalanceCheck.check(statement, new BigDecimal("10000"), new BigDecimal("10800")));
    }

    @Test
    void requestBalanceMustAgreeWithTheFile() {
        assertEquals("error.msg.treasury.statement.opening.balance.mismatch",
                assertThrows(StatementParseException.class,
                        () -> StatementBalanceCheck.check(Mt940StatementParser.parse(read("sample.sta")), new BigDecimal("1"), null))
                        .getGlobalisationMessageCode());
    }

    @Test
    void lineKeysTellRepeatedLinesApart() {
        final ParsedStatement.Line charge = new ParsedStatement.Line(1, LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16),
                new BigDecimal("-500.00"), "SMS fee", null, null);
        final ParsedStatement.Line sameCharge = new ParsedStatement.Line(2, LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16),
                new BigDecimal("-500"), "SMS fee", null, null);

        final List<String> keys = StatementFiles.lineKeys(List.of(charge, sameCharge));
        assertNotEquals(keys.get(0), keys.get(1), "two equal charges in one file are two lines");
        assertEquals(keys.get(0), StatementFiles.lineKeys(List.of(sameCharge)).get(0),
                "but the first matches the same line in another file");
    }

    @Test
    void fileTextFallsBackToLatin1() {
        assertEquals("Café", StatementFiles.text("Café".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Café", StatementFiles.text("Café".getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals(64, StatementFiles.sha256(new byte[] { 1, 2, 3 }).length());
    }

    private static String read(final String name) {
        try (InputStream in = StatementParsersTest.class.getResourceAsStream("/statements/" + name + ".txt")) {
            return StatementFiles.text(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
