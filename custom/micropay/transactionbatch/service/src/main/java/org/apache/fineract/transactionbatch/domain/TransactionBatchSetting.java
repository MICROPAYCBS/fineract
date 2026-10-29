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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One clearing GL and one suspense GL per currency. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_txn_batch_setting")
public class TransactionBatchSetting {

    @Id
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "clearing_gl_account_id", nullable = false)
    private Long clearingGlAccountId;

    @Column(name = "suspense_gl_account_id", nullable = false)
    private Long suspenseGlAccountId;
}
