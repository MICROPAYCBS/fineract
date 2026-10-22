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
 * A funding transfer between two treasury accounts in the same currency. Sending posts the source to cash in transit
 * and receiving posts cash in transit to the destination, so cash sent but not received is counted once.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_transfer")
public class TreasuryTransfer extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "from_account_id", nullable = false)
    private Long fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private Long toAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TreasuryTransferStatus status;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_on_date")
    private LocalDate approvedOnDate;

    @Column(name = "sent_by")
    private Long sentBy;

    @Column(name = "sent_on_date")
    private LocalDate sentOnDate;

    @Column(name = "send_transaction_id", length = 50)
    private String sendTransactionId;

    @Column(name = "received_by")
    private Long receivedBy;

    @Column(name = "received_on_date")
    private LocalDate receivedOnDate;

    @Column(name = "receive_transaction_id", length = 50)
    private String receiveTransactionId;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_on_date")
    private LocalDate cancelledOnDate;

    /** Cash-in-transit GL account used at send; receive clears the same account. */
    @Column(name = "in_transit_gl_account_id")
    private Long inTransitGlAccountId;

    @Column(name = "note", length = 500)
    private String note;
}
