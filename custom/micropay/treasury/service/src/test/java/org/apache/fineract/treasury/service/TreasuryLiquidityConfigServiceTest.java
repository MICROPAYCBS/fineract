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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
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
import org.apache.fineract.treasury.domain.TreasuryAssetClassRuleRepository;
import org.apache.fineract.treasury.domain.TreasuryAssumptionRepository;
import org.apache.fineract.treasury.domain.TreasuryAssumptionSet;
import org.apache.fineract.treasury.domain.TreasuryAssumptionSetRepository;
import org.apache.fineract.treasury.domain.TreasuryBucket;
import org.apache.fineract.treasury.domain.TreasuryBucketRepository;
import org.apache.fineract.treasury.domain.TreasuryRatioComponentRepository;
import org.apache.fineract.treasury.domain.TreasuryRatioDefinitionRepository;
import org.apache.fineract.treasury.domain.TreasuryRuleSet;
import org.apache.fineract.treasury.domain.TreasuryRuleSetRepository;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunLine;
import org.apache.fineract.treasury.domain.TreasuryRunLineRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.liquidity.ConfigStatus;
import org.apache.fineract.treasury.liquidity.LadderFlow;
import org.apache.fineract.treasury.liquidity.LadderSource;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.liquidity.RunType;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Versioning and activation of rule sets and assumption sets, and a run replacing the earlier one. */
class TreasuryLiquidityConfigServiceTest {

    private static final long MAKER = 5L;
    private static final long CHECKER = 6L;

    private final TreasuryActor actor = mock(TreasuryActor.class);
    private final TreasuryRuleSetRepository ruleSets = mock(TreasuryRuleSetRepository.class);
    private final TreasuryBucketRepository buckets = mock(TreasuryBucketRepository.class);
    private final TreasuryAssumptionSetRepository assumptionSets = mock(TreasuryAssumptionSetRepository.class);
    private final TreasuryRunRepository runs = mock(TreasuryRunRepository.class);
    private final TreasuryRunLineRepository runLines = mock(TreasuryRunLineRepository.class);
    private final TreasuryReferenceDataLookup lookup = mock(TreasuryReferenceDataLookup.class);
    private final TreasuryLiquidityDataValidator validator = new TreasuryLiquidityDataValidator(new FromJsonHelper());
    private final TreasuryRuleSetService ruleSetService = new TreasuryRuleSetService(this.validator, this.actor, this.ruleSets,
            this.buckets, mock(TreasuryRatioDefinitionRepository.class), mock(TreasuryRatioComponentRepository.class),
            mock(TreasuryAssetClassRuleRepository.class), mock(ObjectProvider.class));
    private final TreasuryAssumptionSetService assumptionSetService = new TreasuryAssumptionSetService(this.validator, this.actor,
            this.assumptionSets, mock(TreasuryAssumptionRepository.class));
    private final List<TreasuryRuleSet> savedSets = new ArrayList<>();
    private final List<TreasuryBucket> savedBuckets = new ArrayList<>();
    private final List<TreasuryRunLine> savedLines = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", mock(FineractPlatformTenantConnection.class)));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 10, 16))));
        lenient().when(this.actor.userId(any())).thenReturn(MAKER);
        lenient().when(this.ruleSets.saveAndFlush(any())).thenAnswer(call -> {
            final TreasuryRuleSet set = call.getArgument(0);
            if (set.getId() == null) {
                set.setId((long) this.savedSets.size() + 1);
                this.savedSets.add(set);
            }
            return set;
        });
        lenient().when(this.ruleSets.findById(any()))
                .thenAnswer(call -> this.savedSets.stream().filter(s -> s.getId().equals(call.getArgument(0))).findFirst());
        lenient().when(this.ruleSets.findByCodeOrderByVersionAsc(anyString()))
                .thenAnswer(call -> this.savedSets.stream().filter(s -> s.getCode().equals(call.getArgument(0))).toList());
        lenient().when(this.ruleSets.findByCodeAndStatus(anyString(), any())).thenAnswer(call -> this.savedSets.stream()
                .filter(s -> s.getCode().equals(call.getArgument(0)) && s.getStatus() == call.getArgument(1)).toList());
        lenient().when(this.ruleSets.findByDefaultSetTrue())
                .thenAnswer(call -> this.savedSets.stream().filter(TreasuryRuleSet::isDefaultSet).toList());
        lenient().when(this.buckets.save(any())).thenAnswer(call -> {
            this.savedBuckets.add(call.getArgument(0));
            return call.getArgument(0);
        });
        lenient().when(this.buckets.findByRuleSetIdOrderBySequenceNumberAsc(any()))
                .thenAnswer(call -> this.savedBuckets.stream().filter(b -> b.getRuleSetId().equals(call.getArgument(0))).toList());
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void aNewVersionIsActivatedBySomeoneElseAndSupersedesTheDefault() {
        final TreasuryRuleSet v1 = ruleSet("BOU_MDI_2004", 1, ConfigStatus.ACTIVE);
        v1.setDefaultSet(true);

        final Long v2 = this.ruleSetService.create(JsonCommand.from(
                "{\"code\": \"BOU_MDI_2004\", \"name\": \"MDI\", \"effectiveDate\": \"2026-10-01\", \"buckets\": [{\"code\": \"ON\", \"label\": \"O/N\", \"fromDay\": 0, \"toDay\": 1}, {\"code\": \"REST\", \"label\": \"Rest\", \"fromDay\": 2}]}"))
                .getResourceId();
        assertEquals(2, find(v2).getVersion());
        assertEquals(ConfigStatus.DRAFT, find(v2).getStatus());
        assertCode("rule.set.draft.exists", () -> this.ruleSetService.create(JsonCommand.from(
                "{\"code\": \"BOU_MDI_2004\", \"name\": \"MDI\", \"effectiveDate\": \"2026-10-01\", \"buckets\": [{\"code\": \"ON\", \"label\": \"O/N\", \"fromDay\": 0}]}")));

        assertCode("rule.set.same.user", () -> this.ruleSetService.activate(v2, JsonCommand.from("{}")));
        when(this.actor.userId(any())).thenReturn(CHECKER);
        this.ruleSetService.activate(v2, JsonCommand.from("{}"));

        assertEquals(ConfigStatus.SUPERSEDED, v1.getStatus());
        assertFalse(v1.isDefaultSet());
        assertEquals(ConfigStatus.ACTIVE, find(v2).getStatus());
        assertTrue(find(v2).isDefaultSet(), "the default moves to the new version");
        assertEquals(CHECKER, find(v2).getActivatedBy());
    }

