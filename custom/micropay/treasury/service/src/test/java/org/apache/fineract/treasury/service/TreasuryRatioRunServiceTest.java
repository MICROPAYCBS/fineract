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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.treasury.data.TreasuryReturnData;
import org.apache.fineract.treasury.domain.TreasuryAssetClassRule;
import org.apache.fineract.treasury.domain.TreasuryRatioComponent;
import org.apache.fineract.treasury.domain.TreasuryRatioDefinition;
import org.apache.fineract.treasury.domain.TreasuryRuleSet;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunComponent;
import org.apache.fineract.treasury.domain.TreasuryRunComponentRepository;
import org.apache.fineract.treasury.domain.TreasuryRunLineRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRatio;
import org.apache.fineract.treasury.domain.TreasuryRunRatioRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
import org.apache.fineract.treasury.domain.TreasuryRunReturnLine;
import org.apache.fineract.treasury.domain.TreasuryRunReturnLineRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.liquidity.ComponentType;
import org.apache.fineract.treasury.liquidity.RatioDirection;
import org.apache.fineract.treasury.liquidity.RatioPart;
import org.apache.fineract.treasury.liquidity.RatioStatus;
import org.apache.fineract.treasury.liquidity.RunType;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Worked example: liquid assets of 2,500 cash and a bond class of 300,000 with 50,000 beyond its cut-off, 25,000
 * pledged and a 10% haircut (202,500); deposits of 1,000,000. Liquid assets to deposits 20.5%, OK against 15% (warning
 * 17%).
 */
class TreasuryRatioRunServiceTest {

    private final TreasuryRuleSetService ruleSetService = mock(TreasuryRuleSetService.class);
    private final TreasuryRatioInputs inputs = mock(TreasuryRatioInputs.class);
    private final TreasuryRunRatioRepository ratios = mock(TreasuryRunRatioRepository.class);
    private final TreasuryRunComponentRepository components = mock(TreasuryRunComponentRepository.class);
    private final TreasuryRunReturnLineRepository returnLines = mock(TreasuryRunReturnLineRepository.class);
    private final TreasuryRatioRunService service = new TreasuryRatioRunService(this.ruleSetService, this.inputs, this.ratios,
            this.components, this.returnLines);
    private final List<TreasuryRunRatio> savedRatios = new ArrayList<>();
    private final List<TreasuryRunComponent> savedComponents = new ArrayList<>();
    private final List<TreasuryRunReturnLine> savedLines = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", mock(FineractPlatformTenantConnection.class)));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 10, 16))));
        final TreasuryRatioDefinition ratio = new TreasuryRatioDefinition();
        ratio.setId(11L);
        ratio.setSequenceNumber(1);
        ratio.setCode("LIQUID_ASSETS_TO_DEPOSITS");
        ratio.setName("Liquid assets to total deposits");
        ratio.setDirection(RatioDirection.MIN);
        ratio.setLimitValue(BigDecimal.valueOf(15));
        ratio.setWarningValue(BigDecimal.valueOf(17));
        ratio.setInReturn(true);
        ratio.setNumeratorLabel("Liquid assets");
        ratio.setDenominatorLabel("Deposit liabilities");
        lenient().when(this.ruleSetService.ratios(1L)).thenReturn(List.of(ratio));
        lenient().when(this.ruleSetService.components(11L))
                .thenReturn(List.of(component(RatioPart.NUMERATOR, 1, ComponentType.ASSET_CLASS, "CASH"),
                        component(RatioPart.NUMERATOR, 2, ComponentType.ASSET_CLASS, "GOVT_BOND"),
                        component(RatioPart.DENOMINATOR, 1, ComponentType.GL_ACCOUNT, null)));
        lenient().when(this.ruleSetService.assetClasses(1L)).thenReturn(List.of(rule("CASH", "0"), rule("GOVT_BOND", "10")));
        lenient().when(this.inputs.asset(eq(rule("CASH", "0")), any(), any(), any(), anyInt()))
                .thenReturn(new TreasuryRatioInputs.AssetValue(new BigDecimal("2500"), BigDecimal.ZERO, BigDecimal.ZERO));
        lenient().when(this.inputs.asset(eq(rule("GOVT_BOND", "10")), any(), any(), any(), anyInt()))
                .thenReturn(new TreasuryRatioInputs.AssetValue(new BigDecimal("300000"), new BigDecimal("50000"), new BigDecimal("25000")));
        lenient().when(this.inputs.gl(any(), any(), any(), any())).thenReturn(new BigDecimal("1000000"));
        lenient().when(this.ratios.save(any())).thenAnswer(call -> {
            this.savedRatios.add(call.getArgument(0));
            return call.getArgument(0);
        });
        lenient().when(this.components.save(any())).thenAnswer(call -> {
            this.savedComponents.add(call.getArgument(0));
            return call.getArgument(0);
        });
        lenient().when(this.returnLines.save(any())).thenAnswer(call -> {
            this.savedLines.add(call.getArgument(0));
            return call.getArgument(0);
        });
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void ratiosWithExclusionsAndHaircutsAndTheConfigurationKept() {
        final TreasuryRun run = run(20L, RunType.RATIOS);
        this.service.computeRatios(run, run(10L, RunType.LADDER), 2);

        final TreasuryRunRatio ratio = this.savedRatios.getFirst();
        assertEquals(new BigDecimal("205000.00"), ratio.getNumerator());
        assertEquals(new BigDecimal("1000000.00"), ratio.getDenominator());
        assertEquals(new BigDecimal("20.5000"), ratio.getResult());
        assertEquals(RatioStatus.OK, ratio.getStatus());
        final TreasuryRunComponent bond = this.savedComponents.get(1);
        assertEquals(new BigDecimal("50000"), bond.getExcludedMaturity());
        assertEquals(new BigDecimal("25000"), bond.getExcludedPledged());
        assertEquals(new BigDecimal("10"), bond.getHaircut());
        assertEquals(new BigDecimal("202500.00"), bond.getEligibleAmount());
        assertTrue(run.getConfigSnapshot().contains("\"code\":\"LIQUID_ASSETS_TO_DEPOSITS\""));
        assertTrue(run.getConfigSnapshot().contains("\"haircut\":\"10\""));
    }

