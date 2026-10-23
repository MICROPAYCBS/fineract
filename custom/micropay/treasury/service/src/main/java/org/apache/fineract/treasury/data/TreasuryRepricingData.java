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
 * A REPRICING run: the GL totals, the non-sensitive positions, per bucket RSA and RSL by rate type with the gaps, the
 * change in net interest income per rate shock, and the positions by bucket, source, side and rate type.
 */
public record TreasuryRepricingData(Long runId, LocalDate asOfDate, String currencyCode, Long officeId, Long ruleSetId,
        Integer ruleSetVersion, Long ladderRunId, BigDecimal totalAssets, BigDecimal totalLiabilities, BigDecimal rsa, BigDecimal rsl,
        BigDecimal nonSensitiveAssets, BigDecimal nonSensitiveLiabilities, BigDecimal cumulativeGapYear,
        BigDecimal cumulativeGapYearPercent, BigDecimal coreCapital, List<Bucket> buckets, List<Shock> shocks, List<Breakdown> breakdown) {

    public record Bucket(int seq, String code, int fromDay, Integer toDay, BigDecimal rsaFixed, BigDecimal rsaFloating,
            BigDecimal rsaAdministered, BigDecimal rslFixed, BigDecimal rslFloating, BigDecimal rslAdministered, BigDecimal rsa,
            BigDecimal rsl, BigDecimal gap, BigDecimal cumulativeGap, BigDecimal gapPercent, BigDecimal cumulativePercent,
            BigDecimal rsaRslRatio) {
    }

    /**
     * @param earPercent
     *            the loss as a percentage of core capital; null for a gain or without core capital
     */
    public record Shock(int shockBp, BigDecimal deltaNii, BigDecimal earPercent) {
    }

    /**
     * @param bucketCode
     *            null for non-sensitive positions
     */
    public record Breakdown(String bucketCode, String source, String side, String rateType, BigDecimal amount) {
    }
}
