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
package org.apache.fineract.scoring.data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A scoring decision as returned by the API. Rule results, the points breakdown and the input snapshot are returned as
 * parsed JSON, with the overrides made on this decision.
 */
public record ScoringDecisionData(Long id, Long clientId, Long loanId, Long loanProductId, String trigger, String mode, Long scorecardId,
        String scorecardCode, Integer scorecardVersion, BigDecimal score, String grade, BigDecimal expectedPd, String outcome,
        List<String> reasonCodes, Object ruleResults, Object pointsBreakdown, Object inputs, List<ScoringOverrideData> overrides,
        Long createdBy, OffsetDateTime createdOn) {
}
