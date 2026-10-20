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

import static org.apache.fineract.scoring.api.ScoringApiConstants.COMMENT_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.CURRENCY_CODE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.DEFAULT_AUTHORITY_TASK;
import static org.apache.fineract.scoring.api.ScoringApiConstants.DESCRIPTION_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.GRADES_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.LOAN_PRODUCT_ID_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.MAX_AMOUNT_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.MAX_EXPOSURE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.MIN_AMOUNT_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.MIN_EXPOSURE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.OUTCOMES_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.REASON_ID_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.SCORING_AUTHORITY_RULE_RESOURCE_NAME;
import static org.apache.fineract.scoring.api.ScoringApiConstants.SCORING_DECISION_RESOURCE_NAME;
import static org.apache.fineract.scoring.api.ScoringApiConstants.STAGE_CODE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.TASK_PERMISSION_CODE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.TO_OUTCOME_PARAM;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.scoring.data.ScoringAuthorityRuleRequest;
import org.apache.fineract.scoring.data.ScoringOverrideRequest;
import org.apache.fineract.scoring.domain.ScoringOutcome;
import org.springframework.stereotype.Component;

/**
 * Field-level validation of authority rule and override payloads.
 */
@Component
@RequiredArgsConstructor
public class ScoringAuthorityDataValidator {

    private static final String CODE_PATTERN = "[A-Za-z0-9_]{1,100}";
    private static final List<String> OUTCOMES = Arrays.stream(ScoringOutcome.values()).map(Enum::name).toList();

    private final FromJsonHelper fromApiJsonHelper;

    /**
     * Also fills in the default task, {@code APPROVE_LOAN}, and upper-cases codes.
     */
    public ScoringAuthorityRuleRequest validateAndParseRule(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final ScoringAuthorityRuleRequest request = this.fromApiJsonHelper.fromJson(json, ScoringAuthorityRuleRequest.class);
        if (StringUtils.isBlank(request.getTaskPermissionCode())) {
            request.setTaskPermissionCode(DEFAULT_AUTHORITY_TASK);
        }
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(SCORING_AUTHORITY_RULE_RESOURCE_NAME);

        validator.reset().parameter(TASK_PERMISSION_CODE_PARAM).value(request.getTaskPermissionCode()).matchesRegularExpression(CODE_PATTERN,
                "The parameter `taskPermissionCode` must be a permission code such as APPROVE_LOAN");
        validator.reset().parameter(STAGE_CODE_PARAM).value(request.getStageCode()).notBlank().notExceedingLengthOf(100);
        validator.reset().parameter(DESCRIPTION_PARAM).value(request.getDescription()).ignoreIfNull().notExceedingLengthOf(500);
        validator.reset().parameter(LOAN_PRODUCT_ID_PARAM).value(request.getLoanProductId()).ignoreIfNull().longGreaterThanZero();
        validator.reset().parameter(CURRENCY_CODE_PARAM).value(request.getCurrencyCode()).ignoreIfNull().matchesRegularExpression("[A-Za-z]{3}",
                "The parameter `currencyCode` must be a three-letter currency code");
        validateRange(validator, MIN_AMOUNT_PARAM, request.getMinAmount(), MAX_AMOUNT_PARAM, request.getMaxAmount());
        validateRange(validator, MIN_EXPOSURE_PARAM, request.getMinExposure(), MAX_EXPOSURE_PARAM, request.getMaxExposure());
        final List<String> grades = nullToEmpty(request.getGrades());
        for (int i = 0; i < grades.size(); i++) {
            validator.reset().parameter(GRADES_PARAM + "[" + i + "]").value(grades.get(i)).notBlank().notExceedingLengthOf(10);
        }
        final List<String> outcomes = nullToEmpty(request.getOutcomes());
        for (int i = 0; i < outcomes.size(); i++) {
            validator.reset().parameter(OUTCOMES_PARAM + "[" + i + "]").value(outcomes.get(i)).notBlank().isOneOfTheseStringValues(OUTCOMES);
        }
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
        request.setTaskPermissionCode(request.getTaskPermissionCode().toUpperCase());
        if (request.getCurrencyCode() != null) {
            request.setCurrencyCode(request.getCurrencyCode().toUpperCase());
        }
        return request;
    }

    public ScoringOverrideRequest validateAndParseOverride(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final ScoringOverrideRequest request = this.fromApiJsonHelper.fromJson(json, ScoringOverrideRequest.class);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(SCORING_DECISION_RESOURCE_NAME);
        validator.reset().parameter(TO_OUTCOME_PARAM).value(request.getToOutcome()).notBlank().isOneOfTheseStringValues(OUTCOMES);
        validator.reset().parameter(REASON_ID_PARAM).value(request.getReasonId()).notNull().longGreaterThanZero();
        validator.reset().parameter(COMMENT_PARAM).value(request.getComment()).ignoreIfNull().notExceedingLengthOf(1000);
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
        return request;
    }

    private static void validateRange(final DataValidatorBuilder validator, final String minParam, final BigDecimal min,
            final String maxParam, final BigDecimal max) {
        validator.reset().parameter(minParam).value(min).ignoreIfNull().zeroOrPositiveAmount();
        validator.reset().parameter(maxParam).value(max).ignoreIfNull().zeroOrPositiveAmount();
        if (min != null && max != null && min.compareTo(max) > 0) {
            validator.reset().parameter(minParam).value(min).failWithCode("greater.than." + maxParam, max);
        }
    }

    private static <T> List<T> nullToEmpty(final List<T> list) {
        return list == null ? List.of() : list;
    }
}
