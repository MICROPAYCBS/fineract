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
package org.apache.fineract.treasury.data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** A trigger that fired, with its stage and actions. */
public record TreasuryCfpActivationData(Long id, Long triggerId, String triggerCode, String stage, String sourceType, Long sourceId,
        LocalDate asOfDate, String currencyCode, Long officeId, String scenarioCode, String detail, int occurrences, String status,
        Long openedBy, OffsetDateTime openedOnUtc, Long closedBy, OffsetDateTime closedOnUtc, String closeComment, List<Action> actions) {

    public record Action(Long id, int seq, String description, Long ownerRoleId, Long ownerUserId, Long fundingLineId, String status,
            Long completedBy, OffsetDateTime completedOnUtc, String comment) {
    }
}
