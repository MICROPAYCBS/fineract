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
package org.apache.fineract.scoring.engine;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.scoring.domain.ScoringOutcome;

/**
 * Outcome of evaluating one scorecard against one set of inputs. Reason codes list the fired rules first, then the
 * characteristics that cost the most points against their best bin (adverse action reasons).
 */
public record ScoringResult(BigDecimal score, String grade, BigDecimal expectedPd, ScoringOutcome outcome, List<String> reasonCodes,
        List<RuleResult> ruleResults, List<PointsLine> breakdown) {

    public record RuleResult(String ruleType, String characteristic, String operator, String threshold, Object actual, boolean fired,
            ScoringOutcome outcome, String reasonCode) {
    }

    public record PointsLine(String characteristic, Object value, BigDecimal points, BigDecimal maxPoints, String reasonCode) {

        public BigDecimal shortfall() {
            return maxPoints.subtract(points);
        }
    }
}
