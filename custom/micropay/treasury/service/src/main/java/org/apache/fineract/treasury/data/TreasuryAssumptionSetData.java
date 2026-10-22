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
import org.apache.fineract.treasury.domain.TreasuryAssumption;
import org.apache.fineract.treasury.domain.TreasuryAssumptionSet;

public record TreasuryAssumptionSetData(Long id, String code, String name, int version, LocalDate effectiveDate, String status,
        String description, Long createdBy, Long lastEditedBy, Long activatedBy, LocalDate activatedOnDate,
        List<AssumptionData> assumptions) {

    public record AssumptionData(Long id, String type, String source, Long productId, String bucketCode, Integer minDays, Integer maxDays,
            BigDecimal rate) {
    }

    public static TreasuryAssumptionSetData from(final TreasuryAssumptionSet set, final List<TreasuryAssumption> rows) {
        return new TreasuryAssumptionSetData(set.getId(), set.getCode(), set.getName(), set.getVersion(), set.getEffectiveDate(),
                set.getStatus().name(), set.getDescription(), set.getCreatedBy(), set.getLastEditedBy(), set.getActivatedBy(),
                set.getActivatedOnDate(), rows.stream().map(r -> new AssumptionData(r.getId(), r.getAssumptionType().name(),
                        r.getSource().name(), r.getProductId(), r.getBucketCode(), r.getMinDays(), r.getMaxDays(), r.getRate())).toList());
    }
}
