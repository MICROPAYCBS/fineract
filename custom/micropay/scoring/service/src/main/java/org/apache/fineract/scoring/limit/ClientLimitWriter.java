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
package org.apache.fineract.scoring.limit;

import static org.apache.fineract.scoring.api.ScoringApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.scoring.domain.ScoringClientLimit;
import org.apache.fineract.scoring.domain.ScoringClientLimitRepository;
import org.apache.fineract.scoring.domain.ScoringDecision;
import org.apache.fineract.scoring.domain.ScoringLimitPolicy;
import org.apache.fineract.scoring.domain.ScoringLimitPolicyRepository;
import org.apache.fineract.scoring.domain.ScoringLimitStatus;
import org.apache.fineract.scoring.domain.ScoringTrigger;
import org.apache.fineract.scoring.input.ScoringInputCollector.LoanFacts;
import org.apache.fineract.scoring.service.ScoringService;
import org.apache.fineract.scoring.service.ScoringService.ScoringRun;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scores one client under one policy and replaces the client's limit, in a transaction of its own so one client's
 * failure does not stop the run.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class ClientLimitWriter {

    private final ScoringLimitPolicyRepository policyRepository;
    private final ScoringClientLimitRepository limitRepository;
    private final ScoringService scoringService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ScoringClientLimit scoreClient(final Long policyId, final Long clientId, final LocalDate today) {
        final ScoringLimitPolicy policy = this.policyRepository.findById(policyId).orElseThrow();
        final ScoringRun run = this.scoringService.scoreAndRecord(LoanFacts.forClient(clientId, policy.getLoanProductId()),
                policy.getScorecardCode(), policy.getMode(), ScoringTrigger.NIGHTLY);
        final ScoringDecision decision = run.decision();
        final BigDecimal amount = LimitCalculator.calculate(policy, decision.getGrade(), decision.getOutcome(), run.inputs());

        this.limitRepository.findFirstByClientIdAndPolicyIdAndStatusOrderByIdDesc(clientId, policyId, ScoringLimitStatus.ACTIVE)
                .ifPresent(previous -> {
                    previous.setStatus(ScoringLimitStatus.SUPERSEDED);
                    this.limitRepository.save(previous);
                });
        final ScoringClientLimit limit = new ScoringClientLimit();
        limit.setClientId(clientId);
        limit.setPolicyId(policyId);
        limit.setLoanProductId(policy.getLoanProductId());
        limit.setAmount(amount);
        limit.setGrade(decision.getGrade());
        limit.setDecisionId(decision.getId());
        limit.setValidFrom(today);
        limit.setValidTo(today.plusDays(policy.getValidDays() - 1L));
        limit.setStatus(ScoringLimitStatus.ACTIVE);
        return this.limitRepository.saveAndFlush(limit);
    }
}
