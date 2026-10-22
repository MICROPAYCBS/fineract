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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** An evaluation of the limits and what it observed per limit and subject. */
public record TreasuryLimitEvaluationData(Long id, LocalDate asOfDate, String currencyCode, Long officeId, String triggerType,
        Long sourceRunId, Long sourceDealId, int limitsEvaluated, int breachesCreated, int breachesEscalated, int breachesRepeated,
        int breachesCleared, Long createdBy, OffsetDateTime createdOnUtc, List<Line> lines) {

    public record Line(int seq, Long limitId, String limitCode, int limitVersion, String metric, String scopeType, Long scopeId,
            String scopeLabel, BigDecimal observedValue, String comparison, BigDecimal warningLevel, BigDecimal hardLevel, String unit,
            String level, String action, Long breachId, String sourceType, Long sourceId, String detail) {
    }
}
