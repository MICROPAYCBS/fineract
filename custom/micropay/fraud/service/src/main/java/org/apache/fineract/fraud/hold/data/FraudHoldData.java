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
package org.apache.fineract.fraud.hold.data;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FraudHoldData {

    private final Long id;
    private final FraudHoldEntityType entityType;
    private final Long entityId;
    private final FraudHoldStatus status;
    private final FraudHoldSource source;
    private final String reason;
    private final String externalReference;
    private final Long createdBy;
    private final LocalDateTime createdOnUtc;
    private final Long releasedBy;
    private final LocalDateTime releasedOnUtc;
    private final String releaseNote;
    private final List<FraudHoldEffectData> effects;

    public boolean isActive() {
        return status == FraudHoldStatus.ACTIVE;
    }

    public FraudHoldData withEffects(List<FraudHoldEffectData> newEffects) {
        return new FraudHoldData(id, entityType, entityId, status, source, reason, externalReference, createdBy, createdOnUtc, releasedBy,
                releasedOnUtc, releaseNote, newEffects);
    }
}
