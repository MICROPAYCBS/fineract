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
package org.apache.fineract.treasury.service;

import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.domain.TreasuryDealEvent;
import org.apache.fineract.treasury.domain.TreasuryDealEventRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEventType;

/** Writes a deal's history line, dated the business date. */
final class DealEvents {

    private DealEvents() {}

    static void record(final TreasuryDealEventRepository repository, final Long dealId, final TreasuryDealEventType type, final Long userId,
            final String note, final String transactionId) {
        final TreasuryDealEvent event = new TreasuryDealEvent();
        event.setDealId(dealId);
        event.setEventType(type);
        event.setBusinessDate(DateUtils.getBusinessLocalDate());
        event.setUserId(userId);
        event.setNote(note == null ? null : note.length() > 1000 ? note.substring(0, 1000) : note);
        event.setTransactionId(transactionId);
        event.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        repository.save(event);
    }
}
