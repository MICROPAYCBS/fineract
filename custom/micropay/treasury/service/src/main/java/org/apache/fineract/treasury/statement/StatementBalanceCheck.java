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

/**
 * Checks that a statement adds up: opening balance plus every line equals the closing balance, and each running balance
 * in the file equals the one before plus the line. Opening and closing balances come from the file, else from the
 * request, else from the running balances of the first and last line.
 */
public final class StatementBalanceCheck {

    private StatementBalanceCheck() {}

    public record Balances(BigDecimal opening, BigDecimal closing) {
    }

    public static Balances check(final ParsedStatement statement, final BigDecimal requestOpening, final BigDecimal requestClosing) {
        final ParsedStatement.Line first = statement.lines().getFirst();
        final ParsedStatement.Line last = statement.lines().getLast();
        BigDecimal opening = statement.openingBalance() != null ? statement.openingBalance() : requestOpening;
        if (opening == null && first.balance() != null) {
            opening = first.balance().subtract(first.amount());
        }
        BigDecimal closing = statement.closingBalance() != null ? statement.closingBalance() : requestClosing;
        if (closing == null && last.balance() != null) {
            closing = last.balance();
        }
        if (opening == null || closing == null) {
            throw new StatementParseException("balances.missing",
                    "The statement has no opening and closing balance; send openingBalance and closingBalance, or use a format with a balance column");
        }
        if (statement.openingBalance() != null && requestOpening != null && requestOpening.compareTo(statement.openingBalance()) != 0) {
            throw new StatementParseException("opening.balance.mismatch", "The openingBalance sent (" + requestOpening.toPlainString()
                    + ") differs from the file's (" + statement.openingBalance().toPlainString() + ")", requestOpening,
                    statement.openingBalance());
        }
        if (statement.closingBalance() != null && requestClosing != null && requestClosing.compareTo(statement.closingBalance()) != 0) {
            throw new StatementParseException("closing.balance.mismatch", "The closingBalance sent (" + requestClosing.toPlainString()
                    + ") differs from the file's (" + statement.closingBalance().toPlainString() + ")", requestClosing,
                    statement.closingBalance());
        }

        BigDecimal running = opening;
        for (final ParsedStatement.Line line : statement.lines()) {
            running = running.add(line.amount());
            if (line.balance() != null && line.balance().compareTo(running) != 0) {
                throw StatementParseException.atLine(line.lineNumber(), "running.balance.mismatch",
                        "the balance " + line.balance().toPlainString() + " should be " + running.toPlainString()
                                + " (previous balance plus " + line.amount().toPlainString() + ")");
            }
        }
        if (running.compareTo(closing) != 0) {
            throw new StatementParseException("closing.balance.mismatch",
                    "Opening balance " + opening.toPlainString() + " plus the lines (" + running.subtract(opening).toPlainString() + ") is "
                            + running.toPlainString() + ", not the closing balance " + closing.toPlainString(),
                    opening, closing, running);
        }
        return new Balances(opening, closing);
    }
}
