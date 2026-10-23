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
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A stored run: LADDER runs with their bucket totals (every line from {@code GET /runs/{id}/lines}), RATIOS runs with
 * their ratios and components.
 *
 * @param opening
 *            LADDER: the opening stock of liquid treasury accounts, part of the first bucket
 * @param sourceRunId
 *            RATIOS: the ladder run; RETURN: the ratios run
 */
public record TreasuryRunData(Long id, String runType, LocalDate asOfDate, String currencyCode, Long officeId, Long ruleSetId,
        int ruleSetVersion, Long assumptionSetId, Integer assumptionSetVersion, String status, String notes, BigDecimal opening,
        BigDecimal beyondHorizon, Long sourceRunId, Long submittedBy, LocalDate submittedOnDate, Long createdBy,
        OffsetDateTime createdOnUtc, List<Line> buckets, List<Ratio> ratios) {

    /** A line: a source line in a bucket, or (source TOTAL) the bucket's totals with its net and cumulative gap. */
    public record Line(int bucketSeq, String bucketCode, String source, String lineCode, Long productId, String productName,
            BigDecimal inflow, BigDecimal outflow, BigDecimal contractual, BigDecimal behavioural, BigDecimal net, BigDecimal cumulativeGap,
            String rateType) {
    }

    public record Ratio(int seq, String code, String name, String direction, BigDecimal numerator, BigDecimal denominator,
            BigDecimal result, BigDecimal limit, BigDecimal warning, String status, boolean inReturn, List<Component> components) {
    }

    public record Component(String part, int seq, String type, String label, String reference, BigDecimal sourceAmount,
            BigDecimal excludedPledged, BigDecimal excludedMaturity, BigDecimal haircut, BigDecimal weight, int sign,
            BigDecimal eligibleAmount) {
    }
}
