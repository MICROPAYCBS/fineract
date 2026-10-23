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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRepricingBucket;
import org.apache.fineract.treasury.domain.TreasuryRunRepricingBucketRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRepricingShock;
import org.apache.fineract.treasury.domain.TreasuryRunRepricingShockRepository;
import org.apache.fineract.treasury.liquidity.RunType;
import org.apache.fineract.treasury.repricing.RepricingBuilder;

/** The scope's latest REPRICING run of the date: its gap as a percentage of total assets, or its earnings at risk. */
@RequiredArgsConstructor
abstract class RepricingLimitEvaluator implements LimitEvaluator {

    private final TreasuryRunRepository runRepository;
    private final TreasuryRunRepricingBucketRepository bucketRepository;
    private final TreasuryRunRepricingShockRepository shockRepository;

    @Override
    public List<Observation> observe(final TreasuryLimit limit, final EvaluationContext context) {
        final Optional<TreasuryRun> run = this.runRepository
                .findByRunTypeAndAsOfDateAndCurrencyCode(RunType.REPRICING, context.asOfDate(), context.currencyCode()).stream()
                .filter(r -> Objects.equals(r.getOfficeId(), context.officeId())).max(Comparator.comparing(TreasuryRun::getId));
        if (run.isEmpty()) {
            return List
                    .of(LimitSubjects.unavailable(limit, context, "RUN", null, "No REPRICING run of the scope on " + context.asOfDate()));
        }
        final Long runId = run.get().getId();
        return List.of(metric() == LimitMetric.EAR_PCT ? ear(limit, context, runId) : gap(limit, context, runId));
    }

    private Observation gap(final TreasuryLimit limit, final EvaluationContext context, final Long runId) {
        final List<TreasuryRunRepricingBucket> buckets = this.bucketRepository.findByRunIdOrderByBucketSeqAsc(runId);
        final Optional<TreasuryRunRepricingBucket> bucket = limit.getMetricKey() == null
                ? buckets.stream().filter(b -> b.getToDay() != null && b.getToDay() <= RepricingBuilder.YEAR_DAYS).reduce((a, b) -> b)
                : buckets.stream().filter(b -> b.getBucketCode().equals(limit.getMetricKey())).findFirst();
        if (bucket.isEmpty()) {
            return LimitSubjects.unavailable(limit, context, "RUN", runId,
                    (limit.getMetricKey() == null ? "No bucket within a year" : "Bucket " + limit.getMetricKey() + " is not") + " in run "
                            + runId);
        }
        final TreasuryRunRepricingBucket b = bucket.get();
        final BigDecimal value = limit.getMetricKey() == null ? b.getCumulativePercent() : b.getGapPercent();
        final String detail = (limit.getMetricKey() == null
                ? "Cumulative gap to " + b.getBucketCode() + " " + LimitSubjects.amount(b.getCumulativeGap())
                : "Bucket " + b.getBucketCode() + " gap " + LimitSubjects.amount(b.getGap())) + " (run " + runId + ")";
        return value == null ? LimitSubjects.unavailable(limit, context, "RUN", runId, detail + ": no total assets")
                : LimitSubjects.observe(limit, context, value, "RUN", runId, detail);
    }

    private Observation ear(final TreasuryLimit limit, final EvaluationContext context, final Long runId) {
        final List<TreasuryRunRepricingShock> shocks = this.shockRepository.findByRunIdOrderByShockBpDesc(runId);
        final Optional<TreasuryRunRepricingShock> shock = limit.getMetricKey() == null
                ? shocks.stream().min(Comparator.comparing(TreasuryRunRepricingShock::getDeltaNii))
                : shocks.stream().filter(s -> String.valueOf(s.getShockBp()).equals(limit.getMetricKey().replace("+", ""))).findFirst();
        if (shock.isEmpty()) {
            return LimitSubjects.unavailable(limit, context, "RUN", runId, "No shock " + limit.getMetricKey() + " in run " + runId);
        }
        final TreasuryRunRepricingShock s = shock.get();
        final String detail = "Shock " + s.getShockBp() + "bp: net interest income " + LimitSubjects.amount(s.getDeltaNii()) + " (run "
                + runId + ")";
        if (s.getEarPercent() == null) {
            return LimitSubjects.unavailable(limit, context, "RUN", runId, detail + ": no core capital, or a gain");
        }
        return LimitSubjects.observe(limit, context, s.getEarPercent(), "RUN", runId, detail);
    }
}
