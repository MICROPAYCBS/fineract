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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.scoring.data.ScoringClientLimitData;
import org.apache.fineract.scoring.data.ScoringLimitPolicyData;
import org.apache.fineract.scoring.data.ScoringLimitPolicyRequest;
import org.apache.fineract.scoring.domain.ScorecardRepository;
import org.apache.fineract.scoring.domain.ScoringClientLimit;
import org.apache.fineract.scoring.domain.ScoringClientLimitRepository;
import org.apache.fineract.scoring.domain.ScoringLimitBand;
import org.apache.fineract.scoring.domain.ScoringLimitPolicy;
import org.apache.fineract.scoring.domain.ScoringLimitPolicyRepository;
import org.apache.fineract.scoring.domain.ScoringLimitStatus;
import org.apache.fineract.scoring.domain.ScoringMode;
import org.apache.fineract.scoring.exception.ScoringConfigurationException;
import org.apache.fineract.scoring.exception.ScoringLimitPolicyNotFoundException;
import org.apache.fineract.scoring.limit.LimitCheckService;
import org.apache.fineract.scoring.serialization.ScoringLimitPolicyDataValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class ScoringLimitPlatformServiceImpl implements ScoringLimitPlatformService {

    private final ScoringLimitPolicyDataValidator validator;
    private final ScoringLimitPolicyRepository policyRepository;
    private final ScoringClientLimitRepository limitRepository;
    private final ScorecardRepository scorecardRepository;
    private final LimitCheckService limitCheckService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public CommandProcessingResult createPolicy(final JsonCommand command) {
        final ScoringLimitPolicyRequest request = this.validator.validateAndParse(command.json());
        if (this.policyRepository.existsByCode(request.getCode())) {
            throw new ScoringConfigurationException("limit.policy.code.exists", "A limit policy with code " + request.getCode() + " exists",
                    request.getCode());
        }
        final ScoringLimitPolicy policy = new ScoringLimitPolicy();
        policy.setCode(request.getCode());
        apply(policy, request);
        this.policyRepository.saveAndFlush(policy);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(policy.getId())
                .withProductId(policy.getLoanProductId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updatePolicy(final Long policyId, final JsonCommand command) {
        final ScoringLimitPolicy policy = this.policyRepository.findById(policyId)
                .orElseThrow(() -> new ScoringLimitPolicyNotFoundException(policyId));
        final ScoringLimitPolicyRequest request = this.validator.validateAndParse(command.json());
        if (!policy.getCode().equals(request.getCode())) {
            throw new ScoringConfigurationException("limit.policy.code.immutable", "The code of a limit policy cannot change",
                    policy.getCode());
        }
        apply(policy, request);
        this.policyRepository.saveAndFlush(policy);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(policyId)
                .withProductId(policy.getLoanProductId()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScoringLimitPolicyData> retrievePolicies() {
        return this.policyRepository.findAllByOrderByCodeAsc().stream().map(ScoringLimitPolicyData::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ScoringLimitPolicyData retrievePolicy(final Long policyId) {
        return this.policyRepository.findById(policyId).map(ScoringLimitPolicyData::from)
                .orElseThrow(() -> new ScoringLimitPolicyNotFoundException(policyId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScoringClientLimitData> retrieveClientLimits(final Long clientId) {
        final LocalDate today = DateUtils.getBusinessLocalDate();
        final Map<Long, ScoringLimitPolicy> policies = this.policyRepository.findAll().stream()
                .collect(Collectors.toMap(ScoringLimitPolicy::getId, Function.identity()));
        return this.limitRepository.findByClientIdAndStatusOrderByIdDesc(clientId, ScoringLimitStatus.ACTIVE).stream().map(limit -> {
            final BigDecimal used = this.limitCheckService.usedOnProduct(clientId, limit.getLoanProductId(), null);
            final boolean current = limit.isValidOn(today);
            final BigDecimal available = current ? limit.getAmount().subtract(used).max(BigDecimal.ZERO) : BigDecimal.ZERO;
            final ScoringLimitPolicy policy = policies.get(limit.getPolicyId());
            return new ScoringClientLimitData(limit.getId(), clientId, limit.getPolicyId(), policy == null ? null : policy.getCode(),
                    limit.getLoanProductId(), limit.getAmount(), used, available, limit.getGrade(), limit.getDecisionId(),
                    limit.getValidFrom(), limit.getValidTo(), current);
        }).toList();
    }

    private void apply(final ScoringLimitPolicy policy, final ScoringLimitPolicyRequest request) {
        final Long productCount = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_product_loan WHERE id = ?", Long.class,
                request.getLoanProductId());
        if (productCount == null || productCount == 0) {
            throw new ScoringConfigurationException("loan.product.not.found", "Loan product " + request.getLoanProductId() + " does not exist",
                    request.getLoanProductId());
        }
        if (!this.scorecardRepository.existsByCode(request.getScorecardCode())) {
            throw new ScoringConfigurationException("scorecard.code.unknown", "No scorecard has code " + request.getScorecardCode(),
                    request.getScorecardCode());
        }
        policy.setName(request.getName());
        policy.setScorecardCode(request.getScorecardCode());
        policy.setLoanProductId(request.getLoanProductId());
        policy.setMode(ScoringMode.valueOf(request.getMode()));
        policy.setStaffOnly(request.getStaffOnly());
        policy.setOfficeId(request.getOfficeId());
        policy.setCustomerClassId(request.getCustomerClassId());
        policy.setValidDays(request.getValidDays());
        policy.setBaseCharacteristic(request.getBaseCharacteristic());
        policy.setRoundingUnit(request.getRoundingUnit());
        policy.getBands().clear();
        request.getBands().forEach(band -> policy.addBand(ScoringLimitBand.create(band.getGrade().toUpperCase(), band.getMultiplier(),
                band.getMaxAmount())));
    }
}
