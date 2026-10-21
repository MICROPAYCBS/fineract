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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.apache.fineract.scoring.domain.Scorecard;
import org.apache.fineract.scoring.domain.ScorecardGradeBand;
import org.apache.fineract.scoring.domain.ScorecardType;
import org.apache.fineract.scoring.domain.ScoringOutcome;
import org.apache.fineract.scoring.exception.ScoringConfigurationException;
import org.junit.jupiter.api.Test;

class ScorecardActivationValidationTest {

    @Test
    void scorecardWithoutBandsCannotBeActivated() {
        assertThrows(ScoringConfigurationException.class, () -> ScorecardWritePlatformServiceImpl.validateForActivation(scorecard()));
    }

    @Test
    void overlappingBandsCannotBeActivated() {
        final Scorecard scorecard = scorecard();
        scorecard.addGradeBand(band("A", 500, 1000));
        scorecard.addGradeBand(band("B", 400, 500));

        assertThrows(ScoringConfigurationException.class, () -> ScorecardWritePlatformServiceImpl.validateForActivation(scorecard));
    }

    @Test
    void adjacentBandsCanBeActivatedInAnyOrder() {
        final Scorecard scorecard = scorecard();
        scorecard.addGradeBand(band("A", 500, 1000));
        scorecard.addGradeBand(band("B", 400, 499));

        assertDoesNotThrow(() -> ScorecardWritePlatformServiceImpl.validateForActivation(scorecard));
    }

    private static Scorecard scorecard() {
        return Scorecard.createDraft("AGENT", 1, "Agent", null, ScorecardType.APPLICATION, BigDecimal.ZERO);
    }

    private static ScorecardGradeBand band(final String grade, final int min, final int max) {
        return ScorecardGradeBand.create(grade, BigDecimal.valueOf(min), BigDecimal.valueOf(max), null, ScoringOutcome.APPROVE);
    }
}
