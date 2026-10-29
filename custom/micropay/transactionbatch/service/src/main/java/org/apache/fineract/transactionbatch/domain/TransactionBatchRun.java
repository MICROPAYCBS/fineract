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
@Table(name = "m_txn_batch_run")
public class TransactionBatchRun extends AbstractPersistableCustom<Long> {

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "run_no", nullable = false)
    private Integer runNo;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "posted_count", nullable = false)
    private Integer postedCount;

    @Column(name = "suspended_count", nullable = false)
    private Integer suspendedCount;

    @Column(name = "started_on_utc", nullable = false)
    private OffsetDateTime startedOnUtc;

    @Column(name = "completed_on_utc")
    private OffsetDateTime completedOnUtc;

    @Column(name = "run_by")
    private Long runBy;

    public boolean isCompleted() {
        return TransactionBatchRunStatus.COMPLETED.getValue().equals(this.status);
    }
}
