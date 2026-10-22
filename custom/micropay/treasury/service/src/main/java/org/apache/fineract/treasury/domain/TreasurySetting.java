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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * Treasury settings for one currency.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_setting")
public class TreasurySetting extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "currency_code", nullable = false, length = 3, unique = true)
    private String currencyCode;

    /** Asset detail account that holds transfers between send and receive. */
    @Column(name = "cash_in_transit_gl_account_id")
    private Long cashInTransitGlAccountId;

    /** Payment type of agent float top-ups and sweeps. */
    @Column(name = "float_payment_type_id")
    private Long floatPaymentTypeId;

    /** When false (the default), a transfer is not sent if the source GL balance is below the amount. */
    @Column(name = "allow_insufficient_balance", nullable = false)
    private boolean allowInsufficientBalance;
}
