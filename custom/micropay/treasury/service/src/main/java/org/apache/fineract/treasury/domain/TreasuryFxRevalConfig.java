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
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * How a foreign currency GL account is revalued: the base currency adjustment account and the unrealised gain and loss
 * accounts.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_fx_reval_config")
public class TreasuryFxRevalConfig extends AbstractPersistableCustom<Long> {

    @Column(name = "gl_account_id", nullable = false)
    private Long glAccountId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** Holds the base currency revaluation of the foreign currency balance. */
    @Column(name = "adjustment_gl_account_id", nullable = false)
    private Long adjustmentGlAccountId;

    @Column(name = "gain_gl_account_id", nullable = false)
    private Long gainGlAccountId;

    @Column(name = "loss_gl_account_id", nullable = false)
    private Long lossGlAccountId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "last_modified_on_utc")
    private OffsetDateTime lastModifiedOnUtc;
}
