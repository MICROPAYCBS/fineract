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
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.scoring.domain.ScoringAuthorityRule;

public record ScoringAuthorityRuleData(Long id, String taskPermissionCode, String stageCode, String description, Long loanProductId,
        String currencyCode, BigDecimal minAmount, BigDecimal maxAmount, BigDecimal minExposure, BigDecimal maxExposure,
        List<String> grades, List<String> outcomes, Boolean overridden) {

    public static ScoringAuthorityRuleData from(final ScoringAuthorityRule rule) {
        return new ScoringAuthorityRuleData(rule.getId(), rule.getTaskPermissionCode(), rule.getStageCode(), rule.getDescription(),
                rule.getLoanProductId(), rule.getCurrencyCode(), rule.getMinAmount(), rule.getMaxAmount(), rule.getMinExposure(),
                rule.getMaxExposure(), split(rule.getGrades()), split(rule.getOutcomes()), rule.getOverridden());
    }

    private static List<String> split(final String csv) {
        return StringUtils.isBlank(csv) ? List.of() : Arrays.asList(csv.split(","));
    }
}
