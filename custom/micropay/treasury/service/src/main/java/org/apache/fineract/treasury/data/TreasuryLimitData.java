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

/** A limit with its version history. */
public record TreasuryLimitData(Long id, String code, String name, String description, String metric, String metricKey, String gapType,
        String gapBasis, Integer topN, String scopeType, Long scopeId, String currencyCode, String comparison, BigDecimal warningLevel,
        BigDecimal hardLevel, String unit, boolean useOwnLevels, String status, LocalDate effectiveFrom, LocalDate effectiveTo,
        Long notifyRoleId, int version, Long createdBy, OffsetDateTime createdOnUtc, Long lastModifiedBy, OffsetDateTime lastModifiedOnUtc,
        Long activatedBy, LocalDate activatedOnDate, List<History> history) {

    /**
     * @param snapshot
     *            the limit as JSON after the change
     */
    public record History(int version, String action, Long changedBy, OffsetDateTime changedOnUtc, String snapshot) {
    }
}
