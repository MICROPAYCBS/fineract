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

import static org.apache.fineract.treasury.limits.LimitFixtures.AS_OF;
import static org.apache.fineract.treasury.limits.LimitFixtures.limit;
import static org.apache.fineract.treasury.limits.LimitFixtures.own;
import static org.apache.fineract.treasury.limits.LimitFixtures.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.treasury.deal.LimitCheck;
import org.apache.fineract.treasury.domain.TreasuryBreach;
import org.apache.fineract.treasury.domain.TreasuryBreachComment;
import org.apache.fineract.treasury.domain.TreasuryBreachCommentRepository;
import org.apache.fineract.treasury.domain.TreasuryBreachRepository;
import org.apache.fineract.treasury.domain.TreasuryCounterparty;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.domain.TreasuryLimitEvaluation;
import org.apache.fineract.treasury.domain.TreasuryLimitEvaluationLine;
import org.apache.fineract.treasury.domain.TreasuryLimitEvaluationLineRepository;
import org.apache.fineract.treasury.domain.TreasuryLimitEvaluationRepository;
import org.apache.fineract.treasury.domain.TreasuryLimitRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRepository;
import org.apache.fineract.treasury.limits.BreachAction;
import org.apache.fineract.treasury.limits.BreachStatus;
import org.apache.fineract.treasury.limits.EvaluationContext;
import org.apache.fineract.treasury.limits.LimitComparison;
import org.apache.fineract.treasury.limits.LimitEvaluator;
import org.apache.fineract.treasury.limits.LimitLevel;
import org.apache.fineract.treasury.limits.LimitMetric;
import org.apache.fineract.treasury.limits.LimitScopeType;
import org.apache.fineract.treasury.limits.LimitStatus;
import org.apache.fineract.treasury.limits.LimitUnit;
import org.apache.fineract.treasury.limits.Observation;
import org.apache.fineract.treasury.liquidity.RunType;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The breach log over a sequence of observations of the advances to deposits ratio (maximum 80%, warning 75%): new,
 * seen again, escalated in place, cleared without being resolved, then crossing again.
 */
class TreasuryLimitEngineTest {

