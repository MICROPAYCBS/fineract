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
package com.micropay.fraud.engine.fineract;

import com.micropay.fraud.engine.event.TransactionEvent;
import com.micropay.fraud.engine.store.AccountOwnerRepository;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Fills in the client behind a transaction: from the event itself (transfers), from what the engine has learnt before,
 * or from Fineract. Without any of these the transaction is judged against its account's history alone.
 */
@Component
@RequiredArgsConstructor
public class AccountOwnerResolver {

    private final AccountOwnerRepository owners;
    private final FineractClient fineract;
    private final Clock clock;

    public TransactionEvent resolve(TransactionEvent event) {
        if (event.clientId() != null) {
            owners.save(event.tenantId(), event.accountType(), event.accountId(), event.clientId(), clock.instant());
            return event;
        }
        final Optional<Long> known = owners.findClientId(event.tenantId(), event.accountType(), event.accountId());
        if (known.isPresent()) {
            return event.withClientId(known.get());
        }
        final Optional<Long> looked = fineract.findClientId(event.tenantId(), event.accountType(), event.accountId());
        looked.ifPresent(clientId -> owners.save(event.tenantId(), event.accountType(), event.accountId(), clientId, clock.instant()));
        return looked.map(event::withClientId).orElse(event);
    }
}
