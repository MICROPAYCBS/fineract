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

public record TreasuryRuleSetData(Long id, String code, String name, int version, LocalDate effectiveDate, String status, boolean isDefault,
        String description, Long createdBy, Long lastEditedBy, Long activatedBy, LocalDate activatedOnDate, List<BucketData> buckets,
        List<RatioData> ratios, List<AssetClassData> assetClasses, String returnFrequency) {

    public record BucketData(int seq, String code, String label, int fromDay, Integer toDay) {
    }

    public record RatioData(int seq, String code, String name, String direction, BigDecimal limit, BigDecimal warning, boolean inReturn,
            String numeratorLabel, String denominatorLabel, String description, List<ComponentData> components) {
    }

    public record ComponentData(String part, int seq, String type, String label, Long glAccountId, String glCodePrefix, String glCodeFrom,
            String glCodeTo, String assetClass, String ladderMetric, Integer metricDays, int sign, BigDecimal weight) {
    }

    public record AssetClassData(String code, String label, boolean liquid, List<String> accountTypes, List<String> instrumentKinds,
            List<Long> dealTypeIds, Integer maxMaturityDays, BigDecimal haircut, List<String> classifications) {
    }
}
