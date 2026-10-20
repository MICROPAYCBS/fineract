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
import static org.apache.fineract.scoring.api.ScoringApiConstants.OVERRIDE_REASON_CODE_NAME;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.scoring.data.ScoringAuthorityRuleRequest;
import org.apache.fineract.scoring.data.ScoringOverrideRequest;
import org.apache.fineract.scoring.domain.ScoringAuthorityRule;
import org.apache.fineract.scoring.domain.ScoringAuthorityRuleRepository;
import org.apache.fineract.scoring.domain.ScoringDecision;
import org.apache.fineract.scoring.domain.ScoringDecisionRepository;
import org.apache.fineract.scoring.domain.ScoringOutcome;
import org.apache.fineract.scoring.domain.ScoringOverride;
import org.apache.fineract.scoring.domain.ScoringOverrideRepository;
import org.apache.fineract.scoring.exception.ScoringAuthorityRuleNotFoundException;
import org.apache.fineract.scoring.exception.ScoringConfigurationException;
import org.apache.fineract.scoring.exception.ScoringDecisionNotFoundException;
import org.apache.fineract.scoring.serialization.ScoringAuthorityDataValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class ScoringAuthorityWritePlatformServiceImpl implements ScoringAuthorityWritePlatformService {

    private static final int LOAN_STATUS_SUBMITTED = 100;

    private final ScoringAuthorityDataValidator validator;
    private final ScoringAuthorityRuleRepository ruleRepository;
    private final ScoringDecisionRepository decisionRepository;
    private final ScoringOverrideRepository overrideRepository;
    private final ScoringService scoringService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public CommandProcessingResult createRule(final JsonCommand command) {
        final ScoringAuthorityRule rule = new ScoringAuthorityRule();
        apply(rule, this.validator.validateAndParseRule(command.json()));
        this.ruleRepository.saveAndFlush(rule);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(rule.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateRule(final Long ruleId, final JsonCommand command) {
        final ScoringAuthorityRule rule = this.ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ScoringAuthorityRuleNotFoundException(ruleId));
        apply(rule, this.validator.validateAndParseRule(command.json()));
        this.ruleRepository.saveAndFlush(rule);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(ruleId).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteRule(final Long ruleId, final JsonCommand command) {
        final ScoringAuthorityRule rule = this.ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ScoringAuthorityRuleNotFoundException(ruleId));
        this.ruleRepository.delete(rule);
        this.ruleRepository.flush();
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(ruleId).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult overrideDecision(final Long decisionId, final JsonCommand command) {
        final ScoringOverrideRequest request = this.validator.validateAndParseOverride(command.json());
        final ScoringDecision decision = this.decisionRepository.findById(decisionId)
                .orElseThrow(() -> new ScoringDecisionNotFoundException(decisionId));
        final Long loanId = decision.getLoanId();
        if (loanId == null) {
            throw new ScoringConfigurationException("override.no.loan", "Scoring decision " + decisionId + " is not for a loan", decisionId);
        }
        final Long latestId = this.decisionRepository.findFirstByLoanIdOrderByIdDesc(loanId).map(ScoringDecision::getId).orElse(null);
        if (!decisionId.equals(latestId)) {
            throw new ScoringConfigurationException("override.decision.not.latest",
                    "Only the latest scoring decision of loan " + loanId + " can be overridden (decision " + latestId + ")", decisionId,
                    latestId);
        }
        final List<Integer> status = this.jdbcTemplate.queryForList("SELECT loan_status_id FROM m_loan WHERE id = ?", Integer.class, loanId);
        if (status.isEmpty() || status.getFirst() != LOAN_STATUS_SUBMITTED) {
            throw new ScoringConfigurationException("override.loan.not.pending.approval",
                    "Loan " + loanId + " is not awaiting approval, so its scoring decision cannot be overridden", loanId);
        }
        validateReason(request.getReasonId());

        final ScoringOutcome from = this.scoringService.effectiveOutcome(decision);
        final ScoringOutcome to = ScoringOutcome.valueOf(request.getToOutcome());
        if (from == to) {
            throw new ScoringConfigurationException("override.same.outcome", "The outcome of loan " + loanId + " is already " + to, loanId,
                    to);
        }

        final ScoringOverride override = new ScoringOverride();
        override.setDecisionId(decisionId);
        override.setLoanId(loanId);
        override.setClientId(decision.getClientId());
        override.setFromOutcome(from);
        override.setToOutcome(to);
        override.setReasonCodeValueId(request.getReasonId());
        override.setComment(request.getComment());
        this.overrideRepository.saveAndFlush(override);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(override.getId()).withLoanId(loanId)
                .withClientId(decision.getClientId()).build();
    }

    private void apply(final ScoringAuthorityRule rule, final ScoringAuthorityRuleRequest request) {
        if (request.getLoanProductId() != null) {
            final Long count = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_product_loan WHERE id = ?", Long.class,
                    request.getLoanProductId());
            if (count == null || count == 0) {
                throw new ScoringConfigurationException("loan.product.not.found", "Loan product " + request.getLoanProductId() + " does not exist",
                        request.getLoanProductId());
            }
        }
        rule.setTaskPermissionCode(request.getTaskPermissionCode());
        rule.setStageCode(request.getStageCode());
        rule.setDescription(request.getDescription());
        rule.setLoanProductId(request.getLoanProductId());
        rule.setCurrencyCode(request.getCurrencyCode());
        rule.setMinAmount(request.getMinAmount());
        rule.setMaxAmount(request.getMaxAmount());
        rule.setMinExposure(request.getMinExposure());
        rule.setMaxExposure(request.getMaxExposure());
        rule.setGrades(join(request.getGrades()));
        rule.setOutcomes(join(request.getOutcomes()));
        rule.setOverridden(request.getOverridden());
    }

    private void validateReason(final Long reasonId) {
        final Long count = this.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM m_code_value cv JOIN m_code c ON c.id = cv.code_id WHERE cv.id = ? AND c.code_name = ? AND cv.is_active = ?",
                Long.class, reasonId, OVERRIDE_REASON_CODE_NAME, Boolean.TRUE);
        if (count == null || count == 0) {
            throw new ScoringConfigurationException("override.reason.unknown",
                    "Reason " + reasonId + " is not an active value of the " + OVERRIDE_REASON_CODE_NAME + " code", reasonId);
        }
    }

    private static String join(final List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(",", values.stream().map(String::trim).map(String::toUpperCase).toList());
    }
}
