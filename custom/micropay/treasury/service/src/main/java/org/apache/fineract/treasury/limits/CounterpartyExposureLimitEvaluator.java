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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.data.TreasuryExposureData;
import org.apache.fineract.treasury.deal.LimitCheck;
import org.apache.fineract.treasury.domain.TreasuryCounterparty;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyRepository;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyStatus;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.service.TreasuryCounterpartyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Each active counterparty's exposure, from the same calculation as the deal capture check ({@link LimitCheck} over the
 * asset deals approved, confirmed or settled). With {@code useOwnLevels} the counterparty's own exposure limit and
 * warning percentage apply, and the level is the capture check's outcome (WARNING, or HARD when EXCEEDED); otherwise
 * the utilisation of the counterparty's limit (PERCENT) or the exposure itself (AMOUNT, in the limit's currency) is
 * compared with the limit's levels. Exposure is today's: deal statuses are not kept per date.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class CounterpartyExposureLimitEvaluator implements LimitEvaluator {

    static final BigDecimal FULL = BigDecimal.valueOf(100);

    private final TreasuryCounterpartyRepository counterpartyRepository;
    private final TreasuryCounterpartyService counterpartyService;

    @Override
    public LimitMetric metric() {
        return LimitMetric.COUNTERPARTY_EXPOSURE;
    }

    @Override
    public List<Observation> observe(final TreasuryLimit limit, final EvaluationContext context) {
        final List<Observation> observations = new ArrayList<>();
        for (final TreasuryCounterparty counterparty : this.counterpartyRepository.findAllByOrderByNameAsc()) {
            if (counterparty.getStatus() != TreasuryCounterpartyStatus.ACTIVE
                    || limit.getScopeType() == LimitScopeType.COUNTERPARTY && !counterparty.getId().equals(limit.getScopeId())) {
                continue;
            }
            final TreasuryExposureData exposure = this.counterpartyService.exposure(counterparty.getId());
            final BigDecimal amount = exposure.deals().stream().filter(d -> context.currencyCode().equals(d.currencyCode()))
                    .map(TreasuryExposureData.DealExposure::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            final LimitCheck.Result result = LimitCheck.evaluate(counterparty.getExposureLimit(), counterparty.getWarningPercent(),
                    exposure.exposure(), BigDecimal.ZERO);
            final Observation observation = observe(limit, counterparty, context.currencyCode(), result, amount, "COUNTERPARTY",
                    counterparty.getId(),
                    "Exposure " + LimitSubjects.amount(exposure.exposure()) + " over " + exposure.deals().size() + " deals"
                            + (counterparty.getExposureLimit() == null ? ""
                                    : ", limit " + LimitSubjects.amount(counterparty.getExposureLimit()) + " "
                                            + counterparty.getLimitCurrencyCode()));
            if (observation != null) {
                observations.add(observation);
            }
        }
        return observations;
    }

    /**
     * One counterparty against the limit, from a capture check (the exposure with the deal) or today's exposure. None
     * when the limit cannot apply: no exposure limit of the counterparty in the currency for own levels or a
     * percentage, nothing in the currency for an amount.
     *
     * @param exposureInCurrency
     *            the exposure in the evaluated currency, for an AMOUNT limit
     */
    public static Observation observe(final TreasuryLimit limit, final TreasuryCounterparty counterparty, final String currency,
            final LimitCheck.Result result, final BigDecimal exposureInCurrency, final String sourceType, final Long sourceId,
            final String detail) {
        final String label = counterparty.getShortCode() + " (" + counterparty.getName() + ")";
        final boolean ownLimit = counterparty.getExposureLimit() != null && Objects.equals(counterparty.getLimitCurrencyCode(), currency);
        if (limit.isUseOwnLevels()) {
            if (!ownLimit) {
                return null;
            }
            final LimitLevel level = switch (result.outcome()) {
                case WARNING -> LimitLevel.WARNING;
                case EXCEEDED -> LimitLevel.HARD;
                default -> null;
            };
            return new Observation(LimitScopeType.COUNTERPARTY, counterparty.getId(), label, result.utilisation(), LimitComparison.MAX,
                    counterparty.getWarningPercent(), FULL, level, sourceType, sourceId, detail);
        }
        if (limit.getUnit() == LimitUnit.PERCENT) {
            return ownLimit ? Observation.of(LimitScopeType.COUNTERPARTY, counterparty.getId(), label, result.utilisation(),
                    limit.getComparison(), limit.getWarningLevel(), limit.getHardLevel(), sourceType, sourceId, detail) : null;
        }
        if (exposureInCurrency == null || exposureInCurrency.signum() == 0 && limit.getComparison() == LimitComparison.MAX) {
            return null;
        }
        return Observation.of(LimitScopeType.COUNTERPARTY, counterparty.getId(), label, exposureInCurrency, limit.getComparison(),
                limit.getWarningLevel(), limit.getHardLevel(), sourceType, sourceId, detail);
    }
}
