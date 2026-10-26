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
package com.micropay.fraud.engine;

import com.micropay.fraud.engine.event.AccountType;
import com.micropay.fraud.engine.event.TransactionEvent;
import com.micropay.fraud.engine.event.TransactionKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TestEvents {

    public static final String TENANT = "default";

    private TestEvents() {}

    public static TransactionEvent savings(TransactionKind kind, long accountId, Long clientId, long amount, Instant at) {
        return new TransactionEvent(TENANT, UUID.randomUUID().toString(),
                kind == TransactionKind.DEPOSIT ? "SavingsDepositBusinessEvent" : "SavingsWithdrawalBusinessEvent", kind,
                AccountType.SAVINGS, accountId, clientId, null, BigDecimal.valueOf(amount), "UGX", at, null, "Mobile Money", false);
    }
}
