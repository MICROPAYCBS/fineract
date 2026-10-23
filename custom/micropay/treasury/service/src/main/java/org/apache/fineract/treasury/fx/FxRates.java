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
package org.apache.fineract.treasury.fx;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picking a rate: the latest on or before the date; on the same date BoU before manual before imported, then the latest
 * entered.
 */
public final class FxRates {

    private FxRates() {}

    /** A rate of a pair: units of the base currency for one unit of the quote currency. */
    public record Rate(Long id, LocalDate rateDate, FxRateType type, FxRateSource source, BigDecimal rate) {
    }

    public static Optional<Rate> latest(final List<Rate> rates, final FxRateType type, final LocalDate date) {
        return rates.stream().filter(r -> r.type() == type && !r.rateDate().isAfter(date))
                .max(Comparator.comparing(Rate::rateDate).thenComparing(r -> -r.source().ordinal()).thenComparing(Rate::id));
    }

    /** Older than the maximum age in days on the date: refused for revaluation. */
    public static boolean isStale(final Rate rate, final LocalDate date, final int maxAgeDays) {
        return ChronoUnit.DAYS.between(rate.rateDate(), date) > maxAgeDays;
    }
}
