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
package org.apache.fineract.treasury.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.treasury.statement.CsvStatementFormat;

/**
 * An import profile: how to read one bank's or wallet's statement file. MT940 needs no settings; CSV needs the
 * delimiter, header rows, date format, decimal separator and the columns.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_statement_format")
public class TreasuryStatementFormat extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "format_type", nullable = false, length = 20)
    private TreasuryStatementFormatType formatType;

    @Column(name = "delimiter_char", length = 1)
    private String delimiter;

    @Column(name = "header_rows", nullable = false)
    private int headerRows;

    @Column(name = "date_format", length = 30)
    private String dateFormat;

    @Column(name = "decimal_separator", length = 1)
    private String decimalSeparator;

    @Column(name = "date_column", length = 100)
    private String dateColumn;

    @Column(name = "value_date_column", length = 100)
    private String valueDateColumn;

    @Column(name = "description_column", length = 100)
    private String descriptionColumn;

    @Column(name = "reference_column", length = 100)
    private String referenceColumn;

    @Column(name = "debit_column", length = 100)
    private String debitColumn;

    @Column(name = "credit_column", length = 100)
    private String creditColumn;

    @Column(name = "amount_column", length = 100)
    private String amountColumn;

    @Column(name = "balance_column", length = 100)
    private String balanceColumn;

    @Column(name = "invert_amount", nullable = false)
    private boolean invertAmount;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public CsvStatementFormat toCsvFormat() {
        return new CsvStatementFormat(charOr(this.delimiter, ','), this.headerRows, this.dateFormat, charOr(this.decimalSeparator, '.'),
                this.dateColumn, this.valueDateColumn, this.descriptionColumn, this.referenceColumn, this.debitColumn, this.creditColumn,
                this.amountColumn, this.balanceColumn, this.invertAmount);
    }

    private static char charOr(final String value, final char fallback) {
        return value == null || value.isEmpty() ? fallback : value.charAt(0);
    }
}
