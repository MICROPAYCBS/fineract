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
package com.micropay.fraud.engine.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One money movement on a savings or loan account, normalised from Fineract's Avro payloads.
 *
 * @param eventKey
 *            Fineract's idempotency key, used to process each event once
 * @param clientId
 *            the account owner when the payload names it (transfers), otherwise null until resolved
 * @param occurredAt
 *            when Fineract raised the event (UTC)
 * @param channel
 *            the payment type name, for example "Mobile Money", when Fineract has one
 */
public record TransactionEvent(String tenantId, String eventKey, String eventType, TransactionKind kind, AccountType accountType,
        Long accountId, Long clientId, Long transactionId, BigDecimal amount, String currency, Instant occurredAt, LocalDate businessDate,
        String channel, boolean transfer) implements FineractEvent {

    public TransactionEvent withClientId(Long newClientId) {
        return new TransactionEvent(tenantId, eventKey, eventType, kind, accountType, accountId, newClientId, transactionId, amount,
                currency, occurredAt, businessDate, channel, transfer);
    }
}
