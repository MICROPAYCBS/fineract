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
package org.apache.fineract.treasury.limits;

import java.math.BigDecimal;

/**
 * A value an evaluator observed for a limit and one subject (the institution, an office, a counterparty or an account),
 * with the levels it was compared with and the level it crossed, if any.
 *
 * @param sourceType
 *            where the value comes from: RUN, DEAL, DEAL_REQUEST, ACCOUNT, DEPOSITS or DEALS
 * @param level
 *            null when no level is crossed, or nothing could be observed ({@code value} null)
 */
public record Observation(LimitScopeType subjectType, Long subjectId, String subjectLabel, BigDecimal value, LimitComparison comparison,
        BigDecimal warning, BigDecimal hard, LimitLevel level, String sourceType, Long sourceId, String detail) {

    public static Observation of(final LimitScopeType subjectType, final Long subjectId, final String subjectLabel, final BigDecimal value,
            final LimitComparison comparison, final BigDecimal warning, final BigDecimal hard, final String sourceType, final Long sourceId,
            final String detail) {
        return new Observation(subjectType, subjectId, subjectLabel, value, comparison, warning, hard,
                LimitLevels.level(comparison, warning, hard, value), sourceType, sourceId, detail);
    }

    /** Nothing to compare: no run, no denominator. */
    public static Observation unavailable(final LimitScopeType subjectType, final Long subjectId, final String subjectLabel,
            final LimitComparison comparison, final BigDecimal warning, final BigDecimal hard, final String sourceType, final Long sourceId,
            final String detail) {
        return new Observation(subjectType, subjectId, subjectLabel, null, comparison, warning, hard, null, sourceType, sourceId, detail);
    }

    public Observation withLevel(final LimitLevel newLevel) {
        return new Observation(this.subjectType, this.subjectId, this.subjectLabel, this.value, this.comparison, this.warning, this.hard,
                newLevel, this.sourceType, this.sourceId, this.detail);
    }
}
