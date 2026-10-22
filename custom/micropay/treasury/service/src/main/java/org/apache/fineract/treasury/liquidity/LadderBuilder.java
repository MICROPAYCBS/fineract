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
package org.apache.fineract.treasury.liquidity;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Puts source flows into buckets and works out each bucket's inflows, outflows, net and cumulative gap. A flow falls in
 * the bucket holding its days after the as-of date; undated and overdue flows fall in the first bucket. When the last
 * bucket is bounded, flows beyond it are left out and totalled as beyond the horizon.
 */
public final class LadderBuilder {

    private LadderBuilder() {}

    /** One source line in one bucket; amounts signed, inflows positive. */
    public record Line(Bucket bucket, LadderSourceCode source, String lineCode, Long productId, String productName, BigDecimal contractual,
            BigDecimal behavioural) {

        public BigDecimal total() {
            return this.contractual.add(this.behavioural);
        }

        public BigDecimal inflow() {
            return total().max(BigDecimal.ZERO);
        }

        public BigDecimal outflow() {
            return total().min(BigDecimal.ZERO).negate();
        }
    }

    public record BucketTotal(Bucket bucket, BigDecimal inflow, BigDecimal outflow, BigDecimal contractual, BigDecimal behavioural,
            BigDecimal net, BigDecimal cumulativeGap) {
    }

    public record Ladder(List<Line> lines, List<BucketTotal> totals, BigDecimal beyondHorizon) {
    }

    private record Key(int seq, LadderSourceCode source, String lineCode, Long productId) {
    }

    public static Ladder build(final LadderContext context, final List<LadderFlow> flows) {
        final Map<Key, Line> lines = new LinkedHashMap<>();
        BigDecimal beyond = BigDecimal.ZERO;
        for (final LadderFlow flow : flows) {
            if (flow.amount() == null || flow.amount().signum() == 0) {
                continue;
            }
            final Bucket bucket = bucketOf(context, flow);
            if (bucket == null) {
                beyond = beyond.add(flow.amount());
                continue;
            }
            final Key key = new Key(bucket.seq(), flow.source(), flow.lineCode(), flow.productId());
            final Line current = lines.get(key);
            final BigDecimal contractual = (current == null ? BigDecimal.ZERO : current.contractual())
                    .add(flow.behavioural() ? BigDecimal.ZERO : flow.amount());
            final BigDecimal behavioural = (current == null ? BigDecimal.ZERO : current.behavioural())
                    .add(flow.behavioural() ? flow.amount() : BigDecimal.ZERO);
            lines.put(key, new Line(bucket, flow.source(), flow.lineCode(), flow.productId(),
                    current == null ? flow.productName() : current.productName(), contractual, behavioural));
        }
        final List<Line> sorted = new ArrayList<>(lines.values());
        sorted.sort((a, b) -> a.bucket().seq() != b.bucket().seq() ? Integer.compare(a.bucket().seq(), b.bucket().seq())
                : a.source() != b.source() ? a.source().compareTo(b.source())
                        : !Objects.equals(a.lineCode(), b.lineCode()) ? a.lineCode().compareTo(b.lineCode())
                                : Long.compare(a.productId() == null ? 0 : a.productId(), b.productId() == null ? 0 : b.productId()));
        final List<BucketTotal> totals = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        for (final Bucket bucket : context.buckets()) {
            BigDecimal inflow = BigDecimal.ZERO;
            BigDecimal outflow = BigDecimal.ZERO;
            BigDecimal contractual = BigDecimal.ZERO;
            BigDecimal behavioural = BigDecimal.ZERO;
            for (final Line line : sorted) {
                if (line.bucket().seq() == bucket.seq()) {
                    inflow = inflow.add(line.inflow());
                    outflow = outflow.add(line.outflow());
                    contractual = contractual.add(line.contractual());
                    behavioural = behavioural.add(line.behavioural());
                }
            }
            final BigDecimal net = inflow.subtract(outflow);
            cumulative = cumulative.add(net);
            totals.add(new BucketTotal(bucket, inflow, outflow, contractual, behavioural, net, cumulative));
        }
        return new Ladder(sorted, totals, beyond);
    }

    /** The bucket of the flow, or null beyond a bounded last bucket. */
    static Bucket bucketOf(final LadderContext context, final LadderFlow flow) {
        final long day = flow.date() == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(context.asOf(), flow.date()));
        for (final Bucket bucket : context.buckets()) {
            if (bucket.contains(day)) {
                return bucket;
            }
        }
        return null;
    }

    /**
     * Buckets must start at day 0, follow each other without gaps or overlaps, and only the last may be open-ended.
     *
     * @return the problem, or null when there is none
     */
    public static String checkBuckets(final List<Bucket> buckets) {
        if (buckets.isEmpty()) {
            return "at least one bucket is needed";
        }
        int expected = 0;
        final java.util.Set<String> codes = new java.util.HashSet<>();
        for (int i = 0; i < buckets.size(); i++) {
            final Bucket bucket = buckets.get(i);
            if (!codes.add(bucket.code())) {
                return "bucket code " + bucket.code() + " is used twice";
            }
            if (bucket.fromDay() != expected) {
                return "bucket " + bucket.code() + " must start on day " + expected + ", not " + bucket.fromDay();
            }
            if (bucket.toDay() == null) {
                if (i != buckets.size() - 1) {
                    return "only the last bucket may be open-ended; " + bucket.code() + " is not last";
                }
                return null;
            }
            if (bucket.toDay() < bucket.fromDay()) {
                return "bucket " + bucket.code() + " ends before it starts";
            }
            expected = bucket.toDay() + 1;
        }
        return null;
    }
}
