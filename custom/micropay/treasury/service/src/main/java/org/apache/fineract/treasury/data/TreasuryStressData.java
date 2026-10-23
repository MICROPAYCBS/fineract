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
package org.apache.fineract.treasury.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A STRESS run: the scenario, the survival horizon, the buckets, the capacity and the lines before and after the
 * shocks.
 */
public record TreasuryStressData(Long runId, LocalDate asOfDate, String currencyCode, Long officeId, Long ladderRunId, Long scenarioId,
        String scenarioCode, Integer scenarioVersion, BigDecimal survivalDays, boolean beyondHorizon, BigDecimal baselineSurvivalDays,
        List<Bucket> buckets, List<Capacity> capacity, List<Line> lines) {

    public record Bucket(int seq, String code, int fromDay, Integer toDay, BigDecimal baselineNet, BigDecimal baselineCumulative,
            BigDecimal stressedNet, BigDecimal stressedCumulative, BigDecimal liquidCapacity, BigDecimal fundingCapacity,
            BigDecimal baselineCapacity, BigDecimal position) {
    }

    public record Capacity(String kind, String reference, String label, BigDecimal sourceAmount, BigDecimal excludedAmount,
            BigDecimal haircut, BigDecimal shockHaircut, BigDecimal eligibleAmount, Integer daysToAccess, boolean frozen, String note) {
    }

    public record Line(int bucketSeq, String bucketCode, String source, String lineCode, Long productId, String productName,
            BigDecimal baselineAmount, BigDecimal stressedAmount, String shocks) {
    }
}
