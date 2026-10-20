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
package org.apache.fineract.scoring.engine;

import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_CURRENT_DAYS_IN_ARREARS;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_HAS_EXPIRED_ID;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_LEGAL_FORM;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_MAX_DAYS_LATE_HISTORY;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_MONTHLY_INCOME;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.CLIENT_MONTHS_ACTIVE;
import static org.apache.fineract.scoring.input.ScoringCharacteristics.SAVINGS_MONTHLY_DEPOSITS_6M;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.scoring.domain.RuleOperator;
import org.apache.fineract.scoring.domain.Scorecard;
import org.apache.fineract.scoring.domain.ScorecardBin;
import org.apache.fineract.scoring.domain.ScorecardGradeBand;
import org.apache.fineract.scoring.domain.ScorecardRule;
import org.apache.fineract.scoring.domain.ScorecardType;
import org.apache.fineract.scoring.domain.ScoringOutcome;
import org.apache.fineract.scoring.domain.ScoringRuleType;
import org.junit.jupiter.api.Test;

class ExpertScorecardEngineTest {

    private final ExpertScorecardEngine engine = new ExpertScorecardEngine();

    /**
     * A small agent scorecard: deposits, tenure and repayment history, five grades, an expired-ID knock-out, an
     * arrears knock-out and a refer rule when income is unknown.
     */
    private static Scorecard agentScorecard() {
        final Scorecard scorecard = Scorecard.createDraft("AGENT", 1, "Agent scorecard", null, ScorecardType.APPLICATION,
                BigDecimal.valueOf(300));
        scorecard.addBin(bin(SAVINGS_MONTHLY_DEPOSITS_6M, null, 500_000, 0, "LOW_DEPOSITS"));
        scorecard.addBin(bin(SAVINGS_MONTHLY_DEPOSITS_6M, 500_000, 2_000_000, 60, "MODERATE_DEPOSITS"));
        scorecard.addBin(bin(SAVINGS_MONTHLY_DEPOSITS_6M, 2_000_000, null, 120, null));
        scorecard.addBin(bin(CLIENT_MONTHS_ACTIVE, null, 6, 0, "SHORT_TENURE"));
        scorecard.addBin(bin(CLIENT_MONTHS_ACTIVE, 6, 24, 40, "MEDIUM_TENURE"));
        scorecard.addBin(bin(CLIENT_MONTHS_ACTIVE, 24, null, 80, null));
        scorecard.addBin(ScorecardBin.create(CLIENT_MAX_DAYS_LATE_HISTORY, null, null, null, true, BigDecimal.valueOf(30), "NO_HISTORY"));
        scorecard.addBin(bin(CLIENT_MAX_DAYS_LATE_HISTORY, null, 1, 100, null));
        scorecard.addBin(bin(CLIENT_MAX_DAYS_LATE_HISTORY, 1, 30, 50, "PAST_LATE_PAYMENT"));
        scorecard.addBin(bin(CLIENT_MAX_DAYS_LATE_HISTORY, 30, null, 0, "SERIOUS_PAST_ARREARS"));

        scorecard.addGradeBand(band("A", 560, 1000, "0.01", ScoringOutcome.APPROVE));
        scorecard.addGradeBand(band("B", 480, 559, "0.03", ScoringOutcome.APPROVE));
        scorecard.addGradeBand(band("C", 420, 479, "0.07", ScoringOutcome.REFER));
        scorecard.addGradeBand(band("D", 360, 419, "0.15", ScoringOutcome.REFER));
        scorecard.addGradeBand(band("E", 300, 359, "0.30", ScoringOutcome.DECLINE));

        scorecard.addRule(ScorecardRule.create(ScoringRuleType.EXPIRED_ID, null, null, null, ScoringOutcome.DECLINE, "ID_EXPIRED"));
        scorecard.addRule(ScorecardRule.create(ScoringRuleType.CHARACTERISTIC, CLIENT_CURRENT_DAYS_IN_ARREARS, RuleOperator.GT, "30",
                ScoringOutcome.DECLINE, "IN_ARREARS"));
        scorecard.addRule(ScorecardRule.create(ScoringRuleType.CHARACTERISTIC, CLIENT_MONTHLY_INCOME, RuleOperator.MISSING, null,
                ScoringOutcome.REFER, "INCOME_UNKNOWN"));
        return scorecard;
    }

    private static Map<String, Object> strongAgent() {
        final Map<String, Object> inputs = new HashMap<>();
        inputs.put(SAVINGS_MONTHLY_DEPOSITS_6M, new BigDecimal("3500000"));
        inputs.put(CLIENT_MONTHS_ACTIVE, BigDecimal.valueOf(36));
        inputs.put(CLIENT_MAX_DAYS_LATE_HISTORY, BigDecimal.ZERO);
        inputs.put(CLIENT_HAS_EXPIRED_ID, false);
        inputs.put(CLIENT_CURRENT_DAYS_IN_ARREARS, BigDecimal.ZERO);
        inputs.put(CLIENT_MONTHLY_INCOME, new BigDecimal("1200000"));
        return inputs;
    }

    @Test
    void strongAgentGetsGradeAWithNoReasons() {
        final ScoringResult result = engine.evaluate(agentScorecard(), strongAgent());

        assertEquals(0, new BigDecimal("600").compareTo(result.score()));
        assertEquals("A", result.grade());
        assertEquals(new BigDecimal("0.01"), result.expectedPd());
        assertEquals(ScoringOutcome.APPROVE, result.outcome());
        assertTrue(result.reasonCodes().isEmpty());
    }

