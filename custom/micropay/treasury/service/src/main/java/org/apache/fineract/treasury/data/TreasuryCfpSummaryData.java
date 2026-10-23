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
 * The contingency funding plan on a date: per currency the funding available by source and days to access, the worst
 * stressed gap and survival of each scenario's institution stress run, the coverage of the worst gap by the funding
 * available, and the open activations.
 */
public record TreasuryCfpSummaryData(LocalDate asOfDate, List<Currency> currencies, List<TreasuryCfpActivationData> openActivations) {

    /**
     * @param coverageRatio
     *            the funding available over the worst stressed gap of every scenario; null without a gap
     */
    public record Currency(String currencyCode, BigDecimal totalAvailable, BigDecimal capacityAvailable, List<Source> bySource,
            List<Scenario> scenarios, BigDecimal worstGap, BigDecimal coverageRatio) {
    }

    public record Source(String sourceType, int daysToAccess, BigDecimal amount, int lines) {
    }

    /**
     * @param worstGap
     *            the most negative stressed cumulative gap, as a positive amount; zero when it never turns negative
     */
    public record Scenario(Long runId, String scenarioCode, Integer scenarioVersion, BigDecimal survivalDays, boolean beyondHorizon,
            BigDecimal worstGap, String worstBucket, BigDecimal coverageRatio) {
    }
}
