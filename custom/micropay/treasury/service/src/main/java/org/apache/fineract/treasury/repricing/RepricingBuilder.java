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
package org.apache.fineract.treasury.repricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.fineract.treasury.liquidity.Bucket;
import org.apache.fineract.treasury.liquidity.LadderContext;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;

/**
 * Buckets repricing positions and works out the gap: per bucket the rate-sensitive assets (RSA) and liabilities (RSL)
 * by rate type, the gap (RSA - RSL), the cumulative gap, the gap as a percentage of total assets and RSA / RSL; and the
 * change in net interest income over the next twelve months for a parallel rate shock.
 */
public final class RepricingBuilder {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal BASIS_POINTS = BigDecimal.valueOf(10_000);
    private static final BigDecimal YEAR = BigDecimal.valueOf(365);
    /** A bucket counts for earnings at risk when it ends within the year. */
    public static final int YEAR_DAYS = 365;

    private RepricingBuilder() {}

    /** Positions summed per bucket (null for non-sensitive), source, line, product, side and rate type. */
    public record Line(Bucket bucket, LadderSourceCode source, String lineCode, Long productId, String productName, RepricingSide side,
            RateType rateType, BigDecimal amount) {
    }

    public record BucketGap(Bucket bucket, Map<RateType, BigDecimal> assets, Map<RateType, BigDecimal> liabilities, BigDecimal rsa,
            BigDecimal rsl, BigDecimal gap, BigDecimal cumulativeGap, BigDecimal gapPercent, BigDecimal cumulativePercent,
            BigDecimal ratio) {
    }

    public record Gap(List<Line> lines, List<BucketGap> buckets, BigDecimal nonSensitiveAssets, BigDecimal nonSensitiveLiabilities,
            BigDecimal cumulativeGapYear, BigDecimal cumulativeGapYearPercent) {
    }

    public static Gap build(final LadderContext context, final List<RepricingPosition> positions, final BigDecimal totalAssets) {
        final Map<List<Object>, Line> lines = new LinkedHashMap<>();
        for (final RepricingPosition p : positions) {
            if (p.amount() == null || p.amount().signum() == 0) {
                continue;
            }
            final Bucket bucket = p.rateType().isSensitive() ? bucketOf(context, p) : null;
            final List<Object> key = Arrays.asList(bucket == null ? null : bucket.seq(), p.source(), p.lineCode(), p.productId(), p.side(),
                    p.rateType());
            final Line current = lines.get(key);
            lines.put(key,
                    new Line(bucket, p.source(), p.lineCode(), p.productId(), current == null ? p.productName() : current.productName(),
                            p.side(), p.rateType(), (current == null ? BigDecimal.ZERO : current.amount()).add(p.amount())));
        }
        final List<Line> sorted = new ArrayList<>(lines.values());
        sorted.sort(Comparator.comparing((Line l) -> l.bucket() == null ? Integer.MAX_VALUE : l.bucket().seq()).thenComparing(Line::source)
                .thenComparing(Line::side).thenComparing(Line::rateType).thenComparing(l -> Objects.toString(l.lineCode(), ""))
                .thenComparing(l -> l.productId() == null ? 0L : l.productId()));
        final List<BucketGap> gaps = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        BigDecimal cumulativeYear = BigDecimal.ZERO;
        for (final Bucket bucket : context.buckets()) {
            final Map<RateType, BigDecimal> assets = new EnumMap<>(RateType.class);
            final Map<RateType, BigDecimal> liabilities = new EnumMap<>(RateType.class);
            for (final Line line : sorted) {
                if (line.bucket() != null && line.bucket().seq() == bucket.seq()) {
                    (line.side() == RepricingSide.ASSET ? assets : liabilities).merge(line.rateType(), line.amount(), BigDecimal::add);
                }
            }
            final BigDecimal rsa = assets.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            final BigDecimal rsl = liabilities.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            final BigDecimal gap = rsa.subtract(rsl);
            cumulative = cumulative.add(gap);
            if (withinYear(bucket)) {
                cumulativeYear = cumulative;
            }
            gaps.add(new BucketGap(bucket, assets, liabilities, rsa, rsl, gap, cumulative, percent(gap, totalAssets),
                    percent(cumulative, totalAssets), rsl.signum() == 0 ? null : rsa.divide(rsl, 4, RoundingMode.HALF_UP)));
        }
        final BigDecimal nsAssets = sum(sorted, RepricingSide.ASSET);
        final BigDecimal nsLiabilities = sum(sorted, RepricingSide.LIABILITY);
        return new Gap(sorted, gaps, nsAssets, nsLiabilities, cumulativeYear, percent(cumulativeYear, totalAssets));
    }

    /**
     * The change in net interest income over twelve months: each gap within the year x the shock x the rest of the year
     * from the bucket's midpoint.
     */
    public static BigDecimal deltaNii(final List<BucketGap> buckets, final int shockBasisPoints, final int scale) {
        BigDecimal total = BigDecimal.ZERO;
        final BigDecimal shock = BigDecimal.valueOf(shockBasisPoints).divide(BASIS_POINTS, 10, RoundingMode.HALF_UP);
        for (final BucketGap b : buckets) {
            if (!withinYear(b.bucket())) {
                continue;
            }
            final BigDecimal midpoint = BigDecimal.valueOf(b.bucket().fromDay() + b.bucket().toDay()).divide(BigDecimal.valueOf(2), 4,
                    RoundingMode.HALF_UP);
            total = total.add(b.gap().multiply(shock).multiply(YEAR.subtract(midpoint)).divide(YEAR, 10, RoundingMode.HALF_UP));
        }
        return total.setScale(scale, RoundingMode.HALF_UP);
    }

    public static boolean withinYear(final Bucket bucket) {
        return bucket.toDay() != null && bucket.toDay() <= YEAR_DAYS;
    }

    /** The loss as a percentage of the base: none for a gain, none without a base. */
    public static BigDecimal earPercent(final BigDecimal deltaNii, final BigDecimal base) {
        if (base == null || base.signum() <= 0) {
            return null;
        }
        return deltaNii.min(BigDecimal.ZERO).negate().multiply(HUNDRED).divide(base, 4, RoundingMode.HALF_UP);
    }

    static Bucket bucketOf(final LadderContext context, final RepricingPosition position) {
        if (position.bucketCode() != null) {
            return context.buckets().stream().filter(b -> b.code().equals(position.bucketCode())).findFirst().orElse(context.first());
        }
        final long day = position.repricingDate() == null ? 0
                : Math.max(0, ChronoUnit.DAYS.between(context.asOf(), position.repricingDate()));
        return context.buckets().stream().filter(b -> b.contains(day)).findFirst().orElse(context.last());
    }

    static BigDecimal percent(final BigDecimal value, final BigDecimal total) {
        return total == null || total.signum() <= 0 ? null : value.multiply(HUNDRED).divide(total, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(final List<Line> lines, final RepricingSide side) {
        return lines.stream().filter(l -> l.bucket() == null && l.side() == side).map(Line::amount).reduce(BigDecimal.ZERO,
                BigDecimal::add);
    }
}
