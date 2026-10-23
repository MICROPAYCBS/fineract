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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
import org.apache.fineract.treasury.domain.TreasuryRunStressBucket;
import org.apache.fineract.treasury.domain.TreasuryRunStressBucketRepository;
import org.apache.fineract.treasury.liquidity.RunType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The survival horizon of the scope's latest STRESS run per scenario on the date: of the scenario named by
 * {@code metricKey}, or the shortest. A run that never turns negative counts the first day of its last bucket.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class SurvivalLimitEvaluator implements LimitEvaluator {

    private final TreasuryRunRepository runRepository;
    private final TreasuryRunStressBucketRepository bucketRepository;

    @Override
    public LimitMetric metric() {
        return LimitMetric.SURVIVAL_DAYS;
    }

    @Override
    public List<Observation> observe(final TreasuryLimit limit, final EvaluationContext context) {
        final Map<String, TreasuryRun> latest = this.runRepository
                .findByRunTypeAndAsOfDateAndCurrencyCode(RunType.STRESS, context.asOfDate(), context.currencyCode()).stream()
                .filter(r -> Objects.equals(r.getOfficeId(), context.officeId()))
                .filter(r -> limit.getMetricKey() == null || limit.getMetricKey().equals(r.getScenarioCode()))
                .collect(Collectors.toMap(TreasuryRun::getScenarioCode, Function.identity(), (a, b) -> a.getId() > b.getId() ? a : b));
        if (latest.isEmpty()) {
            return List.of(LimitSubjects.unavailable(limit, context, "RUN", null, "No STRESS run of the scope on " + context.asOfDate()
                    + (limit.getMetricKey() == null ? "" : " for scenario " + limit.getMetricKey())));
        }
        final TreasuryRun worst = latest.values().stream().min(Comparator.comparing(this::days).thenComparing(TreasuryRun::getScenarioCode))
                .orElseThrow();
        final String detail = "Scenario " + worst.getScenarioCode() + " v" + worst.getScenarioVersion() + ": "
                + (worst.isSurvivalBeyondHorizon() ? "beyond the horizon (from day " + LimitSubjects.amount(days(worst)) + ")"
                        : LimitSubjects.amount(worst.getSurvivalDays()) + " days")
                + " (run " + worst.getId() + (latest.size() > 1 ? ", shortest of " + latest.size() + " scenarios" : "") + ")";
        return List.of(LimitSubjects.observe(limit, context, days(worst), "RUN", worst.getId(), detail));
    }

    private BigDecimal days(final TreasuryRun run) {
        if (!run.isSurvivalBeyondHorizon()) {
            return run.getSurvivalDays();
        }
        return this.bucketRepository.findByRunIdOrderByBucketSeqAscIdAsc(run.getId()).stream().map(TreasuryRunStressBucket::getFromDay)
                .max(Integer::compare).map(BigDecimal::valueOf).orElse(BigDecimal.ZERO);
    }
}
