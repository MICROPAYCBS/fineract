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
import java.util.List;

/**
 * A statement as read from a file, before it is checked or stored. Amounts are signed from the account holder's side:
 * positive is money into the account (a bank credit, our GL debit), negative is money out.
 *
 * @param currencyCode
 *            from the file when it has one (MT940), else {@code null}
 * @param openingBalance
 *            from the file, or {@code null} when the format has none
 * @param closingBalance
 *            from the file, or {@code null} when the format has none
 */
public record ParsedStatement(String statementReference, String accountIdentification, String currencyCode, BigDecimal openingBalance,
        BigDecimal closingBalance, List<Line> lines) {

    /**
     * @param lineNumber
     *            1-based position in the file (row for CSV, entry for MT940)
     * @param balance
     *            running balance after the line, when the file has one
     */
    public record Line(int lineNumber, LocalDate transactionDate, LocalDate valueDate, BigDecimal amount, String description,
            String reference, BigDecimal balance) {
    }

    public LocalDate periodFrom() {
        return this.lines.stream().map(Line::transactionDate).min(LocalDate::compareTo).orElse(null);
    }

    public LocalDate periodTo() {
        return this.lines.stream().map(Line::transactionDate).max(LocalDate::compareTo).orElse(null);
    }
}