    private final TreasuryLimitRepository limits = mock(TreasuryLimitRepository.class);
    private final TreasuryBreachRepository breaches = mock(TreasuryBreachRepository.class);
    private final TreasuryBreachCommentRepository comments = mock(TreasuryBreachCommentRepository.class);
    private final TreasuryLimitEvaluationRepository evaluations = mock(TreasuryLimitEvaluationRepository.class);
    private final TreasuryLimitEvaluationLineRepository lines = mock(TreasuryLimitEvaluationLineRepository.class);
    private final TreasuryReferenceDataLookup lookup = mock(TreasuryReferenceDataLookup.class);
    private final TreasuryBreachNotifier notifier = mock(TreasuryBreachNotifier.class);
    private final LimitEvaluator ratioEvaluator = mock(LimitEvaluator.class);
    private final TreasuryCfpService cfp = mock(TreasuryCfpService.class);
    private final LimitEvaluator exposureEvaluator = mock(LimitEvaluator.class);
    private final List<TreasuryBreach> stored = new ArrayList<>();
    private final List<TreasuryBreachComment> savedComments = new ArrayList<>();
    private final List<TreasuryLimitEvaluationLine> savedLines = new ArrayList<>();
    private final AtomicReference<BigDecimal> value = new AtomicReference<>();
    private TreasuryLimitEngine engine;
    private TreasuryLimit advances;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", mock(FineractPlatformTenantConnection.class)));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, AS_OF)));
        when(this.ratioEvaluator.metric()).thenReturn(LimitMetric.RATIO);
        when(this.exposureEvaluator.metric()).thenReturn(LimitMetric.COUNTERPARTY_EXPOSURE);
        this.engine = new TreasuryLimitEngine(List.of(this.ratioEvaluator, this.exposureEvaluator), this.limits, this.breaches,
                this.comments, this.evaluations, this.lines, mock(TreasuryRunRepository.class), this.lookup, this.notifier,
                mock(AppUserRepositoryWrapper.class), mock(PlatformTransactionManager.class), this.cfp);
        this.advances = limit(4L, LimitMetric.RATIO, LimitComparison.MAX, "75", "80", LimitUnit.PERCENT);
        this.advances.setMetricKey("ADVANCES_TO_DEPOSITS");
        this.advances.setNotifyRoleId(9L);
        lenient().when(this.limits.findByStatusOrderByCodeAsc(LimitStatus.ACTIVE)).thenReturn(List.of(this.advances));
        lenient().when(this.lookup.decimalPlaces(anyString())).thenReturn(2);
        lenient().when(this.ratioEvaluator.observe(any(), any()))
                .thenAnswer(call -> List.of(Observation.of(LimitScopeType.INSTITUTION, null, "Institution, UGX", this.value.get(),
                        LimitComparison.MAX, new BigDecimal("75"), new BigDecimal("80"), "RUN", 13L, "detail")));
        lenient().when(this.breaches.findByLimitIdAndStatusIn(any(), any())).thenAnswer(call -> {
            final Collection<BreachStatus> statuses = call.getArgument(1);
            return this.stored.stream().filter(b -> b.getLimitId().equals(call.getArgument(0)) && statuses.contains(b.getStatus()))
                    .toList();
        });
        lenient().when(this.breaches.saveAndFlush(any())).thenAnswer(call -> {
            final TreasuryBreach breach = call.getArgument(0);
            if (breach.getId() == null) {
                breach.setId(100L + this.stored.size());
                this.stored.add(breach);
            }
            return breach;
        });
        lenient().when(this.comments.save(any())).thenAnswer(call -> {
            this.savedComments.add(call.getArgument(0));
            return call.getArgument(0);
        });
        lenient().when(this.evaluations.saveAndFlush(any())).thenAnswer(call -> {
            final TreasuryLimitEvaluation evaluation = call.getArgument(0);
            if (evaluation.getId() == null) {
                evaluation.setId(500L);
            }
            return evaluation;
        });
        lenient().when(this.lines.save(any())).thenAnswer(call -> {
            this.savedLines.add(call.getArgument(0));
            return call.getArgument(0);
        });
        lenient().when(this.notifier.notify(any(), any(), any(), any())).thenReturn(2);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void newThenSeenAgainThenEscalatedInPlace() {
        TreasuryLimitEngine.Summary summary = observe("76");
        assertEquals(1, summary.created());
        assertEquals(2, summary.notified());
        final TreasuryBreach breach = this.stored.getFirst();
        assertEquals(LimitLevel.WARNING, breach.getLevel());
        assertEquals(BreachStatus.OPEN, breach.getStatus());
        assertEquals(1, breach.getOccurrences());
        assertEquals(0, new BigDecimal("80").compareTo(breach.getHardLevel()), "levels kept on the breach");
        assertEquals(1, breach.getLimitVersion());
        assertEquals("ADVANCES_TO_DEPOSITS", breach.getMetricKey());
        assertEquals(13L, breach.getSourceId());

        summary = observe("77");
        assertEquals(1, summary.repeated());
        assertEquals(1, this.stored.size(), "the same open breach");
        assertEquals(2, breach.getOccurrences());
        assertEquals(0, new BigDecimal("77").compareTo(breach.getObservedValue()));

        breach.setStatus(BreachStatus.ACKNOWLEDGED);
        this.advances.setLimitVersion(2);
        summary = observe("692.07");
        assertEquals(1, summary.escalated());
        assertEquals(1, this.stored.size(), "upgraded, not a new row");
        assertEquals(LimitLevel.HARD, breach.getLevel());
        assertEquals(AS_OF, breach.getEscalatedOn());
        assertEquals(BreachStatus.OPEN, breach.getStatus(), "an escalation reopens an acknowledged breach");
        assertEquals(3, breach.getOccurrences());
        assertEquals(2, breach.getLimitVersion(), "the levels as they were at the escalation");
        assertTrue(this.savedComments.getFirst().getComment().startsWith("Escalated from WARNING to HARD"));
        verify(this.notifier, times(2)).notify(any(), any(), any(), any());
        verify(this.notifier).notify(eq(this.advances), eq(breach), eq(BreachAction.ESCALATED), eq(7L));
        verify(this.cfp, times(2)).breachRaised(any(), any(), any());

        observe("85");
        assertEquals(LimitLevel.HARD, breach.getLevel());
        assertEquals(LimitLevel.HARD, breach.getLastLevel());
        assertEquals(4, breach.getOccurrences());
        verify(this.notifier, times(2)).notify(any(), any(), any(), any());
    }

