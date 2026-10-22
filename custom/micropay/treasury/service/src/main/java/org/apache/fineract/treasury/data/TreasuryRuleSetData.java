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

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.treasury.domain.TreasuryBucket;
import org.apache.fineract.treasury.domain.TreasuryRuleSet;

public record TreasuryRuleSetData(Long id, String code, String name, int version, LocalDate effectiveDate, String status, boolean isDefault,
        String description, Long createdBy, Long lastEditedBy, Long activatedBy, LocalDate activatedOnDate, List<BucketData> buckets) {

    public record BucketData(int seq, String code, String label, int fromDay, Integer toDay) {
    }

    public static TreasuryRuleSetData from(final TreasuryRuleSet set, final List<TreasuryBucket> buckets) {
        return new TreasuryRuleSetData(set.getId(), set.getCode(), set.getName(), set.getVersion(), set.getEffectiveDate(),
                set.getStatus().name(), set.isDefaultSet(), set.getDescription(), set.getCreatedBy(), set.getLastEditedBy(),
                set.getActivatedBy(), set.getActivatedOnDate(), buckets.stream()
                        .map(b -> new BucketData(b.getSequenceNumber(), b.getCode(), b.getLabel(), b.getFromDay(), b.getToDay())).toList());
    }
}
