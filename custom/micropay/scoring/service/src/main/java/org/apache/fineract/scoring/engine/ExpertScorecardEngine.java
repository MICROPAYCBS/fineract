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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.fineract.scoring.domain.RuleOperator;
import org.apache.fineract.scoring.domain.Scorecard;
import org.apache.fineract.scoring.domain.ScorecardBin;
import org.apache.fineract.scoring.domain.ScorecardGradeBand;
import org.apache.fineract.scoring.domain.ScorecardRule;
import org.apache.fineract.scoring.domain.ScoringOutcome;
import org.apache.fineract.scoring.domain.ScoringRuleType;
import org.apache.fineract.scoring.engine.ScoringResult.PointsLine;
import org.apache.fineract.scoring.engine.ScoringResult.RuleResult;
import org.apache.fineract.scoring.input.ScoringCharacteristics;
import org.springframework.stereotype.Component;

/**
 * Points-based expert scorecard: base points plus the points of the matching bin for each characteristic, mapped to a
 * grade band, with knock-out and refer rules applied on top. Pure function of its inputs, so it is unit-tested without
 * a database.
 */
@Component
public class ExpertScorecardEngine implements ScoringEngine {

    public static final String REASON_SCORE_OUTSIDE_BANDS = "SCORE_OUTSIDE_BANDS";
    private static final int MAX_ADVERSE_REASONS = 3;

    @Override
    public ScoringResult evaluate(final Scorecard scorecard, final Map<String, Object> inputs) {
        final List<PointsLine> breakdown = scoreCharacteristics(scorecard, inputs);
        final BigDecimal score = breakdown.stream().map(PointsLine::points).reduce(scorecard.getBasePoints(), BigDecimal::add);

        final List<String> reasonCodes = new ArrayList<>();
        String grade = null;
        BigDecimal expectedPd = null;
        ScoringOutcome outcome;
        final ScorecardGradeBand band = findBand(scorecard, score);
        if (band != null) {
            grade = band.getGrade();
            expectedPd = band.getExpectedPd();
            outcome = band.getOutcome();
        } else {
            outcome = ScoringOutcome.REFER;
            reasonCodes.add(REASON_SCORE_OUTSIDE_BANDS);
        }

        final List<RuleResult> ruleResults = new ArrayList<>();
        for (final ScorecardRule rule : scorecard.getRules()) {
            final Object actual = actualValueFor(rule, inputs);
            final boolean fired = fires(rule, actual);
            ruleResults.add(new RuleResult(rule.getRuleType().name(), rule.getCharacteristic(),
                    rule.getOperator() == null ? null : rule.getOperator().name(), rule.getValue(), actual, fired, rule.getOutcome(),
                    rule.getReasonCode()));
            if (fired) {
                outcome = ScoringOutcome.worst(outcome, rule.getOutcome());
                reasonCodes.add(rule.getReasonCode());
            }
        }

        breakdown.stream().filter(line -> line.reasonCode() != null && line.shortfall().signum() > 0)
                .sorted(Comparator.comparing(PointsLine::shortfall).reversed()).limit(MAX_ADVERSE_REASONS)
                .forEach(line -> reasonCodes.add(line.reasonCode()));

        return new ScoringResult(score, grade, expectedPd, outcome, reasonCodes.stream().distinct().toList(), ruleResults, breakdown);
    }

    private List<PointsLine> scoreCharacteristics(final Scorecard scorecard, final Map<String, Object> inputs) {
        final Map<String, List<ScorecardBin>> binsByCharacteristic = new LinkedHashMap<>();
        for (final ScorecardBin bin : scorecard.getBins()) {
            binsByCharacteristic.computeIfAbsent(bin.getCharacteristic(), key -> new ArrayList<>()).add(bin);
        }

        final List<PointsLine> lines = new ArrayList<>();
        binsByCharacteristic.forEach((characteristic, bins) -> {
            final Object value = inputs.get(characteristic);
            // a value no bin matches scores 0, so 0 is a reachable best too; without it a penalty-only characteristic never
            // shows up among the adverse reasons
            final BigDecimal maxPoints = bins.stream().map(ScorecardBin::getPoints).reduce(BigDecimal.ZERO, BigDecimal::max);
            final ScorecardBin matched = bins.stream().filter(bin -> matches(bin, value)).findFirst().orElse(null);
            if (matched == null) {
                lines.add(new PointsLine(characteristic, value, BigDecimal.ZERO, maxPoints, null));
            } else {
                lines.add(new PointsLine(characteristic, value, matched.getPoints(), maxPoints, matched.getReasonCode()));
            }
        });
        return lines;
    }

    static boolean matches(final ScorecardBin bin, final Object value) {
        if (value == null) {
            return bin.isMissingBin();
        }
        if (bin.isMissingBin()) {
            return false;
        }
        if (bin.getMatchValue() != null) {
            return bin.getMatchValue().equalsIgnoreCase(asText(value));
        }
        final BigDecimal number = ScoringCharacteristics.toNumber(value);
        if (number == null) {
            return false;
        }
        final boolean aboveLower = bin.getLower() == null || number.compareTo(bin.getLower()) >= 0;
        final boolean belowUpper = bin.getUpper() == null || number.compareTo(bin.getUpper()) < 0;
        return aboveLower && belowUpper;
    }

    private static ScorecardGradeBand findBand(final Scorecard scorecard, final BigDecimal score) {
        return scorecard.getGradeBands().stream()
                .filter(band -> score.compareTo(band.getMinScore()) >= 0 && score.compareTo(band.getMaxScore()) <= 0).findFirst()
                .orElse(null);
    }

    private static Object actualValueFor(final ScorecardRule rule, final Map<String, Object> inputs) {
        return switch (rule.getRuleType()) {
            case EXPIRED_ID -> inputs.get(ScoringCharacteristics.CLIENT_HAS_EXPIRED_ID);
            case BLACKLISTED -> inputs.get(ScoringCharacteristics.CLIENT_BLACKLISTED_FOR_LOANS);
            case CUSTOMER_CLASS_NOT_ELIGIBLE -> inputs.get(ScoringCharacteristics.CLIENT_CLASS_NOT_LOAN_ELIGIBLE);
            case CHARACTERISTIC -> inputs.get(rule.getCharacteristic());
        };
    }

    static boolean fires(final ScorecardRule rule, final Object actual) {
        if (rule.getRuleType() != ScoringRuleType.CHARACTERISTIC) {
            return Boolean.TRUE.equals(actual);
        }
        final RuleOperator operator = rule.getOperator();
        if (operator == RuleOperator.MISSING) {
            return actual == null;
        }
        if (actual == null) {
            return false;
        }
        final BigDecimal actualNumber = ScoringCharacteristics.toNumber(actual);
        final BigDecimal threshold = ScoringCharacteristics.toNumber(rule.getValue());
        if (actualNumber != null && threshold != null) {
            final int comparison = actualNumber.compareTo(threshold);
            return switch (operator) {
                case LT -> comparison < 0;
                case LTE -> comparison <= 0;
                case GT -> comparison > 0;
                case GTE -> comparison >= 0;
                case EQ -> comparison == 0;
                case NEQ -> comparison != 0;
                case MISSING -> false;
            };
        }
        final boolean equal = Objects.equals(asText(actual).toLowerCase(Locale.ROOT),
                rule.getValue() == null ? null : rule.getValue().toLowerCase(Locale.ROOT));
        return switch (operator) {
            case EQ -> equal;
            case NEQ -> !equal;
            default -> false;
        };
    }

    private static String asText(final Object value) {
        if (value instanceof BigDecimal number) {
            return number.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }
}
