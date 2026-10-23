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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.treasury.domain.TreasuryCoreCapital;
import org.apache.fineract.treasury.domain.TreasuryCoreCapitalRepository;
import org.apache.fineract.treasury.domain.TreasuryTenantSetting;
import org.apache.fineract.treasury.domain.TreasuryTenantSettingRepository;
import org.apache.fineract.treasury.fx.NopCapitalBasis;
import org.junit.jupiter.api.Test;

/** The tenant's own settings over the deployment defaults, and the core capital the NOP is measured against. */
class TreasuryFxConfigTest {

    private final TreasuryTenantSettingRepository settings = mock(TreasuryTenantSettingRepository.class);
    private final TreasuryFxConfig config = new TreasuryFxConfig(" ugx ", 3, this.settings);

    @Test
    void unsetSettingsFallBackToTheDeploymentProperties() {
        when(this.settings.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        assertEquals("UGX", this.config.baseCurrency());
        assertEquals(3, this.config.maxRateAgeDays());
        assertEquals(NopCapitalBasis.PRECEDING_QUARTER, this.config.nopCapitalBasis());

        when(this.settings.findFirstByOrderByIdAsc()).thenReturn(Optional.of(new TreasuryTenantSetting()));
        assertEquals("UGX", this.config.baseCurrency(), "a row with nothing set");
        assertEquals(3, this.config.maxRateAgeDays());
    }

    @Test
    void theTenantSettingsWinAndAreReadEachTime() {
        final TreasuryTenantSetting row = new TreasuryTenantSetting();
        row.setBaseCurrency("KES");
        row.setFxMaxRateAgeDays(5);
        row.setNopCapitalBasis(NopCapitalBasis.LATEST);
        when(this.settings.findFirstByOrderByIdAsc()).thenReturn(Optional.of(row));
        assertEquals("KES", this.config.baseCurrency());
        assertEquals(5, this.config.maxRateAgeDays());
        assertEquals(NopCapitalBasis.LATEST, this.config.nopCapitalBasis());
        when(this.settings.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        assertEquals("UGX", this.config.baseCurrency(), "nothing kept from the last read (another tenant)");
    }

    @Test
    void theNopIsMeasuredAgainstCoreCapitalAsAtThePrecedingQuarter() {
        final TreasuryCoreCapitalRepository capitals = mock(TreasuryCoreCapitalRepository.class);
        when(capitals.findAllByOrderByEffectiveDateAscIdAsc())
                .thenReturn(List.of(capital("2026-06-30", "30000000"), capital("2026-10-05", "40000000")));
        final TreasuryFxPositionService service = new TreasuryFxPositionService(null, null, null, this.config, capitals, null, null, null,
                null, null, null);
        final LocalDate asOf = LocalDate.of(2026, 10, 16);

        when(this.settings.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        assertEquals(0, new BigDecimal("30000000").compareTo(service.nopCoreCapital(asOf).orElseThrow().getAmount()),
                "the 2026-10-05 figure is after the end of the preceding quarter");

        final TreasuryTenantSetting latest = new TreasuryTenantSetting();
        latest.setNopCapitalBasis(NopCapitalBasis.LATEST);
        when(this.settings.findFirstByOrderByIdAsc()).thenReturn(Optional.of(latest));
        assertEquals(0, new BigDecimal("40000000").compareTo(service.nopCoreCapital(asOf).orElseThrow().getAmount()));
    }

    private static TreasuryCoreCapital capital(final String date, final String amount) {
        final TreasuryCoreCapital capital = new TreasuryCoreCapital();
        capital.setEffectiveDate(LocalDate.parse(date));
        capital.setAmount(new BigDecimal(amount));
        return capital;
    }
}
