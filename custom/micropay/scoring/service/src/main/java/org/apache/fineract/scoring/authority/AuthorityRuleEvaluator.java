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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.scoring.authority.AuthorityRoute.StageRoute;
import org.apache.fineract.scoring.domain.ScoringAuthorityRule;

/**
 * Decides stage by stage which approval stages a loan needs.
 *
 * <ul>
 * <li>A stage with no rules always applies.</li>
 * <li>A stage with rules applies when at least one rule matches; every condition of a rule must hold, and an empty
 * condition holds for any loan.</li>
 * <li>Fail closed: when the product is scored but the loan has no decision yet, every stage applies.</li>
 * </ul>
 */
public final class AuthorityRuleEvaluator {

    private AuthorityRuleEvaluator() {}

    public static AuthorityRoute evaluate(final List<String> stageCodes, final List<ScoringAuthorityRule> rules,
            final AuthorityContext context) {
        final Map<String, List<ScoringAuthorityRule>> rulesByStage = rules.stream()
                .collect(Collectors.groupingBy(ScoringAuthorityRule::getStageCode));
        final boolean missingDecision = context.scoringExpected() && !context.hasDecision();
        return new AuthorityRoute(stageCodes.stream().map(stageCode -> {
            final List<ScoringAuthorityRule> stageRules = rulesByStage.getOrDefault(stageCode, List.of());
            if (stageRules.isEmpty()) {
                return new StageRoute(stageCode, true, AuthorityRoute.ALWAYS, List.of());
            }
            if (missingDecision) {
                return new StageRoute(stageCode, true, AuthorityRoute.NO_DECISION, List.of());
            }
            final List<Long> matched = stageRules.stream().filter(rule -> matches(rule, context)).map(ScoringAuthorityRule::getId)
                    .toList();
            return matched.isEmpty() ? new StageRoute(stageCode, false, AuthorityRoute.NOT_MATCHED, List.of())
                    : new StageRoute(stageCode, true, AuthorityRoute.MATCHED, matched);
        }).toList());
    }

    static boolean matches(final ScoringAuthorityRule rule, final AuthorityContext context) {
        return (rule.getLoanProductId() == null || rule.getLoanProductId().equals(context.loanProductId()))
                && (rule.getCurrencyCode() == null || rule.getCurrencyCode().equalsIgnoreCase(context.currencyCode()))
                && within(context.amount(), rule.getMinAmount(), rule.getMaxAmount())
                && within(context.exposure(), rule.getMinExposure(), rule.getMaxExposure())
                && listed(rule.getGrades(), context.grade())
                && listed(rule.getOutcomes(), context.outcome() == null ? null : context.outcome().name())
                && (rule.getOverridden() == null || rule.getOverridden() == context.overridden());
    }

    /** Both bounds are inclusive, so adjacent bands should not share a bound. */
    private static boolean within(final BigDecimal value, final BigDecimal min, final BigDecimal max) {
        if (min == null && max == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return (min == null || value.compareTo(min) >= 0) && (max == null || value.compareTo(max) <= 0);
    }

    private static boolean listed(final String csv, final String value) {
        if (StringUtils.isBlank(csv)) {
            return true;
        }
        return value != null && Arrays.stream(csv.split(",")).map(String::trim).anyMatch(value::equalsIgnoreCase);
    }
}
