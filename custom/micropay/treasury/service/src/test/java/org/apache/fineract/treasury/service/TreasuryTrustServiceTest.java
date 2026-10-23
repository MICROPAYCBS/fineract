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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.data.TreasuryTrustData;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyRepository;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.domain.TreasuryEMoneyBalanceRepository;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
import org.apache.fineract.treasury.domain.TreasuryRunTrust;
import org.apache.fineract.treasury.domain.TreasuryRunTrustLine;
import org.apache.fineract.treasury.domain.TreasuryRunTrustLineRepository;
import org.apache.fineract.treasury.domain.TreasuryRunTrustRepository;
import org.apache.fineract.treasury.domain.TreasuryTenantSetting;
import org.apache.fineract.treasury.domain.TreasuryTrustAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryTrustHolding;
import org.apache.fineract.treasury.domain.TreasuryTrustHoldingRepository;
import org.apache.fineract.treasury.domain.TreasuryTrustInterest;
import org.apache.fineract.treasury.domain.TreasuryTrustInterestRepository;
import org.apache.fineract.treasury.liquidity.RunType;
import org.apache.fineract.treasury.trust.EMoneyCategory;
import org.apache.fineract.treasury.trust.EMoneySource;
import org.apache.fineract.treasury.trust.TrustAssetType;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class TreasuryTrustServiceTest {

    private final TreasuryRunRepository runs = mock(TreasuryRunRepository.class);
    private final TreasuryRunTrustRepository trusts = mock(TreasuryRunTrustRepository.class);
    private final TreasuryRunTrustLineRepository lines = mock(TreasuryRunTrustLineRepository.class);
    private final TreasuryTrustHoldingRepository holdings = mock(TreasuryTrustHoldingRepository.class);
    private final TreasuryTrustInterestRepository interest = mock(TreasuryTrustInterestRepository.class);
    private final TreasuryTenantSettingService settings = mock(TreasuryTenantSettingService.class);
    private final EMoneySource source = mock(EMoneySource.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final TreasuryTrustService service = new TreasuryTrustService(mock(TreasuryAccountRepository.class),
            mock(TreasuryTrustAccountRepository.class), this.holdings, mock(TreasuryEMoneyBalanceRepository.class), this.runs, this.trusts,
            this.lines, this.interest, mock(TreasuryCounterpartyRepository.class), mock(TreasuryDealRepository.class),
            mock(TreasuryDealTypeService.class), mock(TreasuryPledgeService.class), mock(TreasuryBalanceReader.class), this.settings,
            mock(TreasuryRuleSetService.class), mock(TreasuryLimitEngine.class), new TreasuryFxConfig("UGX", 3),
            mock(TreasuryReferenceDataLookup.class), List.of(this.source), mock(TreasuryActor.class), new FromJsonHelper(), this.jdbc,
            mock(PlatformTransactionManager.class), mock(AppUserRepositoryWrapper.class));

    @Test
    void theStatementIsReadFromWhatWasStoredNotRecomputed() {
        final TreasuryRun run = new TreasuryRun();
        run.setId(70L);
        run.setRunType(RunType.TRUST_RECON);
        run.setAsOfDate(LocalDate.of(2026, 10, 16));
        run.setCurrencyCode("UGX");
        run.setStatus("SUBMITTED");
        run.setCreatedOnUtc(OffsetDateTime.parse("2026-10-17T02:47:00Z"));
        when(this.runs.findById(70L)).thenReturn(Optional.of(run));
        final TreasuryRunTrust t = new TreasuryRunTrust();
        t.setRunId(70L);
        t.setEmoneySource("IMPORTED");
        t.setEmoneyTotal(new BigDecimal("1000"));
        t.setTrustTotal(new BigDecimal("1100"));
        t.setEligibleTotal(new BigDecimal("900"));
        t.setShortfall(new BigDecimal("100"));
        t.setCoveragePercent(new BigDecimal("90.0000"));
        when(this.trusts.findByRunId(70L)).thenReturn(Optional.of(t));
        final TreasuryRunTrustLine e = new TreasuryRunTrustLine();
        e.setLineType("EMONEY");
        e.setCategory(EMoneyCategory.CUSTOMER);
        e.setAmount(new BigDecimal("1000"));
        final TreasuryRunTrustLine a = new TreasuryRunTrustLine();
        a.setLineType("ASSET");
        a.setAssetType(TrustAssetType.DEMAND_DEPOSIT);
        a.setInstitutionId(7L);
        a.setInstitutionName("Bank, \"A\"");
        a.setLabel("TRUST-1");
        a.setAmount(new BigDecimal("1100"));
        a.setEligible(new BigDecimal("900"));
        a.setEncumbered(BigDecimal.ZERO);
        when(this.lines.findByRunIdOrderByIdAsc(70L)).thenReturn(List.of(e, a));

        final TreasuryTrustData.Statement first = this.service.statement(70L);
        final TreasuryTrustData.Statement second = this.service.statement(70L);
        assertEquals(first, second);
        verifyNoInteractions(this.source);
        assertEquals(Map.of("CUSTOMER", new BigDecimal("1000")), first.emoney());
        assertEquals(0, new BigDecimal("110.0000").compareTo(first.institutions().getFirst().percentOfEmoney()));
        final String csv = TreasuryTrustService.csv(first);
        assertEquals(csv, TreasuryTrustService.csv(second));
        assertTrue(csv.startsWith("section,item,institution,asset_type,amount,eligible,encumbered,percent_of_emoney\n"));
        assertTrue(csv.contains("summary,\"shortfall\",,,100,,,"), csv);
        assertTrue(csv.contains("asset,\"TRUST-1\",\"Bank, \"\"A\"\"\",\"DEMAND_DEPOSIT\",1100,900,0,"), csv);
    }

    @Test
    void theDaysInterestIsTheTrustDealsAccrualsAndTheBankInterestCredited() {
        final TreasuryTrustHolding holding = new TreasuryTrustHolding();
        holding.setDealId(41L);
        when(this.holdings.findAllByOrderByIdAsc()).thenReturn(List.of(holding));
        when(this.jdbc.queryForList(any(String.class), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of("deal_id", 41L, "interest", new BigDecimal("1234.56"))));
        final TreasuryTenantSetting setting = new TreasuryTenantSetting();
        setting.setTrustInterestGlAccountId(88L);
        when(this.settings.setting()).thenReturn(setting);
        when(this.jdbc.queryForObject(any(String.class), any(MapSqlParameterSource.class), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("500"));
        final List<TreasuryTrustInterest> saved = new ArrayList<>();
        when(this.interest.save(any())).thenAnswer(call -> {
            saved.add(call.getArgument(0));
            return call.getArgument(0);
        });
        final LocalDate date = LocalDate.of(2026, 10, 15);
        assertEquals(2, this.service.accrueInterest(date, "UGX"));
        verify(this.interest).deleteByInterestDateAndCurrencyCode(date, "UGX");
        assertEquals("DEAL_ACCRUAL 41 1234.56", saved.get(0).getSource() + " " + saved.get(0).getDealId() + " " + saved.get(0).getAmount());
        assertEquals("BANK_INTEREST 88 500",
                saved.get(1).getSource() + " " + saved.get(1).getGlAccountId() + " " + saved.get(1).getAmount());
    }

    @Test
    void theInputsAreValidated() {
        assertThrows(PlatformApiDataValidationException.class, () -> this.service.createHolding(JsonCommand.from("{}")),
                "dealId and trustAccountId are required");
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.service.importEMoney(JsonCommand.from("{\"balanceDate\": \"2026-10-16\", \"customers\": -5}")),
                "a negative balance");
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.service.importEMoney(JsonCommand.from("{\"balanceDate\": \"2026-10-16\"}")), "no category");
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.service.importEMoney(JsonCommand.from("{\"balanceDate\": \"16/10/2026\", \"agents\": 5}")), "a bad date");
    }
}
