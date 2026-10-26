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
package com.micropay.fraud.engine.store;

import com.micropay.fraud.engine.event.AccountType;
import com.micropay.fraud.engine.event.TransactionEvent;
import java.util.List;

/**
 * Whose behaviour a transaction is compared with: the client when known, so activity across all of a client's accounts
 * counts together, otherwise the account itself.
 */
public record Subject(String tenantId, Long clientId, AccountType accountType, Long accountId) {

    public static Subject of(TransactionEvent event) {
        return new Subject(event.tenantId(), event.clientId(), event.accountType(), event.accountId());
    }

    public boolean isClient() {
        return clientId != null;
    }

    String whereClause() {
        return isClient() ? "tenant_id = ? AND client_id = ?" : "tenant_id = ? AND account_type = ? AND account_id = ?";
    }

    List<Object> whereParams() {
        return isClient() ? List.of(tenantId, clientId) : List.of(tenantId, accountType.name(), accountId);
    }
}
