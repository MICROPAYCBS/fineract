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
package org.apache.fineract.infrastructure.accountnumberformat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_account_number_sequence", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "scope_key" }, name = "uk_account_number_sequence_scope_key") })
@Getter
@Setter
@NoArgsConstructor
public class AccountNumberSequence extends AbstractPersistableCustom<Long> {

    @Column(name = "scope_key", length = 100, nullable = false, unique = true)
    private String scopeKey;

    @Column(name = "last_value", nullable = false)
    private Long lastValue = 0L;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public AccountNumberSequence(final String scopeKey) {
        this.scopeKey = scopeKey;
        this.lastValue = 0L;
    }
}
