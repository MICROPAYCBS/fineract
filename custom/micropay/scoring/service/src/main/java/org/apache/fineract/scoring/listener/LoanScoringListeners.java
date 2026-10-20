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
package org.apache.fineract.scoring.listener;

import static org.apache.fineract.scoring.api.ScoringApiConstants.MODULE_ENABLED_PROPERTY;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanApprovedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanCreatedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanDisbursalBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.scoring.domain.ScoringDecision;
import org.apache.fineract.scoring.domain.ScoringMode;
import org.apache.fineract.scoring.domain.ScoringOutcome;
import org.apache.fineract.scoring.domain.ScoringProductConfig;
import org.apache.fineract.scoring.domain.ScoringTrigger;
import org.apache.fineract.scoring.exception.ScoringDeclinedException;
import org.apache.fineract.scoring.input.ScoringInputCollector.LoanFacts;
import org.apache.fineract.scoring.limit.LimitCheckService;
import org.apache.fineract.scoring.service.ScoringService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Connects scoring to the loan lifecycle through business events, with no change to core loan services.
 *
 * <ul>
 * <li>Submission: scores once the loan is committed. Submission is never blocked, so a declined application stays on
 * record. A submission held for maker-checker rolls back and is not scored.</li>
 * <li>Approval: scores again with the approved amount. Internal listeners run inside the approval transaction, so in
 * ENFORCED mode a DECLINE throws and rolls the approval back. With maker-checker on, this also runs when the approval
 * is first held, so a declined loan does not reach the checkers. A decline that was overridden is let through.</li>
 * <li>Disbursal: scores again just before the money goes out, so a client blacklisted or in arrears since approval is
 * caught. An ENFORCED DECLINE stops the disbursal.</li>
 * <li>Approval and disbursal also check the loan against the pre-approved limits of its product.</li>
 * </ul>
 *
 * Only loans with a client are scored; group loans are skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class LoanScoringListeners {

    private final BusinessEventNotifierService businessEventNotifierService;
    private final ScoringService scoringService;
    private final LimitCheckService limitCheckService;

    @PostConstruct
    public void addListeners() {
        businessEventNotifierService.addPostBusinessEventListener(LoanCreatedBusinessEvent.class, event -> onSubmitted(event.get()));
        businessEventNotifierService.addPostBusinessEventListener(LoanApprovedBusinessEvent.class, event -> onApproved(event.get()));
        // fired before the disbursal is applied, so throwing stops it
        businessEventNotifierService.addPreBusinessEventListener(LoanDisbursalBusinessEvent.class, event -> onDisbursing(event.get()));
    }

    void onSubmitted(final Loan loan) {
        final LoanFacts facts = facts(loan);
        if (facts.clientId() == null) {
            return;
        }
        final Optional<ScoringProductConfig> config = scoringService.findActiveConfig(facts.productId());
        if (config.isEmpty()) {
            return;
        }
        final Runnable score = () -> {
            try {
                scoringService.scoreAndRecord(facts, config.get(), ScoringTrigger.SUBMIT);
            } catch (RuntimeException e) {
                log.error("Credit scoring failed for submitted loan {}; the submission is kept", facts.loanId(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    score.run();
                }
            });
        } else {
            score.run();
        }
    }

    void onApproved(final Loan loan) {
        scoreAndCheckLimit(facts(loan), ScoringTrigger.APPROVE);
    }

    void onDisbursing(final Loan loan) {
        scoreAndCheckLimit(facts(loan), ScoringTrigger.DISBURSE);
    }

    private void scoreAndCheckLimit(final LoanFacts facts, final ScoringTrigger trigger) {
        if (facts.clientId() == null) {
            return;
        }
        score(facts, trigger);
        limitCheckService.check(facts.loanId(), facts.clientId(), facts.productId(), facts.principal());
    }

    private void score(final LoanFacts facts, final ScoringTrigger trigger) {
        final Optional<ScoringProductConfig> config = scoringService.findActiveConfig(facts.productId());
        if (config.isEmpty()) {
            return;
        }
        final boolean enforced = config.get().getMode() == ScoringMode.ENFORCED;
        final ScoringDecision decision;
        try {
            decision = scoringService.scoreAndRecord(facts, config.get(), trigger);
        } catch (RuntimeException e) {
            if (enforced) {
                // fail closed: an enforced product is never approved without a decision
                throw e;
            }
            log.error("Credit scoring failed at {} of loan {}; advisory mode, so it continues", trigger, facts.loanId(), e);
            return;
        }
        // an override with a reason code lets a declined loan go on to the approvers
        if (enforced && scoringService.effectiveOutcome(decision) == ScoringOutcome.DECLINE) {
            throw new ScoringDeclinedException(facts.loanId(), decision.getId(),
                    decision.getReasonCodes() == null ? List.of() : Arrays.asList(decision.getReasonCodes().split(",")));
        }
    }

    private static LoanFacts facts(final Loan loan) {
        return new LoanFacts(loan.getId(), loan.getClientId(), loan.productId(), loan.getPrincipal().getAmount(),
                loan.getNumberOfRepayments());
    }
}
