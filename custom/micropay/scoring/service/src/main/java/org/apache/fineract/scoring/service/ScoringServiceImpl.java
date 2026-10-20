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
package org.apache.fineract.scoring.service;

import static org.apache.fineract.scoring.api.ScoringApiConstants.MODULE_ENABLED_PROPERTY;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.scoring.domain.Scorecard;
import org.apache.fineract.scoring.domain.ScorecardBin;
import org.apache.fineract.scoring.domain.ScorecardRepository;
import org.apache.fineract.scoring.domain.ScorecardRule;
import org.apache.fineract.scoring.domain.ScorecardStatus;
import org.apache.fineract.scoring.domain.ScoringDecision;
import org.apache.fineract.scoring.domain.ScoringDecisionRepository;
import org.apache.fineract.scoring.domain.ScoringMode;
import org.apache.fineract.scoring.domain.ScoringProductConfig;
import org.apache.fineract.scoring.domain.ScoringProductConfigRepository;
import org.apache.fineract.scoring.domain.ScoringTrigger;
import org.apache.fineract.scoring.engine.ScoringEngine;
import org.apache.fineract.scoring.engine.ScoringResult;
import org.apache.fineract.scoring.exception.ScorecardNotFoundException;
import org.apache.fineract.scoring.exception.ScoringConfigurationException;
import org.apache.fineract.scoring.input.ScoringCharacteristics;
import org.apache.fineract.scoring.input.ScoringInputCollector;
import org.apache.fineract.scoring.input.ScoringInputCollector.LoanFacts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class ScoringServiceImpl implements ScoringService {

    private static final Gson GSON = new Gson();

    private final ScoringProductConfigRepository productConfigRepository;
    private final ScorecardRepository scorecardRepository;
    private final ScoringDecisionRepository decisionRepository;
    private final ScoringInputCollector inputCollector;
    private final ScoringEngine scoringEngine;

    @Override
    @Transactional(readOnly = true)
    public Optional<ScoringProductConfig> findActiveConfig(final Long loanProductId) {
        return this.productConfigRepository.findByLoanProductId(loanProductId).filter(config -> config.getMode() != ScoringMode.OFF);
    }

    /**
     * Runs in its own transaction on purpose. Inputs are plain reads of committed data, so they do not wait on the
     * caller's locks, and a failure here cannot mark the caller's loan transaction for rollback. The decision table
     * has no foreign key to m_loan for the same reason: the insert must not wait on the loan row the caller holds.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ScoringDecision scoreAndRecord(final LoanFacts loan, final ScoringProductConfig config, final ScoringTrigger trigger) {
        final Scorecard scorecard = this.scorecardRepository.findFirstByCodeAndStatus(config.getScorecardCode(), ScorecardStatus.ACTIVE)
                .orElseThrow(() -> new ScorecardNotFoundException(config.getScorecardCode()));
        final Map<String, Object> inputs = this.inputCollector.collect(loan, datatableCharacteristics(scorecard));
        final ScoringResult result = this.scoringEngine.evaluate(scorecard, inputs);

        final ScoringDecision decision = new ScoringDecision();
        decision.setClientId(loan.clientId());
        decision.setLoanId(loan.loanId());
        decision.setLoanProductId(loan.productId());
        decision.setTrigger(trigger);
        decision.setMode(config.getMode());
        decision.setScorecardId(scorecard.getId());
        decision.setScorecardCode(scorecard.getCode());
        decision.setScorecardVersion(scorecard.getVersion());
        decision.setScore(result.score());
        decision.setGrade(result.grade());
        decision.setExpectedPd(result.expectedPd());
        decision.setOutcome(result.outcome());
        decision.setReasonCodes(String.join(",", result.reasonCodes()));
        decision.setRuleResults(GSON.toJson(result.ruleResults().stream().map(rule -> {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("ruleType", rule.ruleType());
            row.put("characteristic", rule.characteristic());
            row.put("operator", rule.operator());
            row.put("threshold", rule.threshold());
            row.put("actual", jsonSafe(rule.actual()));
            row.put("fired", rule.fired());
            row.put("outcome", rule.outcome().name());
            row.put("reasonCode", rule.reasonCode());
            return row;
        }).toList()));
        decision.setPointsBreakdown(GSON.toJson(result.breakdown().stream().map(line -> {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("characteristic", line.characteristic());
            row.put("value", jsonSafe(line.value()));
            row.put("points", line.points());
            row.put("maxPoints", line.maxPoints());
            row.put("reasonCode", line.reasonCode());
            return row;
        }).toList()));
        final Map<String, Object> snapshot = new LinkedHashMap<>();
        inputs.forEach((key, value) -> snapshot.put(key, jsonSafe(value)));
        decision.setInputsSnapshot(GSON.toJson(snapshot));
        return this.decisionRepository.saveAndFlush(decision);
    }

    @Override
    @Transactional(readOnly = true)
    public ScoringResult test(final Long scorecardId, final Long loanId) {
        final Scorecard scorecard = this.scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ScorecardNotFoundException(scorecardId));
        final LoanFacts loan = this.inputCollector.loanFacts(loanId);
        if (loan == null || loan.clientId() == null) {
            throw new ScoringConfigurationException("loan.not.scorable", "Loan " + loanId + " does not exist or has no client", loanId);
        }
        return this.scoringEngine.evaluate(scorecard, this.inputCollector.collect(loan, datatableCharacteristics(scorecard)));
    }

    private static Set<String> datatableCharacteristics(final Scorecard scorecard) {
        final Set<String> codes = new TreeSet<>();
        scorecard.getBins().stream().map(ScorecardBin::getCharacteristic).filter(ScoringCharacteristics::isDatatable).forEach(codes::add);
        scorecard.getRules().stream().map(ScorecardRule::getCharacteristic).filter(ScoringCharacteristics::isDatatable).forEach(codes::add);
        return codes;
    }

    /**
     * Keeps numbers, booleans and text as they are and turns anything else (dates and timestamps from datatables) into
     * text, so the snapshot serialises the same on every JDK.
     */
    private static Object jsonSafe(final Object value) {
        if (value == null || value instanceof BigDecimal || value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        return value.toString();
    }
}
