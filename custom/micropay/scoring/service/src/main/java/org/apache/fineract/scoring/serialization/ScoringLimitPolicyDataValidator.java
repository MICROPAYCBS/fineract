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
package org.apache.fineract.scoring.serialization;

import static org.apache.fineract.scoring.api.ScoringApiConstants.SCORING_LIMIT_POLICY_RESOURCE_NAME;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.scoring.data.ScoringLimitPolicyRequest;
import org.apache.fineract.scoring.data.ScoringLimitPolicyRequest.BandRequest;
import org.apache.fineract.scoring.domain.ScoringMode;
import org.apache.fineract.scoring.input.ScoringCharacteristics;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScoringLimitPolicyDataValidator {

    private static final String CODE_PATTERN = "[A-Z0-9_]{1,100}";
    private static final List<String> MODES = Arrays.stream(ScoringMode.values()).map(Enum::name).toList();

    private final FromJsonHelper fromApiJsonHelper;

    /**
     * Also fills in the defaults: agents only, 30 days.
     */
    public ScoringLimitPolicyRequest validateAndParse(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final ScoringLimitPolicyRequest request = this.fromApiJsonHelper.fromJson(json, ScoringLimitPolicyRequest.class);
        if (request.getStaffOnly() == null) {
            request.setStaffOnly(Boolean.TRUE);
        }
        if (request.getValidDays() == null) {
            request.setValidDays(30);
        }
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(SCORING_LIMIT_POLICY_RESOURCE_NAME);
        validator.reset().parameter("code").value(request.getCode()).notBlank().matchesRegularExpression(CODE_PATTERN,
                "The parameter `code` must use capital letters, digits and underscores only");
        validator.reset().parameter("name").value(request.getName()).notBlank().notExceedingLengthOf(255);
        validator.reset().parameter("scorecardCode").value(request.getScorecardCode()).notBlank().notExceedingLengthOf(100);
        validator.reset().parameter("loanProductId").value(request.getLoanProductId()).notNull().longGreaterThanZero();
        validator.reset().parameter("mode").value(request.getMode()).notBlank().isOneOfTheseStringValues(MODES);
        validator.reset().parameter("officeId").value(request.getOfficeId()).ignoreIfNull().longGreaterThanZero();
        validator.reset().parameter("customerClassId").value(request.getCustomerClassId()).ignoreIfNull().longGreaterThanZero();
        validator.reset().parameter("validDays").value(request.getValidDays()).inMinMaxRange(1, 366);
        validator.reset().parameter("roundingUnit").value(request.getRoundingUnit()).ignoreIfNull().zeroOrPositiveAmount();
        if (request.getBaseCharacteristic() != null && !ScoringCharacteristics.isKnown(request.getBaseCharacteristic())) {
            validator.reset().parameter("baseCharacteristic").value(request.getBaseCharacteristic()).failWithCode("unknown");
        }
        final List<BandRequest> bands = request.getBands() == null ? List.of() : request.getBands();
        if (bands.isEmpty()) {
            validator.reset().parameter("bands").value(null).failWithCode("required");
        }
        final Set<String> grades = new HashSet<>();
        for (int i = 0; i < bands.size(); i++) {
            final BandRequest band = bands.get(i);
            final String prefix = "bands[" + i + "].";
            validator.reset().parameter(prefix + "grade").value(band.getGrade()).notBlank().notExceedingLengthOf(10);
            validator.reset().parameter(prefix + "maxAmount").value(band.getMaxAmount()).notNull().zeroOrPositiveAmount();
            validator.reset().parameter(prefix + "multiplier").value(band.getMultiplier()).ignoreIfNull().zeroOrPositiveAmount();
            if (band.getGrade() != null && !grades.add(band.getGrade().toUpperCase())) {
                validator.reset().parameter(prefix + "grade").value(band.getGrade()).failWithCode("duplicate");
            }
            if (band.getMultiplier() != null && request.getBaseCharacteristic() == null) {
                validator.reset().parameter(prefix + "multiplier").value(band.getMultiplier()).failWithCode("needs.base.characteristic");
            }
        }
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
        return request;
    }
}