    @Test
    void weakAgentListsTheCostliestCharacteristicsFirst() {
        final Map<String, Object> inputs = strongAgent();
        inputs.put(SAVINGS_MONTHLY_DEPOSITS_6M, new BigDecimal("300000"));
        inputs.put(CLIENT_MONTHS_ACTIVE, BigDecimal.valueOf(10));
        inputs.put(CLIENT_MAX_DAYS_LATE_HISTORY, BigDecimal.valueOf(12));

        final ScoringResult result = engine.evaluate(agentScorecard(), inputs);

        // 300 + 0 + 40 + 50 = 390
        assertEquals(0, new BigDecimal("390").compareTo(result.score()));
        assertEquals("D", result.grade());
        assertEquals(ScoringOutcome.REFER, result.outcome());
        assertEquals(List.of("LOW_DEPOSITS", "PAST_LATE_PAYMENT", "MEDIUM_TENURE"), result.reasonCodes());
    }

    @Test
    void expiredIdDeclinesWhateverTheScore() {
        final Map<String, Object> inputs = strongAgent();
        inputs.put(CLIENT_HAS_EXPIRED_ID, true);

        final ScoringResult result = engine.evaluate(agentScorecard(), inputs);

        assertEquals("A", result.grade());
        assertEquals(ScoringOutcome.DECLINE, result.outcome());
        assertEquals(List.of("ID_EXPIRED"), result.reasonCodes());
        assertTrue(result.ruleResults().get(0).fired());
    }

    @Test
    void arrearsAboveThresholdDeclines() {
        final Map<String, Object> inputs = strongAgent();
        inputs.put(CLIENT_CURRENT_DAYS_IN_ARREARS, BigDecimal.valueOf(31));

        assertEquals(ScoringOutcome.DECLINE, engine.evaluate(agentScorecard(), inputs).outcome());

        inputs.put(CLIENT_CURRENT_DAYS_IN_ARREARS, BigDecimal.valueOf(30));
        assertEquals(ScoringOutcome.APPROVE, engine.evaluate(agentScorecard(), inputs).outcome());
    }

    @Test
    void missingIncomeRefersAndMissingHistoryUsesTheMissingBin() {
        final Map<String, Object> inputs = strongAgent();
        inputs.remove(CLIENT_MONTHLY_INCOME);
        inputs.put(CLIENT_MAX_DAYS_LATE_HISTORY, null);

        final ScoringResult result = engine.evaluate(agentScorecard(), inputs);

        // 300 + 120 + 80 + 30 (missing bin) = 530
        assertEquals(0, new BigDecimal("530").compareTo(result.score()));
        assertEquals("B", result.grade());
        assertEquals(ScoringOutcome.REFER, result.outcome());
        assertEquals(List.of("INCOME_UNKNOWN", "NO_HISTORY"), result.reasonCodes());
    }

    @Test
    void scoreOutsideEveryBandIsReferred() {
        final Scorecard scorecard = agentScorecard();
        scorecard.setBasePoints(BigDecimal.valueOf(-100));

        final ScoringResult result = engine.evaluate(scorecard, strongAgent());

        assertNull(result.grade());
        assertEquals(ScoringOutcome.REFER, result.outcome());
        assertEquals(ExpertScorecardEngine.REASON_SCORE_OUTSIDE_BANDS, result.reasonCodes().get(0));
    }

    @Test
    void textBinsMatchIgnoringCase() {
        final Scorecard scorecard = Scorecard.createDraft("SME", 1, "SME", null, ScorecardType.APPLICATION, BigDecimal.ZERO);
        scorecard.addBin(ScorecardBin.create(CLIENT_LEGAL_FORM, null, null, "person", false, BigDecimal.TEN, null));
        scorecard.addBin(ScorecardBin.create(CLIENT_LEGAL_FORM, null, null, "ENTITY", false, BigDecimal.ONE, "ENTITY_BORROWER"));
        scorecard.addGradeBand(band("A", 0, 100, null, ScoringOutcome.APPROVE));

        final ScoringResult result = engine.evaluate(scorecard, Map.of(CLIENT_LEGAL_FORM, "PERSON"));

        assertEquals(0, BigDecimal.TEN.compareTo(result.score()));
    }

    @Test
    void numericRuleAcceptsTextValuesFromDatatables() {
        final ScorecardRule rule = ScorecardRule.create(ScoringRuleType.CHARACTERISTIC, "DT:agent_profile.outlets", RuleOperator.GTE, "2",
                ScoringOutcome.REFER, "MANY_OUTLETS");

        assertTrue(ExpertScorecardEngine.fires(rule, "3"));
        assertTrue(!ExpertScorecardEngine.fires(rule, "1"));
        assertTrue(!ExpertScorecardEngine.fires(rule, null));
    }

    private static ScorecardBin bin(final String characteristic, final Integer lower, final Integer upper, final int points,
            final String reasonCode) {
        return ScorecardBin.create(characteristic, lower == null ? null : BigDecimal.valueOf(lower),
                upper == null ? null : BigDecimal.valueOf(upper), null, false, BigDecimal.valueOf(points), reasonCode);
    }

    private static ScorecardGradeBand band(final String grade, final int min, final int max, final String pd, final ScoringOutcome outcome) {
        return ScorecardGradeBand.create(grade, BigDecimal.valueOf(min), BigDecimal.valueOf(max), pd == null ? null : new BigDecimal(pd),
                outcome);
    }
}
