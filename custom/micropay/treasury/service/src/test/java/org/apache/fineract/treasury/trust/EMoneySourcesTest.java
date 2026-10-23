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
package org.apache.fineract.treasury.trust;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.fineract.treasury.domain.TreasuryEMoneyBalance;
import org.apache.fineract.treasury.domain.TreasuryEMoneyBalanceRepository;
import org.apache.fineract.treasury.domain.TreasuryTenantSetting;
import org.apache.fineract.treasury.service.TreasuryTenantSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class EMoneySourcesTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 16);

    @Test
    void fineractSavingsSumsTheProductsOfEachCategory() throws Exception {
        final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        final TreasuryTenantSettingService settings = mock(TreasuryTenantSettingService.class);
        final TreasuryTenantSetting setting = new TreasuryTenantSetting();
        setting.setEmoneyCustomerProducts("3,4");
        setting.setEmoneyAgentProducts("5");
        when(settings.setting()).thenReturn(setting);
        doAnswer(call -> {
            final RowCallbackHandler handler = call.getArgument(2);
            for (final Object[] row : new Object[][] { { 3L, "700" }, { 4L, "300.50" }, { 5L, "1200" } }) {
                final ResultSet rs = mock(ResultSet.class);
                when(rs.getLong("product_id")).thenReturn((Long) row[0]);
                when(rs.getBigDecimal("balance")).thenReturn(new BigDecimal((String) row[1]));
                handler.processRow(rs);
            }
            return null;
        }).when(jdbc).query(eq(FineractSavingsEMoneySource.SQL), any(MapSqlParameterSource.class), any(RowCallbackHandler.class));
        final Map<EMoneyCategory, BigDecimal> outstanding = new FineractSavingsEMoneySource(jdbc, settings).outstanding(AS_OF, "UGX");
        assertEquals(0, new BigDecimal("1000.50").compareTo(outstanding.get(EMoneyCategory.CUSTOMER)));
        assertEquals(0, new BigDecimal("1200").compareTo(outstanding.get(EMoneyCategory.AGENT)));
        assertEquals(2, outstanding.size(), "no OTHER products, no OTHER line");
    }

    @Test
    void fineractSavingsWithoutProductsHasNoEmoney() {
        final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        final TreasuryTenantSettingService settings = mock(TreasuryTenantSettingService.class);
        when(settings.setting()).thenReturn(new TreasuryTenantSetting());
        assertTrue(new FineractSavingsEMoneySource(jdbc, settings).outstanding(AS_OF, "UGX").isEmpty());
        verifyNoInteractions(jdbc);
    }

    @Test
    void importedReadsTheDatesBalances() {
        final TreasuryEMoneyBalanceRepository repository = mock(TreasuryEMoneyBalanceRepository.class);
        final TreasuryEMoneyBalance agents = new TreasuryEMoneyBalance();
        agents.setCategory(EMoneyCategory.AGENT);
        agents.setAmount(new BigDecimal("250"));
        when(repository.findByBalanceDateAndCurrencyCode(AS_OF, "UGX")).thenReturn(List.of(agents));
        final ImportedEMoneySource source = new ImportedEMoneySource(repository);
        assertEquals(EMoneySourceType.IMPORTED, source.type());
        assertEquals(Map.of(EMoneyCategory.AGENT, new BigDecimal("250")), source.outstanding(AS_OF, "UGX"));
    }
}
