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

import java.util.Optional;
import org.apache.fineract.scoring.domain.ScoringDecision;
import org.apache.fineract.scoring.domain.ScoringProductConfig;
import org.apache.fineract.scoring.domain.ScoringTrigger;
import org.apache.fineract.scoring.engine.ScoringResult;
import org.apache.fineract.scoring.input.ScoringInputCollector.LoanFacts;

public interface ScoringService {

    /**
     * The product's scoring configuration, when scoring is switched on for it.
     */
    Optional<ScoringProductConfig> findActiveConfig(Long loanProductId);

    /**
     * Scores the loan with its product's active scorecard and saves the decision in a transaction of its own, so the
     * decision is kept even when the caller's transaction rolls back.
     */
    ScoringDecision scoreAndRecord(LoanFacts loan, ScoringProductConfig config, ScoringTrigger trigger);

    /**
     * Scores a loan with any scorecard version, drafts included, without saving anything.
     */
    ScoringResult test(Long scorecardId, Long loanId);
}
