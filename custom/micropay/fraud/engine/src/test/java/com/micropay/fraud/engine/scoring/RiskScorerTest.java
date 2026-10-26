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
package com.micropay.fraud.engine.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.micropay.fraud.engine.rule.RuleHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskScorerTest {

    @Test
    void reasonsCombineLikeIndependentProbabilities() {
        assertThat(RiskScorer.combine(List.of())).isZero();
        assertThat(RiskScorer.combine(List.of(new RuleHit("A", 50, null)))).isEqualTo(50);
        assertThat(RiskScorer.combine(List.of(new RuleHit("A", 50, null), new RuleHit("B", 50, null)))).isEqualTo(75);
        assertThat(RiskScorer.combine(List.of(new RuleHit("A", 100, null), new RuleHit("B", 10, null)))).isEqualTo(100);
    }

    @Test
    void anomalyContributionGrowsWithTheScoreAndIsCapped() {
        assertThat(RiskScorer.anomalyContribution(0.62)).isEqualTo(25);
        assertThat(RiskScorer.anomalyContribution(0.80)).isEqualTo(60);
        assertThat(RiskScorer.anomalyContribution(0.99)).isEqualTo(60);
    }
}
