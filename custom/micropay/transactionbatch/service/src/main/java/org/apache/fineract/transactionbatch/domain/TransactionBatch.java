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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "m_txn_batch")
public class TransactionBatch extends AbstractPersistableCustom<Long> {

    @Column(name = "code", length = 20, unique = true)
    private String code;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "is_recurring", nullable = false)
    private boolean recurring;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "frequency_number")
    private Integer frequencyNumber;

    @Column(name = "frequency_unit")
    private Integer frequencyUnit;

    @Column(name = "next_posting_date")
    private LocalDate nextPostingDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_on")
    private LocalDate submittedOn;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_on")
    private LocalDate decidedOn;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "posted_on")
    private LocalDate postedOn;

    @Column(name = "copied_from_batch_id")
    private Long copiedFromBatchId;

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

    public TransactionBatchStatus status() {
        return TransactionBatchStatus.fromInt(this.status);
    }

    public boolean isInactive() {
        return TransactionBatchStatus.INACTIVE.getValue().equals(this.status);
    }

    public boolean isActiveStatus() {
        return TransactionBatchStatus.ACTIVE.getValue().equals(this.status);
    }

    public boolean isPartiallyPosted() {
        return TransactionBatchStatus.PARTIALLY_POSTED.getValue().equals(this.status);
    }
}