    @Test
    void backWithinTheLevelsClearsButDoesNotResolve() {
        observe("81");
        final TreasuryBreach breach = this.stored.getFirst();
        TreasuryLimitEngine.Summary summary = observe("70");
        assertEquals(1, summary.cleared());
        assertEquals(BreachStatus.OPEN, breach.getStatus());
        assertEquals(AS_OF, breach.getClearedOn());
        assertNull(breach.getLastLevel());
        assertEquals(1, breach.getOccurrences());
        summary = observe("70");
        assertEquals(1, summary.cleared());
        assertEquals(1, this.savedComments.size(), "cleared once");
        summary = observe("90");
        assertEquals(1, summary.repeated(), "at HARD again: the same breach");
        assertNull(breach.getClearedOn());
        assertEquals(2, breach.getOccurrences());
        assertTrue(this.savedComments.get(1).getComment().startsWith("At the HARD level again"));
    }

    @Test
    void aResolvedBreachIsNotReusedAndNothingOpenIsNone() {
        observe("81");
        this.stored.getFirst().setStatus(BreachStatus.RESOLVED);
        final TreasuryLimitEngine.Summary summary = observe("82");
        assertEquals(1, summary.created());
        assertEquals(2, this.stored.size());
        observe(null);
        assertEquals(BreachAction.NONE, this.savedLines.getLast().getAction(), "nothing observed leaves the breach as it is");
        this.stored.get(1).setStatus(BreachStatus.RESOLVED);
        assertEquals(0, observe("70").created());
        assertEquals(BreachAction.NONE, this.savedLines.getLast().getAction());
    }

    @Test
    void evaluationsAreStoredWithEveryObservation() {
        final TreasuryLimitEngine.Summary summary = observe("76");
        assertEquals(500L, summary.evaluationId());
        assertEquals(1, summary.limits());
        final TreasuryLimitEvaluationLine line = this.savedLines.getFirst();
        assertEquals(500L, line.getEvaluationId());
        assertEquals("L4", line.getLimitCode());
        assertEquals(LimitLevel.WARNING, line.getLevel());
        assertEquals(BreachAction.CREATED, line.getAction());
        assertEquals(this.stored.getFirst().getId(), line.getBreachId());
        assertEquals(List.of(this.stored.getFirst().getId()), summary.breachIds());
    }

    @Test
    void whichLimitsApply() {
        final TreasuryLimit office = limit(5L, LimitMetric.RATIO, LimitComparison.MIN, null, "15", LimitUnit.PERCENT);
        office.setScopeType(LimitScopeType.OFFICE);
        office.setScopeId(1L);
        final TreasuryLimit dollars = limit(6L, LimitMetric.RATIO, LimitComparison.MIN, null, "15", LimitUnit.PERCENT);
        dollars.setCurrencyCode("USD");
        final TreasuryLimit later = limit(7L, LimitMetric.RATIO, LimitComparison.MIN, null, "15", LimitUnit.PERCENT);
        later.setEffectiveFrom(AS_OF.plusDays(1));
        final TreasuryLimit ended = limit(8L, LimitMetric.RATIO, LimitComparison.MIN, null, "15", LimitUnit.PERCENT);
        ended.setEffectiveTo(AS_OF.minusDays(1));
        final TreasuryLimit gap = limit(9L, LimitMetric.BUCKET_GAP, LimitComparison.MIN, null, "0", LimitUnit.AMOUNT);
        when(this.limits.findByStatusOrderByCodeAsc(LimitStatus.ACTIVE))
                .thenReturn(List.of(this.advances, office, dollars, later, ended, gap));
        final EvaluationContext institution = new EvaluationContext(AS_OF, "UGX", null, null, null, 2);
        assertEquals(List.of(this.advances), this.engine.applicable(institution, java.util.EnumSet.of(LimitMetric.RATIO)));
        assertEquals(List.of(this.advances, gap), this.engine.applicable(institution, null));
        assertEquals(List.of(office), this.engine.applicable(new EvaluationContext(AS_OF, "UGX", 1L, null, null, 2), null));
        assertTrue(this.engine.applicable(new EvaluationContext(AS_OF, "UGX", 2L, null, null, 2), null).isEmpty());
    }

