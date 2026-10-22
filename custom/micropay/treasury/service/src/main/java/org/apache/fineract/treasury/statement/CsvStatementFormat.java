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

/**
 * How to read one bank's CSV export. Column references are 1-based numbers or, when the file has a header row, the
 * header text (case-insensitive). The amount comes either from one signed {@code amountColumn} or from separate
 * {@code debitColumn} (money out) and {@code creditColumn} (money in).
 *
 * @param invertAmount
 *            for exports that show money in as negative in the amount column
 */
public record CsvStatementFormat(char delimiter, int headerRows, String dateFormat, char decimalSeparator, String dateColumn,
        String valueDateColumn, String descriptionColumn, String referenceColumn, String debitColumn, String creditColumn,
        String amountColumn, String balanceColumn, boolean invertAmount) {
}