    @Test
    void activeAndSupersededVersionsCannotChange() {
        final TreasuryRuleSet active = ruleSet("BOU_FI_2023", 1, ConfigStatus.ACTIVE);
        final TreasuryRuleSet old = ruleSet("BOU_FI_2023", 0, ConfigStatus.SUPERSEDED);

        assertCode("rule.set.not.draft", () -> this.ruleSetService.update(active.getId(), JsonCommand.from("{\"name\": \"New\"}")));
        assertCode("rule.set.not.draft", () -> this.ruleSetService.delete(old.getId(), JsonCommand.from("{}")));
        assertCode("rule.set.not.draft", () -> this.ruleSetService.activate(old.getId(), JsonCommand.from("{}")));
        assertCode("rule.set.not.draft", () -> this.ruleSetService.update(old.getId(), JsonCommand.from("{\"isDefault\": true}")));
        this.ruleSetService.update(active.getId(), JsonCommand.from("{\"isDefault\": true}"));
        assertTrue(active.isDefaultSet(), "an active version may be made the default");
    }

    @Test
    void aDraftIsDeletedAfterItsBucketsAreFlushed() {
        final TreasuryRuleSet draft = ruleSet("BOU_MDI_2004", 2, ConfigStatus.DRAFT);

        this.ruleSetService.delete(draft.getId(), JsonCommand.from("{}"));

        final InOrder order = inOrder(this.buckets, this.ruleSets);
        order.verify(this.buckets).deleteByRuleSetId(draft.getId());
        order.verify(this.buckets).flush();
        order.verify(this.ruleSets).delete(draft);
    }

    @Test
    void anAssumptionSetIsImmutableOnceActiveOrSuperseded() {
        final TreasuryAssumptionSet active = new TreasuryAssumptionSet();
        active.setId(1L);
        active.setCode("DEFAULT");
        active.setVersion(1);
        active.setStatus(ConfigStatus.ACTIVE);
        when(this.assumptionSets.findById(1L)).thenReturn(Optional.of(active));
        assertCode("assumption.set.not.draft", () -> this.assumptionSetService.update(1L, JsonCommand.from("{\"name\": \"X\"}")));
        active.setStatus(ConfigStatus.SUPERSEDED);
        assertCode("assumption.set.not.draft", () -> this.assumptionSetService.activate(1L, JsonCommand.from("{}")));
        assertCode("assumption.set.not.draft", () -> this.assumptionSetService.delete(1L, JsonCommand.from("{}")));

        final TreasuryAssumptionSet draft = new TreasuryAssumptionSet();
        draft.setId(2L);
        draft.setCode("DEFAULT");
        draft.setVersion(2);
        draft.setStatus(ConfigStatus.DRAFT);
        draft.setCreatedBy(MAKER);
        draft.setLastEditedBy(MAKER);
        draft.setEffectiveDate(LocalDate.of(2026, 10, 1));
        when(this.assumptionSets.findById(2L)).thenReturn(Optional.of(draft));
        final TreasuryAssumptionSet current = new TreasuryAssumptionSet();
        current.setVersion(1);
        current.setStatus(ConfigStatus.ACTIVE);
        when(this.assumptionSets.findByCodeAndStatus("DEFAULT", ConfigStatus.ACTIVE)).thenReturn(List.of(current));
        when(this.actor.userId(any())).thenReturn(CHECKER);
        final Map<String, Object> changes = this.assumptionSetService.activate(2L, JsonCommand.from("{}")).getChanges();
        assertEquals(1, changes.get("supersededVersion"));
        assertEquals(ConfigStatus.SUPERSEDED, current.getStatus());
        assertEquals(ConfigStatus.ACTIVE, draft.getStatus());
    }

