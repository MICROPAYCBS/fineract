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
package org.apache.fineract.fraud.hold.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.fraud.hold.data.FraudHoldData;
import org.apache.fineract.fraud.hold.data.FraudHoldEntityType;
import org.apache.fineract.fraud.hold.data.FraudHoldStatus;
import org.apache.fineract.fraud.hold.exception.FraudHoldNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FraudHoldReadService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;

    private final FraudHoldRepository holdRepository;

    public FraudHoldData retrieveHold(Long holdId) {
        final FraudHoldData hold = holdRepository.findById(holdId).orElseThrow(() -> new FraudHoldNotFoundException(holdId));
        return hold.withEffects(holdRepository.findEffects(holdId));
    }

    public List<FraudHoldData> retrieveHolds(FraudHoldEntityType entityType, Long entityId, FraudHoldStatus status, Integer limit) {
        final int effectiveLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return holdRepository.find(entityType, entityId, status, effectiveLimit);
    }
}
