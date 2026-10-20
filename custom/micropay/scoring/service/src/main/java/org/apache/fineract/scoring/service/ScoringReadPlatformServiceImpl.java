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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.scoring.data.ScorecardData;
import org.apache.fineract.scoring.data.ScoringCharacteristicData;
import org.apache.fineract.scoring.data.ScoringDecisionData;
import org.apache.fineract.scoring.data.ScoringOverrideData;
import org.apache.fineract.scoring.data.ScoringProductConfigData;
import org.apache.fineract.scoring.domain.Scorecard;
import org.apache.fineract.scoring.domain.ScorecardRepository;
import org.apache.fineract.scoring.domain.ScoringDecision;
import org.apache.fineract.scoring.domain.ScoringDecisionRepository;
import org.apache.fineract.scoring.domain.ScoringOverride;
import org.apache.fineract.scoring.domain.ScoringOverrideRepository;
import org.apache.fineract.scoring.domain.ScoringProductConfigRepository;
import org.apache.fineract.scoring.exception.ScorecardNotFoundException;
import org.apache.fineract.scoring.input.ScoringCharacteristics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class ScoringReadPlatformServiceImpl implements ScoringReadPlatformService {

    private static final Gson GSON = new Gson();

    private final ScorecardRepository scorecardRepository;
    private final ScoringProductConfigRepository productConfigRepository;
    private final ScoringDecisionRepository decisionRepository;
    private final ScoringOverrideRepository overrideRepository;
    private final ScoringService scoringService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ScorecardData> retrieveScorecards(final String code, final String status) {
        return this.scorecardRepository.findAllByOrderByCodeAscVersionDesc().stream()
                .filter(scorecard -> StringUtils.isBlank(code) || scorecard.getCode().equals(code))
                .filter(scorecard -> StringUtils.isBlank(status) || scorecard.getStatus().name().equalsIgnoreCase(status))
                .map(ScorecardData::from).toList();
    }

    @Override
    public ScorecardData retrieveScorecard(final Long scorecardId) {
        final Scorecard scorecard = this.scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ScorecardNotFoundException(scorecardId));
        return ScorecardData.from(scorecard);
    }

    @Override
    public List<ScoringCharacteristicData> retrieveCharacteristics() {
        return ScoringCharacteristics.BUILT_IN.stream()
                .map(characteristic -> new ScoringCharacteristicData(characteristic.code(), characteristic.dataType(),
                        characteristic.description()))
                .toList();
    }

    @Override
    public ScoringProductConfigData retrieveProductConfig(final Long loanProductId) {
        return this.productConfigRepository.findByLoanProductId(loanProductId).map(ScoringProductConfigData::from)
                .orElse(new ScoringProductConfigData(loanProductId, null, "OFF"));
    }

    @Override
    public List<ScoringDecisionData> retrieveDecisions(final Long loanId, final Long clientId) {
        final List<ScoringDecision> decisions;
        if (loanId != null) {
            decisions = this.decisionRepository.findByLoanIdOrderByIdDesc(loanId);
        } else if (clientId != null) {
            decisions = this.decisionRepository.findByClientIdOrderByIdDesc(clientId);
        } else {
            throw new PlatformApiDataValidationException("validation.msg.scoring.decisions.filter.required",
                    "Pass loanId or clientId to list scoring decisions", "loanId");
        }
        final Map<Long, List<ScoringOverrideData>> overrides = overridesByDecision(decisions);
        return decisions.stream().map(decision -> toData(decision, overrides.getOrDefault(decision.getId(), List.of()))).toList();
    }

    private Map<Long, List<ScoringOverrideData>> overridesByDecision(final List<ScoringDecision> decisions) {
        if (decisions.isEmpty()) {
            return Map.of();
        }
        final List<ScoringOverride> overrides = this.overrideRepository
                .findByDecisionIdInOrderByIdAsc(decisions.stream().map(ScoringDecision::getId).toList());
        final Map<Long, Optional<Long>> effectiveByLoan = new HashMap<>();
        final Map<Long, String> reasons = new HashMap<>();
        final Map<Long, List<ScoringOverrideData>> byDecision = new HashMap<>();
        for (final ScoringOverride override : overrides) {
            final Optional<Long> effectiveId = effectiveByLoan.computeIfAbsent(override.getLoanId(),
                    loanId -> this.scoringService.findEffectiveOverride(loanId).map(ScoringOverride::getId));
            final String reason = reasons.computeIfAbsent(override.getReasonCodeValueId(), this::codeValueName);
            byDecision.computeIfAbsent(override.getDecisionId(), id -> new ArrayList<>())
                    .add(new ScoringOverrideData(override.getId(), override.getDecisionId(), override.getLoanId(),
                            override.getFromOutcome().name(), override.getToOutcome().name(), override.getReasonCodeValueId(), reason,
                            override.getComment(), effectiveId.filter(override.getId()::equals).isPresent(),
                            override.getCreatedBy().orElse(null), override.getCreatedDate().orElse(null)));
        }
        return byDecision;
    }

    private String codeValueName(final Long codeValueId) {
        final List<String> names = this.jdbcTemplate.queryForList("SELECT code_value FROM m_code_value WHERE id = ?", String.class,
                codeValueId);
        return names.isEmpty() ? null : names.getFirst();
    }

    private static ScoringDecisionData toData(final ScoringDecision decision, final List<ScoringOverrideData> overrides) {
        final List<String> reasonCodes = StringUtils.isBlank(decision.getReasonCodes()) ? List.of()
                : Arrays.asList(decision.getReasonCodes().split(","));
        return new ScoringDecisionData(decision.getId(), decision.getClientId(), decision.getLoanId(), decision.getLoanProductId(),
                decision.getTrigger().name(), decision.getMode().name(), decision.getScorecardId(), decision.getScorecardCode(),
                decision.getScorecardVersion(), decision.getScore(), decision.getGrade(), decision.getExpectedPd(),
                decision.getOutcome().name(), reasonCodes, parse(decision.getRuleResults()), parse(decision.getPointsBreakdown()),
                parse(decision.getInputsSnapshot()), overrides, decision.getCreatedBy().orElse(null), decision.getCreatedDate().orElse(null));
    }

    private static Object parse(final String json) {
        return json == null ? null : GSON.fromJson(json, Object.class);
    }
}
