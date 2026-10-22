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

/** A breach of a limit, with the levels it was compared with, its occurrences, handling and comment thread. */
public record TreasuryBreachData(Long id, Long limitId, String limitCode, int limitVersion, String metric, String metricKey,
        String scopeType, Long scopeId, String scopeLabel, String currencyCode, LocalDate asOfDate, BigDecimal observedValue, String level,
        String lastLevel, String comparison, BigDecimal warningLevel, BigDecimal hardLevel, String unit, String sourceType, Long sourceId,
        Long evaluationId, String detail, String status, LocalDate firstSeenOn, LocalDate lastSeenOn, int occurrences,
        LocalDate escalatedOn, LocalDate clearedOn, Long acknowledgedBy, OffsetDateTime acknowledgedOnUtc, Long resolvedBy,
        LocalDate resolvedOnDate, OffsetDateTime resolvedOnUtc, String resolutionComment, OffsetDateTime createdOnUtc,
        List<Comment> comments) {

    public record Comment(Long id, String kind, String comment, Long createdBy, OffsetDateTime createdOnUtc) {
    }
}
