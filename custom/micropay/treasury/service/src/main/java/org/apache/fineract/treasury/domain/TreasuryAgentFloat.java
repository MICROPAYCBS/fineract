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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * An agent's float: the client, the savings account that holds the float, its minimum and maximum, and the treasury
 * account on the other side of top-ups and sweeps.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_agent_float")
public class TreasuryAgentFloat extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "savings_account_id", nullable = false)
    private Long savingsAccountId;

    @Column(name = "treasury_account_id", nullable = false)
    private Long treasuryAccountId;

    @Column(name = "min_float", precision = 19, scale = 6)
    private BigDecimal minFloat;

    @Column(name = "max_float", precision = 19, scale = 6)
    private BigDecimal maxFloat;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TreasuryAgentFloatStatus status;

    public boolean isActive() {
        return this.status == TreasuryAgentFloatStatus.ACTIVE;
    }
}
