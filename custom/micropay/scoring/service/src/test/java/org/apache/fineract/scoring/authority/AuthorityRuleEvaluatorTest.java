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
package org.apache.fineract.scoring.authority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.apache.fineract.scoring.domain.ScoringAuthorityRule;
import org.apache.fineract.scoring.domain.ScoringOutcome;
import org.junit.jupiter.api.Test;

/**
 * The example matrix from the design: credit officer always; branch manager always; head of credit above UGX 5m or on
 * REFER; credit committee above UGX 50m, on grade D or E, or when the decision was overridden.
 */
class AuthorityRuleEvaluatorTest {

    private static final List<String> STAGES = List.of("CREDIT_OFFICER", "BRANCH_MANAGER", "HEAD_OF_CREDIT", "CREDIT_COMMITTEE");
    private static final BigDecimal FIVE_M = new BigDecimal("5000000");

    private final List<ScoringAuthorityRule> rules = List.of(
            rule(1L, "HEAD_OF_CREDIT", r -> r.setMinAmount(new BigDecimal("5000000.01"))),
            rule(2L, "HEAD_OF_CREDIT", r -> r.setOutcomes("REFER")),
            rule(3L, "CREDIT_COMMITTEE", r -> r.setMinAmount(new BigDecimal("50000000.01"))),
            rule(4L, "CREDIT_COMMITTEE", r -> r.setGrades("D,E")),
            rule(5L, "CREDIT_COMMITTEE", r -> r.setOverridden(Boolean.TRUE)));

    @Test
    void smallGradeALoanNeedsOnlyStagesWithoutRules() {
        final AuthorityRoute route = AuthorityRuleEvaluator.evaluate(STAGES, rules, context(FIVE_M, "A", ScoringOutcome.APPROVE, false));

        assertEquals(Set.of("HEAD_OF_CREDIT", "CREDIT_COMMITTEE"), route.skipped());
        assertEquals(AuthorityRoute.ALWAYS, route.stages().getFirst().reason());
        assertEquals(AuthorityRoute.NOT_MATCHED, route.stages().get(2).reason());
    }

    @Test
    void amountAboveBandAddsHeadOfCredit() {
        final AuthorityRoute route = AuthorityRuleEvaluator.evaluate(STAGES, rules,
                context(new BigDecimal("8000000"), "B", ScoringOutcome.APPROVE, false));

        assertEquals(Set.of("CREDIT_COMMITTEE"), route.skipped());
        assertEquals(List.of(1L), route.stages().get(2).matchedRuleIds());
    }

    @Test
    void referAddsHeadOfCreditWhateverTheAmount() {
        final AuthorityRoute route = AuthorityRuleEvaluator.evaluate(STAGES, rules, context(FIVE_M, "C", ScoringOutcome.REFER, false));

        assertEquals(Set.of("CREDIT_COMMITTEE"), route.skipped());
        assertEquals(List.of(2L), route.stages().get(2).matchedRuleIds());
    }

    @Test
    void poorGradeGoesToCommittee() {
        final AuthorityRoute route = AuthorityRuleEvaluator.evaluate(STAGES, rules, context(FIVE_M, "d", ScoringOutcome.APPROVE, false));

        assertTrue(route.stages().get(3).required());
        assertEquals(List.of(4L), route.stages().get(3).matchedRuleIds());
    }

    @Test
    void overriddenDeclineGoesToCommittee() {
        final AuthorityRoute route = AuthorityRuleEvaluator.evaluate(STAGES, rules, context(FIVE_M, "E", ScoringOutcome.REFER, true));

        assertEquals(Set.of(), route.skipped());
        assertEquals(List.of(4L, 5L), route.stages().get(3).matchedRuleIds());
    }

    @Test
    void scoredProductWithoutDecisionNeedsEveryStage() {
        final AuthorityRoute route = AuthorityRuleEvaluator.evaluate(STAGES, rules, context(FIVE_M, null, null, false));

        assertEquals(Set.of(), route.skipped());
        assertEquals(AuthorityRoute.NO_DECISION, route.stages().get(3).reason());
    }

    @Test
    void unscoredProductStillRoutesByAmount() {
        final AuthorityContext context = new AuthorityContext(1L, 3L, "UGX", new BigDecimal("60000000"), new BigDecimal("60000000"), null,
                null, false, false);

        final AuthorityRoute route = AuthorityRuleEvaluator.evaluate(STAGES, rules, context);

        assertEquals(Set.of(), route.skipped());
    }

    @Test
    void productCurrencyAndExposureConditionsMustAllHold() {
        final ScoringAuthorityRule rule = rule(9L, "CREDIT_COMMITTEE", r -> {
            r.setLoanProductId(3L);
            r.setCurrencyCode("UGX");
            r.setMinExposure(new BigDecimal("20000000"));
        });
        final AuthorityContext highExposure = new AuthorityContext(1L, 3L, "ugx", FIVE_M, new BigDecimal("25000000"), "A",
                ScoringOutcome.APPROVE, false, true);
        final AuthorityContext otherProduct = new AuthorityContext(1L, 4L, "UGX", FIVE_M, new BigDecimal("25000000"), "A",
                ScoringOutcome.APPROVE, false, true);
        final AuthorityContext lowExposure = new AuthorityContext(1L, 3L, "UGX", FIVE_M, new BigDecimal("19999999"), "A",
                ScoringOutcome.APPROVE, false, true);

        assertTrue(AuthorityRuleEvaluator.matches(rule, highExposure));
        assertFalse(AuthorityRuleEvaluator.matches(rule, otherProduct));
        assertFalse(AuthorityRuleEvaluator.matches(rule, lowExposure));
    }

    private static AuthorityContext context(final BigDecimal amount, final String grade, final ScoringOutcome outcome,
            final boolean overridden) {
        return new AuthorityContext(1L, 3L, "UGX", amount, amount, grade, outcome, overridden, true);
    }

    private static ScoringAuthorityRule rule(final Long id, final String stage, final java.util.function.Consumer<ScoringAuthorityRule> setup) {
        final ScoringAuthorityRule rule = new ScoringAuthorityRule();
        rule.setId(id);
        rule.setTaskPermissionCode("APPROVE_LOAN");
        rule.setStageCode(stage);
        setup.accept(rule);
        return rule;
    }
}
