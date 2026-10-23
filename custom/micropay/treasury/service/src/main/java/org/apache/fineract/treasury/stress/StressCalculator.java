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
package org.apache.fineract.treasury.stress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.apache.fineract.treasury.liquidity.Bucket;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;

/**
 * Applies a scenario's shocks to a ladder run's lines and works out, per bucket, the baseline and stressed net and
 * cumulative gaps, the counterbalancing capacity and the survival horizon.
 *
 * <p>
 * Capacity is the liquid holdings that can be sold (the rule set's instrument asset classes after their haircuts and
 * the scenario's) plus the funding lines that count as capacity, each from the first bucket whose last day is at or
 * after its days to access. Holdings counted as capacity are monetised: their deal types' inflows leave the stressed
 * ladder, so the cash is not counted twice.
 * </p>
 *
 * <p>
 * The survival horizon is the first day the stressed cumulative gap plus the capacity available in the bucket turns
 * negative. Within a bucket the gap is taken to move linearly by day, from the cumulative gap at its start to the one
 * at its end; a bucket without an end cannot be interpolated and counts from its first day. None when it never turns
 * negative: beyond the ladder's horizon.
 * </p>
 */
public final class StressCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private StressCalculator() {}

    /**
     * @param monetised
     *            ladder lines whose inflows are replaced by capacity: their stressed inflow is zero
     */
    public static List<StressLine> shock(final List<Bucket> buckets, final List<LadderLine> lines, final List<Shock> shocks,
            final Predicate<LadderLine> monetised, final int scale) {
        final List<StressLine> result = new ArrayList<>();
        final Map<LadderLine, StressLine> byLine = new LinkedHashMap<>();
        for (final LadderLine line : lines) {
            final StressLine stress = new StressLine(line.bucketSeq(), line.bucketCode(), line.source(), line.lineCode(), line.productId(),
                    line.productName(), line.total());
            if (monetised.test(line) && line.total().signum() > 0) {
                stress.change(BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP), "Monetised as counterbalancing capacity");
            }
            result.add(stress);
            byLine.put(line, stress);
        }
        for (final Shock shock : shocks) {
            switch (shock.type()) {
                case MULTIPLY -> byLine.forEach((line, stress) -> {
                    if (shock.matches(line, line.total(), false)) {
                        stress.change(percent(stress.getStressed(), shock.factor(), scale), shock.label());
                    }
                });
                case REMOVE_ROLLOVER -> byLine.forEach((line, stress) -> {
                    if (shock.matches(line, line.contractual(), false) && line.behavioural().signum() != 0) {
                        stress.change(stress.getStressed().subtract(line.behavioural()), shock.label());
                    }
                });
                case ADD_OUTFLOW_PCT_OF_BALANCE -> addOutflow(buckets, lines, byLine, result, shock, scale);
                default -> {
                }
            }
        }
        result.sort((a, b) -> a.getBucketSeq() != b.getBucketSeq() ? Integer.compare(a.getBucketSeq(), b.getBucketSeq())
                : a.getSource() != b.getSource() ? a.getSource().compareTo(b.getSource())
                        : !Objects.equals(a.getLineCode(), b.getLineCode()) ? a.getLineCode().compareTo(b.getLineCode())
                                : Long.compare(a.getProductId() == null ? 0 : a.getProductId(),
                                        b.getProductId() == null ? 0 : b.getProductId()));
        return result;
    }

    /** Per product, the share of the matching lines' contractual total over every bucket, out in the target bucket. */
    private static void addOutflow(final List<Bucket> buckets, final List<LadderLine> lines, final Map<LadderLine, StressLine> byLine,
            final List<StressLine> result, final Shock shock, final int scale) {
        final Bucket target = shock.bucketCode() == null ? buckets.getFirst()
                : buckets.stream().filter(b -> b.code().equals(shock.bucketCode())).findFirst().orElse(null);
        if (target == null) {
            return;
        }
        final Map<List<Object>, BigDecimal> balances = new LinkedHashMap<>();
        final Map<List<Object>, LadderLine> samples = new LinkedHashMap<>();
        for (final LadderLine line : lines) {
            if (shock.matches(line, line.contractual().signum() == 0 ? line.total() : line.contractual(), true)) {
                final List<Object> key = Arrays.asList(line.source(), line.lineCode(), line.productId());
                balances.merge(key, line.contractual(), BigDecimal::add);
                samples.putIfAbsent(key, line);
            }
        }
        balances.forEach((key, balance) -> {
            final BigDecimal extra = percent(balance.abs(), shock.factor(), scale);
            if (extra.signum() == 0) {
                return;
            }
            final LadderLine sample = samples.get(key);
            StressLine stress = byLine.entrySet().stream()
                    .filter(e -> e.getKey().bucketSeq() == target.seq() && e.getKey().source() == sample.source()
                            && Objects.equals(e.getKey().lineCode(), sample.lineCode())
                            && Objects.equals(e.getKey().productId(), sample.productId()))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
            if (stress == null) {
                stress = new StressLine(target.seq(), target.code(), sample.source(), sample.lineCode(), sample.productId(),
                        sample.productName(), BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP));
                result.add(stress);
                byLine.put(new LadderLine(target.seq(), target.code(), sample.source(), sample.lineCode(), sample.productId(),
                        sample.productName(), BigDecimal.ZERO, BigDecimal.ZERO), stress);
            }
            stress.change(stress.getStressed().subtract(extra), shock.label());
        });
    }

    /** A funding line's amount and how many days it takes to draw. */
    public record FundingAccess(BigDecimal amount, int daysToAccess) {
    }

    /** What a bucket holds before and after the shocks, with the capacity available by its last day. */
    public record BucketResult(Bucket bucket, BigDecimal baselineNet, BigDecimal baselineCumulative, BigDecimal stressedNet,
            BigDecimal stressedCumulative, BigDecimal liquidCapacity, BigDecimal fundingCapacity, BigDecimal baselineCapacity) {

        public BigDecimal capacity() {
            return this.liquidCapacity.add(this.fundingCapacity);
        }

        /** The stressed cumulative gap plus the capacity. */
        public BigDecimal position() {
            return this.stressedCumulative.add(capacity());
        }
    }

    /**
     * @param baselineLiquid
     *            the liquid capacity before the scenario's haircuts
     * @param baselineFunding
     *            the funding lines before the scenario's freezes
     */
    public static List<BucketResult> buckets(final List<Bucket> buckets, final List<StressLine> lines, final BigDecimal liquid,
            final List<FundingAccess> funding, final BigDecimal baselineLiquid, final List<FundingAccess> baselineFunding) {
        final List<BucketResult> results = new ArrayList<>();
        BigDecimal baselineCumulative = BigDecimal.ZERO;
        BigDecimal stressedCumulative = BigDecimal.ZERO;
        for (final Bucket bucket : buckets) {
            BigDecimal baseline = BigDecimal.ZERO;
            BigDecimal stressed = BigDecimal.ZERO;
            for (final StressLine line : lines) {
                if (line.getBucketSeq() == bucket.seq()) {
                    baseline = baseline.add(line.getBaseline());
                    stressed = stressed.add(line.getStressed());
                }
            }
            baselineCumulative = baselineCumulative.add(baseline);
            stressedCumulative = stressedCumulative.add(stressed);
            results.add(new BucketResult(bucket, baseline, baselineCumulative, stressed, stressedCumulative, liquid,
                    accessible(funding, bucket), baselineLiquid.add(accessible(baselineFunding, bucket))));
        }
        return results;
    }

    /** The lines that can be drawn by the bucket's last day; every line in an open-ended bucket. */
    public static BigDecimal accessible(final List<FundingAccess> funding, final Bucket bucket) {
        return funding.stream().filter(f -> bucket.toDay() == null || f.daysToAccess() <= bucket.toDay()).map(FundingAccess::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * @param days
     *            from the as-of date to the day the position turns negative, 2 decimals; null beyond the horizon
     * @param horizonDays
     *            the first day of the last bucket
     */
    public record Survival(BigDecimal days, boolean beyondHorizon, String bucketCode, int horizonDays) {
    }

    public static Survival survival(final List<BucketResult> results, final boolean stressed) {
        BigDecimal previous = BigDecimal.ZERO;
        for (final BucketResult r : results) {
            final BigDecimal capacity = stressed ? r.capacity() : r.baselineCapacity();
            final BigDecimal cumulative = stressed ? r.stressedCumulative() : r.baselineCumulative();
            final BigDecimal start = previous.add(capacity);
            final BigDecimal end = cumulative.add(capacity);
            if (end.signum() < 0) {
                final Bucket bucket = r.bucket();
                BigDecimal days = BigDecimal.valueOf(bucket.fromDay());
                if (start.signum() > 0 && bucket.toDay() != null) {
                    final BigDecimal share = start.divide(start.subtract(end), 10, RoundingMode.HALF_UP);
                    days = days.add(share.multiply(BigDecimal.valueOf(bucket.days())));
                }
                return new Survival(days.setScale(2, RoundingMode.DOWN), false, bucket.code(), results.getLast().bucket().fromDay());
            }
            previous = cumulative;
        }
        return new Survival(null, true, results.isEmpty() ? null : results.getLast().bucket().code(),
                results.isEmpty() ? 0 : results.getLast().bucket().fromDay());
    }

    /** The asset class's value after its own and the scenario's haircuts. */
    public static BigDecimal eligible(final BigDecimal source, final BigDecimal excluded, final BigDecimal haircut,
            final BigDecimal shockHaircut, final int scale) {
        final BigDecimal kept = source.subtract(excluded);
        return kept.multiply(HUNDRED.subtract(haircut)).multiply(HUNDRED.subtract(shockHaircut)).divide(HUNDRED.multiply(HUNDRED), scale,
                RoundingMode.HALF_UP);
    }

    static BigDecimal percent(final BigDecimal value, final BigDecimal percent, final int scale) {
        return value.multiply(percent).divide(HUNDRED, scale, RoundingMode.HALF_UP);
    }

    /** Whether a shock source can be shocked on the ladder: every source but the totals. */
    public static boolean shockable(final LadderSourceCode source) {
        return source != null && source != LadderSourceCode.TOTAL;
    }
}
