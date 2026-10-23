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

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.data.TreasuryFxPositionData;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.service.TreasuryFxConfig;
import org.apache.fineract.treasury.service.TreasuryFxPositionService;

/**
 * The net open position as a percentage of core capital, for the institution: FX_SINGLE_NOP in each foreign currency's
 * evaluation, FX_AGGREGATE_NOP (shorthand) in the base currency's.
 */
@RequiredArgsConstructor
abstract class FxNopLimitEvaluator implements LimitEvaluator {

    private final TreasuryFxPositionService positionService;
    private final TreasuryFxConfig fxConfig;

    @Override
    public List<Observation> observe(final TreasuryLimit limit, final EvaluationContext context) {
        final boolean base = context.currencyCode().equals(this.fxConfig.baseCurrency());
        if (!context.isInstitution() || base != (metric() == LimitMetric.FX_AGGREGATE_NOP)) {
            return List.of();
        }
        final TreasuryFxPositionData positions = this.positionService.positions(context.asOfDate(), null);
        if (positions.coreCapital() == null) {
            return List.of(
                    LimitSubjects.unavailable(limit, context, "FX_POSITION", null, "No core capital in effect on " + context.asOfDate()));
        }
        if (base) {
            return List.of(LimitSubjects.observe(limit, context, positions.aggregateNopPercent(), "FX_POSITION", null,
                    "Longs " + LimitSubjects.amount(positions.sumOfLongs()) + ", shorts " + LimitSubjects.amount(positions.sumOfShorts())
                            + " " + positions.baseCurrency() + " against core capital " + LimitSubjects.amount(positions.coreCapital())));
        }
        final TreasuryFxPositionData.Currency currency = positions.currencies().stream()
                .filter(c -> c.currencyCode().equals(context.currencyCode())).findFirst().orElse(null);
        if (currency == null || currency.nopPercent() == null) {
            return List.of(LimitSubjects.unavailable(limit, context, "FX_POSITION", null, "No MID rate of " + context.currencyCode()));
        }
        return List.of(LimitSubjects.observe(limit, context, currency.nopPercent(), "FX_POSITION", null,
                "Net " + LimitSubjects.amount(currency.netPosition()) + " " + currency.currencyCode() + " = "
                        + LimitSubjects.amount(currency.netBase()) + " " + positions.baseCurrency() + " at "
                        + LimitSubjects.amount(currency.midRate()) + (currency.rateStale() ? " (stale rate)" : "") + ", core capital "
                        + LimitSubjects.amount(positions.coreCapital())));
    }
}
