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
package org.apache.fineract.transactionbatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_txn_batch_item")
public class TransactionBatchItem extends AbstractPersistableCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private TransactionBatch batch;

    @Column(name = "account_type", nullable = false)
    private Integer accountType;

    @Column(name = "gl_account_id")
    private Long glAccountId;

    @Column(name = "savings_account_id")
    private Long savingsAccountId;

    @Column(name = "side", nullable = false)
    private Integer side;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Column(name = "narrative", nullable = false, length = 500)
    private String narrative;

    @Column(name = "reference", nullable = false, length = 100)
    private String reference;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;

    @Column(name = "last_modified_by", nullable = false)
    private Long lastModifiedBy;

    @Column(name = "last_modified_on_utc", nullable = false)
    private OffsetDateTime lastModifiedOnUtc;

    @Version
    @Column(name = "version")
    private Integer version;

    public TransactionBatchAccountType accountType() {
        return TransactionBatchAccountType.fromInt(this.accountType);
    }

    public TransactionBatchSide side() {
        return TransactionBatchSide.fromInt(this.side);
    }

    public boolean isDebit() {
        return TransactionBatchSide.DEBIT.getValue().equals(this.side);
    }
}
