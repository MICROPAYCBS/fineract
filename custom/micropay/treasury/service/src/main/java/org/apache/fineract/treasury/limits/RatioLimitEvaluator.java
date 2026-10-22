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

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.domain.TreasuryRunRatio;
import org.apache.fineract.treasury.domain.TreasuryRunRatioRepository;
import org.apache.fineract.treasury.liquidity.RatioDirection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A ratio of the scope's RATIOS run, by code, as it was stored. With {@code useOwnLevels} the ratio's own direction,
 * limit and warning are the levels. A ratio without a denominator is a HARD breach, as it is a BREACH in the run.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class RatioLimitEvaluator implements LimitEvaluator {

    private final TreasuryRunRatioRepository ratioRepository;

    @Override
    public LimitMetric metric() {
        return LimitMetric.RATIO;
    }

    @Override
    public List<Observation> observe(final TreasuryLimit limit, final EvaluationContext context) {
        if (context.ratiosRun() == null) {
            return List.of(LimitSubjects.unavailable(limit, context, "RUN", null, "No RATIOS run of the scope on " + context.asOfDate()));
        }
        final Long runId = context.ratiosRun().getId();
        final Optional<TreasuryRunRatio> ratio = this.ratioRepository.findByRunIdOrderBySequenceNumberAsc(runId).stream()
                .filter(r -> r.getCode().equals(limit.getMetricKey())).findFirst();
        if (ratio.isEmpty()) {
            return List.of(
                    LimitSubjects.unavailable(limit, context, "RUN", runId, "Ratio " + limit.getMetricKey() + " is not in run " + runId));
        }
        final TreasuryRunRatio r = ratio.get();
        final String detail = r.getCode() + " = " + LimitSubjects.amount(r.getNumerator()) + " / "
                + LimitSubjects.amount(r.getDenominator()) + " (run " + runId + ")";
        final Observation observation = limit.isUseOwnLevels()
                ? Observation.of(limit.getScopeType(), limit.getScopeId(), LimitSubjects.label(limit, context), r.getResult(),
                        r.getDirection() == RatioDirection.MIN ? LimitComparison.MIN : LimitComparison.MAX, r.getWarningValue(),
                        r.getLimitValue(), "RUN", runId, detail)
                : LimitSubjects.observe(limit, context, r.getResult(), "RUN", runId, detail);
        return List.of(r.getResult() == null ? observation.withLevel(LimitLevel.HARD) : observation);
    }
}