    @Test
    void theReturnIsBuiltFromTheStoredRatiosAndExportsTheSameEveryTime() {
        final TreasuryRun ratiosRun = run(20L, RunType.RATIOS);
        this.service.computeRatios(ratiosRun, run(10L, RunType.LADDER), 2);
        when(this.ratios.findByRunIdOrderBySequenceNumberAsc(20L)).thenReturn(this.savedRatios);
        when(this.components.findByRunIdOrderByIdAsc(20L)).thenReturn(this.savedComponents);
        final TreasuryRun returnRun = run(30L, RunType.RETURN);
        returnRun.setSourceRunId(20L);
        this.service.buildReturn(returnRun, ratiosRun);

        assertEquals(8, this.savedLines.size());
        when(this.returnLines.findByRunIdOrderBySequenceNumberAsc(30L)).thenReturn(this.savedLines);
        final TreasuryRuleSet ruleSet = new TreasuryRuleSet();
        ruleSet.setCode("BOU_MDI_2004");
        when(this.ruleSetService.find(1L)).thenReturn(ruleSet);
        final TreasuryReturnData data = this.service.returnData(returnRun, ratiosRun);
        final String csv = TreasuryRatioRunService.csv(data, 2);
        assertTrue(csv.contains("\"C\",\"LIQUID_ASSETS_TO_DEPOSITS.C1\",\"Liquid assets to total deposits (%)\",20.50"));
        assertTrue(csv.contains("\"A\",\"LIQUID_ASSETS_TO_DEPOSITS.A2\",\"GOVT_BOND\",202500.00"));
        returnRun.setStatus("SUBMITTED");
        assertEquals(csv, TreasuryRatioRunService.csv(this.service.returnData(returnRun, ratiosRun), 2),
                "submission does not change the file");
    }

    @Test
    void aSubmittedReturnLocksItsScope() {
        final TreasuryLiquidityDataValidator validator = new TreasuryLiquidityDataValidator(new FromJsonHelper());
        final TreasuryActor actor = mock(TreasuryActor.class);
        final TreasuryRunRepository runs = mock(TreasuryRunRepository.class);
        final TreasuryLiquidityRunService runService = new TreasuryLiquidityRunService(validator, actor, this.ruleSetService,
                mock(TreasuryAssumptionSetService.class), runs, mock(TreasuryRunLineRepository.class),
                mock(TreasuryReferenceDataLookup.class), List.of(), mock(NamedParameterJdbcTemplate.class),
                mock(AppUserRepositoryWrapper.class), mock(PlatformTransactionManager.class), this.service);
        final TreasuryRun returnRun = run(30L, RunType.RETURN);
        returnRun.setStatus("COMPLETED");
        returnRun.setCreatedBy(5L);
        when(runs.findById(30L)).thenReturn(Optional.of(returnRun));

        when(actor.userId(any())).thenReturn(5L);
        assertCode("run.same.user", () -> runService.submit(30L, JsonCommand.from("{}")));
        when(actor.userId(any())).thenReturn(6L);
        runService.submit(30L, JsonCommand.from("{}"));
        assertEquals("SUBMITTED", returnRun.getStatus());
        assertEquals(6L, returnRun.getSubmittedBy());
        assertCode("run.not.submittable", () -> runService.submit(30L, JsonCommand.from("{}")));

        when(runs.findByAsOfDateAndCurrencyCode(LocalDate.of(2026, 10, 16), "UGX")).thenReturn(List.of(returnRun));
        assertCode("run.locked", () -> runService.ratios(run(10L, RunType.LADDER), 6L));
        verify(runs, never()).delete(any());
    }

    private static TreasuryRun run(final Long id, final RunType type) {
        final TreasuryRun run = new TreasuryRun();
        run.setId(id);
        run.setRunType(type);
        run.setAsOfDate(LocalDate.of(2026, 10, 16));
        run.setCurrencyCode("UGX");
        run.setRuleSetId(1L);
        run.setRuleSetVersion(1);
        return run;
    }

    private static TreasuryRatioComponent component(final RatioPart part, final int seq, final ComponentType type,
            final String assetClass) {
        final TreasuryRatioComponent component = new TreasuryRatioComponent();
        component.setPart(part);
        component.setSequenceNumber(seq);
        component.setComponentType(type);
        component.setLabel(assetClass == null ? "Deposits" : assetClass);
        component.setAssetClass(assetClass);
        component.setGlCodePrefix(assetClass == null ? "2000" : null);
        return component;
    }

    private static final Map<String, TreasuryAssetClassRule> RULES = new HashMap<>();

    private static TreasuryAssetClassRule rule(final String code, final String haircut) {
        return RULES.computeIfAbsent(code, k -> {
            final TreasuryAssetClassRule rule = new TreasuryAssetClassRule();
            rule.setCode(code);
            rule.setLabel(code);
            rule.setHaircut(new BigDecimal(haircut));
            return rule;
        });
    }

    private static void assertCode(final String suffix, final org.junit.jupiter.api.function.Executable call) {
        assertEquals("error.msg.treasury." + suffix, assertThrows(TreasuryWorkflowException.class, call).getGlobalisationMessageCode());
    }
}
