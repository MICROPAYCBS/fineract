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
package org.apache.fineract.scoring.data;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.scoring.domain.ScoringLimitPolicy;

public record ScoringLimitPolicyData(Long id, String code, String name, String scorecardCode, Long loanProductId, String mode,
        boolean staffOnly, Long officeId, Long customerClassId, Integer validDays, String baseCharacteristic, BigDecimal roundingUnit,
        List<Band> bands) {

    public record Band(String grade, BigDecimal multiplier, BigDecimal maxAmount) {}

    public static ScoringLimitPolicyData from(final ScoringLimitPolicy policy) {
        return new ScoringLimitPolicyData(policy.getId(), policy.getCode(), policy.getName(), policy.getScorecardCode(),
                policy.getLoanProductId(), policy.getMode().name(), policy.isStaffOnly(), policy.getOfficeId(), policy.getCustomerClassId(),
                policy.getValidDays(), policy.getBaseCharacteristic(), policy.getRoundingUnit(),
                policy.getBands().stream().map(band -> new Band(band.getGrade(), band.getMultiplier(), band.getMaxAmount())).toList());
    }
}
