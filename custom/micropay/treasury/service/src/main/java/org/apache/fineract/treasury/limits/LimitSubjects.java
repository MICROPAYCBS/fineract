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
import org.apache.fineract.treasury.domain.TreasuryLimit;

/** The subject and levels of limits observed on their own scope (ratios, gaps, concentrations). */
final class LimitSubjects {

    private LimitSubjects() {}

    static String label(final TreasuryLimit limit, final EvaluationContext context) {
        return switch (limit.getScopeType()) {
            case OFFICE -> "Office " + limit.getScopeId() + ", " + context.currencyCode();
            case COUNTERPARTY -> "Counterparty " + limit.getScopeId();
            case ACCOUNT -> "Account " + limit.getScopeId();
            default -> "Institution, " + context.currencyCode();
        };
    }

    /** The limit's own scope, with its configured levels. */
    static Observation observe(final TreasuryLimit limit, final EvaluationContext context, final BigDecimal value, final String sourceType,
            final Long sourceId, final String detail) {
        return Observation.of(limit.getScopeType(), limit.getScopeId(), label(limit, context), value, limit.getComparison(),
                limit.getWarningLevel(), limit.getHardLevel(), sourceType, sourceId, detail);
    }

    static Observation unavailable(final TreasuryLimit limit, final EvaluationContext context, final String sourceType, final Long sourceId,
            final String detail) {
        return Observation.unavailable(limit.getScopeType(), limit.getScopeId(), label(limit, context), limit.getComparison(),
                limit.getWarningLevel(), limit.getHardLevel(), sourceType, sourceId, detail);
    }

    static String amount(final BigDecimal value) {
        return value == null ? "none" : value.stripTrailingZeros().toPlainString();
    }
}
