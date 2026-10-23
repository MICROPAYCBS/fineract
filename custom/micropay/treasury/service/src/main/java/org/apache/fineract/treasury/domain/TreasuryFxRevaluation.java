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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.treasury.fx.FxRevaluationStatus;

/**
 * A revaluation of a foreign currency GL balance in an office on a date: the balance, the rate, the target, the carried
 * base and what was posted.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_fx_revaluation")
public class TreasuryFxRevaluation extends AbstractPersistableCustom<Long> {

    @Column(name = "config_id", nullable = false)
    private Long configId;

    @Column(name = "gl_account_id", nullable = false)
    private Long glAccountId;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "revaluation_date", nullable = false)
    private LocalDate revaluationDate;

    @Column(name = "fc_balance", nullable = false, precision = 19, scale = 6)
    private BigDecimal fcBalance;

    @Column(name = "rate", nullable = false, precision = 24, scale = 10)
    private BigDecimal rate;

    @Column(name = "rate_id")
    private Long rateId;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    /** The target: the balance at the closing rate. */
    @Column(name = "base_equivalent", nullable = false, precision = 19, scale = 6)
    private BigDecimal baseEquivalent;

    /** The movements, each at the rate of its own date. */
    @Column(name = "movements_base", nullable = false, precision = 19, scale = 6)
    private BigDecimal movementsBase;

    @Column(name = "prior_adjustments", nullable = false, precision = 19, scale = 6)
    private BigDecimal priorAdjustments;

    @Column(name = "carried_base", nullable = false, precision = 19, scale = 6)
    private BigDecimal carriedBase;

    @Column(name = "adjustment", nullable = false, precision = 19, scale = 6)
    private BigDecimal adjustment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FxRevaluationStatus status;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "reversal_transaction_id", length = 100)
    private String reversalTransactionId;

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reversed_on_utc")
    private OffsetDateTime reversedOnUtc;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_on_utc", nullable = false)
    private OffsetDateTime createdOnUtc;
}
