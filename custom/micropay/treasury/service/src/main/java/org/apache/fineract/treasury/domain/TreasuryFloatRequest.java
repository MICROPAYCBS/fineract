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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * A top-up or sweep of an agent's float. Executing it makes the upstream savings deposit or withdrawal, whose
 * transaction id is kept here.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_float_request")
public class TreasuryFloatRequest extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "agent_float_id", nullable = false)
    private Long agentFloatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private TreasuryFloatRequestType requestType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TreasuryFloatRequestStatus status;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_on_date")
    private LocalDate approvedOnDate;

    @Column(name = "executed_by")
    private Long executedBy;

    @Column(name = "executed_on_date")
    private LocalDate executedOnDate;

    @Column(name = "savings_transaction_id")
    private Long savingsTransactionId;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_on_date")
    private LocalDate cancelledOnDate;

    @Column(name = "note", length = 500)
    private String note;
}
