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
package org.apache.fineract.scoring.handler;

import static org.apache.fineract.scoring.api.ScoringApiConstants.MODULE_ENABLED_PROPERTY;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.scoring.domain.ScoringDecision;
import org.apache.fineract.scoring.domain.ScoringProductConfig;
import org.apache.fineract.scoring.domain.ScoringTrigger;
import org.apache.fineract.scoring.exception.ScoringConfigurationException;
import org.apache.fineract.scoring.input.ScoringInputCollector;
import org.apache.fineract.scoring.input.ScoringInputCollector.LoanFacts;
import org.apache.fineract.scoring.service.ScoringService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Re-scores a loan on request, for example after new datatable entries. The decision is recorded with trigger MANUAL
 * and never changes the loan.
 */
@Service
@CommandType(entity = "LOAN", action = "SCORE")
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class ScoreLoanCommandHandler implements NewCommandSourceHandler {

    private final ScoringService scoringService;
    private final ScoringInputCollector inputCollector;

    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {
        final Long loanId = command.getLoanId();
        final LoanFacts loan = this.inputCollector.loanFacts(loanId);
        if (loan == null || loan.clientId() == null) {
            throw new ScoringConfigurationException("loan.not.scorable", "Loan " + loanId + " does not exist or has no client", loanId);
        }
        final ScoringProductConfig config = this.scoringService.findActiveConfig(loan.productId())
                .orElseThrow(() -> new ScoringConfigurationException("product.not.configured",
                        "Scoring is off for the product of loan " + loanId, loanId));
        final ScoringDecision decision = this.scoringService.scoreAndRecord(loan, config, ScoringTrigger.MANUAL);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(decision.getId()).withLoanId(loanId)
                .withClientId(loan.clientId()).build();
    }
}