    @Test
    void runsEvaluateTheirOwnMetricsAndNothingIsStoredWithoutALimit() {
        when(this.limits.findByStatusOrderByCodeAsc(LimitStatus.ACTIVE)).thenReturn(List.of());
        assertNull(this.engine.afterRun(run(13L, RunType.RATIOS, null), run(12L, RunType.LADDER, null), 7L).evaluationId());
        verify(this.evaluations, never()).saveAndFlush(any());
        assertNull(this.engine.afterRun(run(14L, RunType.RETURN, null), null, 7L).evaluationId());
    }

    @Test
    void aDealCaptureCheckWritesTheCounterpartysBreach() {
        final TreasuryLimit counterpartyLimits = own(11L, LimitMetric.COUNTERPARTY_EXPOSURE);
        when(this.limits.findByStatusOrderByCodeAsc(LimitStatus.ACTIVE)).thenReturn(List.of(counterpartyLimits, this.advances));
        final TreasuryCounterparty bank = new TreasuryCounterparty();
        bank.setId(5L);
        bank.setShortCode("B5");
        bank.setName("Bank 5");
        bank.setExposureLimit(new BigDecimal("1000000"));
        bank.setLimitCurrencyCode("UGX");
        bank.setWarningPercent(BigDecimal.valueOf(80));
        final TreasuryDeal deal = new TreasuryDeal();
        deal.setCurrencyCode("UGX");
        deal.setExposureAmount(new BigDecimal("600000"));
        deal.setLimitExceeded(true);
        final LimitCheck.Result result = LimitCheck.evaluate(bank.getExposureLimit(), bank.getWarningPercent(), new BigDecimal("500000"),
                deal.getExposureAmount());
        final TreasuryLimitEngine.Summary summary = this.engine.dealRefused(bank, deal, result, 7L);
        assertEquals(1, summary.limits(), "only the counterparty limits");
        assertEquals(1, summary.created());
        final TreasuryBreach breach = this.stored.getFirst();
        assertEquals(LimitMetric.COUNTERPARTY_EXPOSURE, breach.getMetric());
        assertEquals(LimitScopeType.COUNTERPARTY, breach.getScopeType());
        assertEquals(5L, breach.getScopeId());
        assertEquals(LimitLevel.HARD, breach.getLevel());
        assertEquals("DEAL_REQUEST", breach.getSourceType());
        assertTrue(breach.getDetail().startsWith("Refused: a deal being captured of 600000 UGX takes the exposure to 1100000"),
                breach.getDetail());
        assertEquals(new BigDecimal("110.00"), breach.getObservedValue());
        deal.setId(70L);
        this.engine.dealChecked(bank, deal, result, 7L);
        assertEquals(2, breach.getOccurrences(), "the same counterparty's breach");
        assertEquals("DEAL", breach.getSourceType());
        assertEquals(70L, breach.getSourceId());
    }

    private TreasuryLimitEngine.Summary observe(final String observed) {
        this.value.set(observed == null ? null : new BigDecimal(observed));
        final EvaluationContext context = new EvaluationContext(AS_OF, "UGX", null, null, run(13L, RunType.RATIOS, null), 2);
        return this.engine.evaluate(context, null, TreasuryLimitEngine.TRIGGER_MANUAL, null, 7L, false);
    }
}
