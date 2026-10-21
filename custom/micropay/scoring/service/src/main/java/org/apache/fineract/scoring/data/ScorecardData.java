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
package org.apache.fineract.scoring.data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.fineract.scoring.domain.Scorecard;

public record ScorecardData(Long id, String code, Integer version, String name, String description, String type, String status,
        BigDecimal basePoints, Long activatedBy, OffsetDateTime activatedOn, List<BinData> bins, List<GradeBandData> gradeBands,
        List<RuleData> rules) {

    public record BinData(String characteristic, BigDecimal lower, BigDecimal upper, String matchValue, boolean missing, BigDecimal points,
            String reasonCode) {
    }

    public record GradeBandData(String grade, BigDecimal minScore, BigDecimal maxScore, BigDecimal expectedPd, String outcome) {
    }

    public record RuleData(String ruleType, String characteristic, String operator, String value, String outcome, String reasonCode) {
    }

    public static ScorecardData from(final Scorecard scorecard) {
        return new ScorecardData(scorecard.getId(), scorecard.getCode(), scorecard.getVersion(), scorecard.getName(),
                scorecard.getDescription(), scorecard.getType().name(), scorecard.getStatus().name(), scorecard.getBasePoints(),
                scorecard.getActivatedBy(), scorecard.getActivatedOn(),
                scorecard.getBins().stream()
                        .map(bin -> new BinData(bin.getCharacteristic(), bin.getLower(), bin.getUpper(), bin.getMatchValue(),
                                bin.isMissingBin(), bin.getPoints(), bin.getReasonCode()))
                        .toList(),
                scorecard.getGradeBands().stream()
                        .map(band -> new GradeBandData(band.getGrade(), band.getMinScore(), band.getMaxScore(), band.getExpectedPd(),
                                band.getOutcome().name()))
                        .toList(),
                scorecard.getRules().stream()
                        .map(rule -> new RuleData(rule.getRuleType().name(), rule.getCharacteristic(),
                                rule.getOperator() == null ? null : rule.getOperator().name(), rule.getValue(), rule.getOutcome().name(),
                                rule.getReasonCode()))
                        .toList());
    }
}
