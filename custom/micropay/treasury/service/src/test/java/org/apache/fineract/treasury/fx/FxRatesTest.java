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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The rate in effect on a date, the source preference and staleness. */
class FxRatesTest {

    private static final LocalDate D10 = LocalDate.of(2026, 10, 10);
    private static final LocalDate D13 = LocalDate.of(2026, 10, 13);
    private static final LocalDate D16 = LocalDate.of(2026, 10, 16);

    private final List<FxRates.Rate> rates = List.of(rate(1L, D10, FxRateType.MID, FxRateSource.MANUAL, "3700"),
            rate(2L, D13, FxRateType.MID, FxRateSource.IMPORT, "3710"), rate(3L, D13, FxRateType.MID, FxRateSource.BOU, "3712"),
            rate(4L, D13, FxRateType.MID, FxRateSource.MANUAL, "3711"), rate(5L, D13, FxRateType.BUY, FxRateSource.MANUAL, "3690"),
            rate(6L, LocalDate.of(2026, 10, 20), FxRateType.MID, FxRateSource.MANUAL, "3800"));

    @Test
    void theLatestOnOrBeforeTheDateBoUFirst() {
        assertEquals(3L, FxRates.latest(this.rates, FxRateType.MID, D16).orElseThrow().id(), "BoU before manual before imported");
        assertEquals(1L, FxRates.latest(this.rates, FxRateType.MID, LocalDate.of(2026, 10, 12)).orElseThrow().id());
        assertEquals(5L, FxRates.latest(this.rates, FxRateType.BUY, D16).orElseThrow().id());
        assertTrue(FxRates.latest(this.rates, FxRateType.MID, LocalDate.of(2026, 10, 9)).isEmpty(), "nothing before the first rate");
        assertEquals(6L, FxRates.latest(this.rates, FxRateType.MID, LocalDate.of(2026, 10, 25)).orElseThrow().id());
        final List<FxRates.Rate> sameSource = List.of(rate(7L, D13, FxRateType.MID, FxRateSource.MANUAL, "1"),
                rate(8L, D13, FxRateType.MID, FxRateSource.MANUAL, "2"));
        assertEquals(8L, FxRates.latest(sameSource, FxRateType.MID, D16).orElseThrow().id(), "the latest entered");
    }

    @Test
    void aRateOlderThanTheMaximumAgeIsStale() {
        final FxRates.Rate rate = rate(1L, D13, FxRateType.MID, FxRateSource.BOU, "3712");
        assertFalse(FxRates.isStale(rate, D16, 3), "three days old");
        assertTrue(FxRates.isStale(rate, LocalDate.of(2026, 10, 17), 3));
        assertFalse(FxRates.isStale(rate, D13, 0));
    }

    private static FxRates.Rate rate(final long id, final LocalDate date, final FxRateType type, final FxRateSource source,
            final String rate) {
        return new FxRates.Rate(id, date, type, source, new BigDecimal(rate));
    }
}
