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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The behavioural assumptions of a run, looked up by type and source. A row for the product wins over a general row (no
 * product). {@link #NONE} has none: the ladder is then contractual only.
 */
public final class Assumptions {

    public static final Assumptions NONE = new Assumptions(List.of());

    /** One assumption row as the ladder reads it. */
    public record Row(AssumptionType type, LadderSourceCode source, Long productId, String bucketCode, Integer minDays, Integer maxDays,
            BigDecimal rate) {
    }

    private final List<Row> rows;

    public Assumptions(final List<Row> rows) {
        this.rows = List.copyOf(rows);
    }

    public boolean isEmpty() {
        return this.rows.isEmpty();
    }

    /** The rate of a product (else the general rate), for a bucket when given. */
    public Optional<BigDecimal> rate(final AssumptionType type, final LadderSourceCode source, final Long productId,
            final String bucketCode) {
        return pick(type, source, productId, row -> Objects.equals(row.bucketCode(), bucketCode)).map(Row::rate);
    }

    /** The row whose days band (min to max, max open when null) holds the days, for the product or else general. */
    public Optional<Row> band(final AssumptionType type, final LadderSourceCode source, final Long productId, final long days) {
        return pick(type, source, productId,
                row -> row.minDays() != null && days >= row.minDays() && (row.maxDays() == null || days <= row.maxDays()));
    }

    /** Every row of the type and source for the product, or the general rows when the product has none. */
    public List<Row> rows(final AssumptionType type, final LadderSourceCode source, final Long productId) {
        final List<Row> specific = this.rows.stream()
                .filter(r -> r.type() == type && r.source() == source && productId != null && productId.equals(r.productId())).toList();
        return specific.isEmpty()
                ? this.rows.stream().filter(r -> r.type() == type && r.source() == source && r.productId() == null).toList()
                : specific;
    }

    private Optional<Row> pick(final AssumptionType type, final LadderSourceCode source, final Long productId,
            final java.util.function.Predicate<Row> matches) {
        final Optional<Row> specific = this.rows.stream().filter(
                r -> r.type() == type && r.source() == source && productId != null && productId.equals(r.productId()) && matches.test(r))
                .findFirst();
        return specific.isPresent() ? specific
                : this.rows.stream().filter(r -> r.type() == type && r.source() == source && r.productId() == null && matches.test(r))
                        .findFirst();
    }
}