    @Test
    void aRerunReplacesTheEarlierRunOfTheSameScope() {
        final TreasuryRuleSet ruleSet = ruleSet("BOU_MDI_2004", 1, ConfigStatus.ACTIVE);
        bucket(ruleSet, 1, "ON", 0, 1);
        bucket(ruleSet, 2, "REST", 2, null);
        final LadderSource opening = source(LadderSourceCode.OPENING,
                LadderFlow.contractual(LadderSourceCode.OPENING, "BANK", 1L, "BANK-1", null, new BigDecimal("1000")));
        final LadderSource items = source(LadderSourceCode.FIXED, LadderFlow.contractual(LadderSourceCode.FIXED, "OUTFLOW", 2L, "Salaries",
                LocalDate.of(2026, 10, 25), new BigDecimal("-400")));
        final TreasuryLiquidityRunService service = new TreasuryLiquidityRunService(this.validator, this.actor, this.ruleSetService,
                this.assumptionSetService, this.runs, this.runLines, this.lookup, List.of(opening, items),
                mock(NamedParameterJdbcTemplate.class), mock(AppUserRepositoryWrapper.class), mock(PlatformTransactionManager.class),
                mock(TreasuryRatioRunService.class), mock(TreasuryLimitEngine.class), mock(TreasuryStressRunService.class),
                mock(TreasuryScenarioService.class), mock(TreasuryCfpService.class), mock(TreasuryRepricingRunService.class));
        final TreasuryRun earlier = new TreasuryRun();
        earlier.setId(70L);
        earlier.setRunType(RunType.LADDER);
        final TreasuryRun otherOffice = new TreasuryRun();
        otherOffice.setId(71L);
        otherOffice.setRunType(RunType.LADDER);
        otherOffice.setOfficeId(2L);
        when(this.runs.findByRunTypeAndAsOfDateAndCurrencyCode(RunType.LADDER, LocalDate.of(2026, 10, 16), "UGX"))
                .thenReturn(List.of(earlier, otherOffice));
        when(this.runLines.save(any())).thenAnswer(call -> {
            this.savedLines.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(this.lookup.decimalPlaces("UGX")).thenReturn(2);

        final TreasuryRun run = service.ladder(LocalDate.of(2026, 10, 16), "UGX", null, ruleSet, Optional.empty(), MAKER);

        verify(this.runLines).deleteByRunId(70L);
        verify(this.runs).delete(earlier);
        verify(this.runLines, never()).deleteByRunId(71L);
        assertEquals("No active assumption set: contractual flows only", run.getNotes());
        final List<TreasuryRunLine> totals = this.savedLines.stream().filter(l -> l.getSource() == LadderSourceCode.TOTAL).toList();
        assertEquals(List.of(new BigDecimal("1000"), new BigDecimal("600")),
                totals.stream().map(TreasuryRunLine::getCumulativeGap).toList());
        assertEquals(new BigDecimal("400"),
                this.savedLines.stream().filter(l -> l.getSource() == LadderSourceCode.FIXED).findFirst().orElseThrow().getOutflow());
    }

    private TreasuryRuleSet ruleSet(final String code, final int version, final ConfigStatus status) {
        final TreasuryRuleSet set = new TreasuryRuleSet();
        set.setCode(code);
        set.setVersion(version);
        set.setStatus(status);
        set.setEffectiveDate(LocalDate.of(2004, 1, 1));
        this.ruleSets.saveAndFlush(set);
        return set;
    }

    private void bucket(final TreasuryRuleSet set, final int seq, final String code, final int from, final Integer to) {
        final TreasuryBucket bucket = new TreasuryBucket();
        bucket.setRuleSetId(set.getId());
        bucket.setSequenceNumber(seq);
        bucket.setCode(code);
        bucket.setLabel(code);
        bucket.setFromDay(from);
        bucket.setToDay(to);
        this.savedBuckets.add(bucket);
    }

    private TreasuryRuleSet find(final Long id) {
        return this.savedSets.stream().filter(s -> s.getId().equals(id)).findFirst().orElseThrow();
    }

    private static LadderSource source(final LadderSourceCode code, final LadderFlow flow) {
        return new LadderSource() {

            @Override
            public LadderSourceCode code() {
                return code;
            }

            @Override
            public List<LadderFlow> flows(final org.apache.fineract.treasury.liquidity.LadderContext context) {
                return List.of(flow);
            }
        };
    }

    private static void assertCode(final String suffix, final org.junit.jupiter.api.function.Executable call) {
        assertEquals("error.msg.treasury." + suffix, assertThrows(TreasuryWorkflowException.class, call).getGlobalisationMessageCode());
    }
}
