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
 * A stored run with its bucket totals; {@code lines} only from {@code GET /runs/{id}/lines}.
 *
 * @param opening
 *            the opening stock of liquid treasury accounts, part of the first bucket
 */
public record TreasuryRunData(Long id, String runType, LocalDate asOfDate, String currencyCode, Long officeId, Long ruleSetId,
        int ruleSetVersion, Long assumptionSetId, Integer assumptionSetVersion, String status, String notes, BigDecimal opening,
        BigDecimal beyondHorizon, Long createdBy, OffsetDateTime createdOnUtc, List<Line> buckets) {

    /** A line: a source line in a bucket, or (source TOTAL) the bucket's totals with its net and cumulative gap. */
    public record Line(int bucketSeq, String bucketCode, String source, String lineCode, Long productId, String productName,
            BigDecimal inflow, BigDecimal outflow, BigDecimal contractual, BigDecimal behavioural, BigDecimal net,
            BigDecimal cumulativeGap) {
    }
}
