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
package org.apache.fineract.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.treasury.data.TreasuryAccountData;
import org.apache.fineract.treasury.data.TreasuryPositionData;
import org.apache.fineract.treasury.data.TreasuryPositionSummaryData;
import org.apache.fineract.treasury.service.TreasuryPositionCalculator.LastSnapshot;
import org.junit.jupiter.api.Test;

class TreasuryPositionCalculatorTest {

    @Test
    void positionCarriesBalanceLevelsAndLastSnapshot() {
        final LastSnapshot snapshot = new LastSnapshot(LocalDate.of(2026, 10, 21), new BigDecimal("400"), "BELOW_MIN");

        final TreasuryPositionData position = TreasuryPositionCalculator.position(account(1L, 1L, "UGX", "500", "2000"),
                new BigDecimal("2500"), new BigDecimal("100"), new BigDecimal("30"), snapshot);

        assertEquals(new BigDecimal("2500"), position.balance());
        assertEquals(new BigDecimal("2570"), position.projectedBalance());
        assertEquals("ABOVE_MAX", position.levelStatus());
        assertEquals(LocalDate.of(2026, 10, 21), position.lastSnapshotDate());
        assertEquals("BELOW_MIN", position.lastSnapshotLevelStatus());
    }

    @Test
    void accountWithoutEntriesHasZeroBalance() {
        final TreasuryPositionData position = TreasuryPositionCalculator.position(account(1L, 1L, "UGX", null, null), null, null, null,
                null);

        assertEquals(BigDecimal.ZERO, position.balance());
        assertEquals(BigDecimal.ZERO, position.projectedBalance());
        assertEquals("OK", position.levelStatus());
        assertNull(position.lastSnapshotDate());
    }

    @Test
    void summaryGroupsByOfficeAndCurrency() {
        final List<TreasuryPositionData> positions = List.of(
                TreasuryPositionCalculator.position(account(1L, 1L, "UGX", "1000", null), new BigDecimal("500"), null, null, null),
                TreasuryPositionCalculator.position(account(2L, 2L, "UGX", null, null), new BigDecimal("70"), null, null, null),
                TreasuryPositionCalculator.position(account(3L, 1L, "UGX", null, "100"), new BigDecimal("200"), null, null, null),
                TreasuryPositionCalculator.position(account(4L, 1L, "USD", null, null), new BigDecimal("9"), null, null, null));

        final List<TreasuryPositionSummaryData> summaries = TreasuryPositionCalculator.summarise(positions);

        assertEquals(3, summaries.size());
        final TreasuryPositionSummaryData headOfficeUgx = summaries.getFirst();
        assertEquals(1L, headOfficeUgx.officeId());
        assertEquals("UGX", headOfficeUgx.currencyCode());
        assertEquals(2, headOfficeUgx.accountCount());
        assertEquals(new BigDecimal("700"), headOfficeUgx.balance());
        assertEquals(1, headOfficeUgx.accountsBelowMin());
        assertEquals(1, headOfficeUgx.accountsAboveMax());
        assertEquals(List.of(1L, 3L), headOfficeUgx.accounts().stream().map(TreasuryPositionData::treasuryAccountId).toList());
        assertEquals(2L, summaries.get(1).officeId());
        assertEquals("USD", summaries.get(2).currencyCode());
    }

    private static TreasuryAccountData account(final Long id, final Long officeId, final String currency, final String min,
            final String max) {
        return new TreasuryAccountData(id, "ACC" + id, "Account " + id, "BANK", 10L, "1101", "Bank", officeId, "Office " + officeId,
                currency, null, null, min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max), "ACTIVE", null,
                null);
    }
}
