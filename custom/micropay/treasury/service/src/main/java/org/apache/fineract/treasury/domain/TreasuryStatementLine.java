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
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * One line of a statement. {@code amount} is positive for money into the account (a bank credit, our GL debit) and
 * negative for money out.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_statement_line")
public class TreasuryStatementLine extends AbstractPersistableCustom<Long> {

    @Column(name = "statement_id", nullable = false)
    private Long statementId;

    @Column(name = "treasury_account_id", nullable = false)
    private Long treasuryAccountId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "reference", length = 150)
    private String reference;

    @Column(name = "running_balance", precision = 19, scale = 6)
    private BigDecimal runningBalance;

    /** Date, amount, reference and description, hashed, to refuse a line already imported in another statement. */
    @Column(name = "line_key", nullable = false, length = 64)
    private String lineKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 20)
    private TreasuryLineMatchStatus matchStatus;

    @Column(name = "match_id")
    private Long matchId;
}
