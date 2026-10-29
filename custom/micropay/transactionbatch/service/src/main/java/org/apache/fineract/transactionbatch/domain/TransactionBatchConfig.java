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
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The single tenant row of batch settings (id is always 1). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_txn_batch_config")
public class TransactionBatchConfig {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "code_prefix", nullable = false, length = 10)
    private String codePrefix;

    @Column(name = "import_row_cap", nullable = false)
    private Integer importRowCap;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_on_utc")
    private OffsetDateTime createdOnUtc;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "last_modified_on_utc")
    private OffsetDateTime lastModifiedOnUtc;

    @Version
    @Column(name = "version")
    private Integer version;
}
