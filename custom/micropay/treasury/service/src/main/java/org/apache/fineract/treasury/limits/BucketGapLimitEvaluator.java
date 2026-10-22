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
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.domain.TreasuryRunLine;
import org.apache.fineract.treasury.domain.TreasuryRunLineRepository;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A bucket's net or cumulative gap in the scope's LADDER run, by bucket code: as an amount, or as a percentage of the
 * ladder's total outflows or of its deposit outflows (savings and term deposits).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class BucketGapLimitEvaluator implements LimitEvaluator {

    private final TreasuryRunLineRepository lineRepository;

    @Override
    public LimitMetric metric() {
        return LimitMetric.BUCKET_GAP;
    }

    @Override
    public List<Observation> observe(final TreasuryLimit limit, final EvaluationContext context) {
        if (context.ladderRun() == null) {
            return List.of(LimitSubjects.unavailable(limit, context, "RUN", null, "No LADDER run of the scope on " + context.asOfDate()));
        }
        final Long runId = context.ladderRun().getId();
        return List.of(observe(limit, context, runId, this.lineRepository.findByRunIdOrderByBucketSeqAscIdAsc(runId)));
    }

    static Observation observe(final TreasuryLimit limit, final EvaluationContext context, final Long runId,
            final List<TreasuryRunLine> lines) {
        final Optional<TreasuryRunLine> total = lines.stream()
                .filter(l -> l.getSource() == LadderSourceCode.TOTAL && l.getBucketCode().equals(limit.getMetricKey())).findFirst();
        if (total.isEmpty()) {
            return LimitSubjects.unavailable(limit, context, "RUN", runId, "Bucket " + limit.getMetricKey() + " is not in run " + runId);
        }
        final GapType type = limit.getGapType() == null ? GapType.CUMULATIVE : limit.getGapType();
        final BigDecimal gap = type == GapType.NET ? total.get().getNet() : total.get().getCumulativeGap();
        final String detail = "Bucket " + limit.getMetricKey() + " " + type.name().toLowerCase() + " gap " + LimitSubjects.amount(gap)
                + " (run " + runId + ")";
        if (limit.getUnit() != LimitUnit.PERCENT) {
            return LimitSubjects.observe(limit, context, gap, "RUN", runId, detail);
        }
        final BigDecimal basis = basis(limit.getGapBasis(), lines);
        final BigDecimal percent = LimitLevels.percent(gap, basis);
        if (percent == null) {
            return LimitSubjects.unavailable(limit, context, "RUN", runId, detail + ": the " + limit.getGapBasis() + " basis is zero");
        }
        return LimitSubjects.observe(limit, context, percent, "RUN", runId,
                detail + " of " + limit.getGapBasis() + " " + LimitSubjects.amount(basis));
    }

    static BigDecimal basis(final GapBasis basis, final List<TreasuryRunLine> lines) {
        return lines.stream()
                .filter(l -> basis == GapBasis.DEPOSIT_OUTFLOWS
                        ? l.getSource() == LadderSourceCode.SAVINGS || l.getSource() == LadderSourceCode.TERM_DEPOSITS
                        : l.getSource() == LadderSourceCode.TOTAL)
                .map(TreasuryRunLine::getOutflow).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
