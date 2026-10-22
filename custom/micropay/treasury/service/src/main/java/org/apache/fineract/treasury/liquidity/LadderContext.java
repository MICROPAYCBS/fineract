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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * What every ladder source needs: as-of date, currency, scope (an office, or null for the institution), buckets,
 * assumptions and the currency's decimal places. Sources add warnings for assumptions they cannot apply.
 */
public record LadderContext(LocalDate asOf, String currencyCode, Long officeId, List<Bucket> buckets, Assumptions assumptions, int scale,
        List<String> warnings) {

    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public LadderContext(final LocalDate asOf, final String currencyCode, final Long officeId, final List<Bucket> buckets,
            final Assumptions assumptions, final int scale) {
        this(asOf, currencyCode, officeId, List.copyOf(buckets), assumptions, scale, new ArrayList<>());
    }

    /** The first day of a bucket as a date. */
    public LocalDate start(final Bucket bucket) {
        return this.asOf.plusDays(bucket.fromDay());
    }

    public Bucket first() {
        return this.buckets.getFirst();
    }

    public Bucket last() {
        return this.buckets.getLast();
    }

    /** The last date a configured item is expanded to: the last bucket's end, or a year past its start when open. */
    public LocalDate horizonEnd() {
        final Bucket last = last();
        return this.asOf.plusDays(last.toDay() == null ? last.fromDay() + 365L : last.toDay());
    }

    public Bucket bucketCode(final String code) {
        return this.buckets.stream().filter(b -> b.code().equals(code)).findFirst().orElse(null);
    }

    /** {@code amount x rate / 100}, rounded half up to the currency. */
    public BigDecimal percent(final BigDecimal amount, final BigDecimal rate) {
        return amount.multiply(rate).divide(HUNDRED, this.scale, RoundingMode.HALF_UP);
    }

    public BigDecimal round(final BigDecimal amount) {
        return amount.setScale(this.scale, RoundingMode.HALF_UP);
    }

    public void warn(final String warning) {
        if (!this.warnings.contains(warning)) {
            this.warnings.add(warning);
        }
    }
}
