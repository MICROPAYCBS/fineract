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

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NopCapitalBasisTest {

    @Test
    void theCapitalDateIsTheEndOfThePrecedingQuarter() {
        assertEquals(LocalDate.of(2026, 9, 30), NopCapitalBasis.PRECEDING_QUARTER.capitalDate(LocalDate.of(2026, 10, 16)));
        assertEquals(LocalDate.of(2026, 9, 30), NopCapitalBasis.PRECEDING_QUARTER.capitalDate(LocalDate.of(2026, 12, 31)));
        assertEquals(LocalDate.of(2026, 9, 30), NopCapitalBasis.PRECEDING_QUARTER.capitalDate(LocalDate.of(2026, 10, 1)));
        assertEquals(LocalDate.of(2026, 6, 30), NopCapitalBasis.PRECEDING_QUARTER.capitalDate(LocalDate.of(2026, 9, 30)),
                "a quarter end looks at the quarter before");
        assertEquals(LocalDate.of(2025, 12, 31), NopCapitalBasis.PRECEDING_QUARTER.capitalDate(LocalDate.of(2026, 2, 28)));
        assertEquals(LocalDate.of(2026, 10, 16), NopCapitalBasis.LATEST.capitalDate(LocalDate.of(2026, 10, 16)));
    }
}
