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
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * An imported bank or wallet statement of one treasury account.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_statement")
public class TreasuryStatement extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "treasury_account_id", nullable = false)
    private Long treasuryAccountId;

    @Column(name = "format_id", nullable = false)
    private Long formatId;

    @Column(name = "statement_reference", length = 100)
    private String statementReference;

    @Column(name = "file_name", length = 255)
    private String fileName;

    /** SHA-256 of the file content, to refuse the same file twice. */
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 6)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", nullable = false, precision = 19, scale = 6)
    private BigDecimal closingBalance;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TreasuryStatementStatus status;

    @Column(name = "imported_by", nullable = false)
    private Long importedBy;

    @Column(name = "imported_on_date", nullable = false)
    private LocalDate importedOnDate;
}
