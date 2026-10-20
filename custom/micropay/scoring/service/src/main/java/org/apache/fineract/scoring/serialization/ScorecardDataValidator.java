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

import static org.apache.fineract.scoring.api.ScoringApiConstants.BASE_POINTS_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.BINS_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.CHARACTERISTIC_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.CODE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.DESCRIPTION_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.EXPECTED_PD_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.GRADE_BANDS_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.GRADE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.MATCH_VALUE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.MAX_SCORE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.MIN_SCORE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.MODE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.NAME_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.OPERATOR_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.OUTCOME_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.POINTS_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.REASON_CODE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.RULES_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.RULE_TYPE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.SCORECARD_CODE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.SCORECARD_RESOURCE_NAME;
import static org.apache.fineract.scoring.api.ScoringApiConstants.SCORING_PRODUCT_CONFIG_RESOURCE_NAME;
import static org.apache.fineract.scoring.api.ScoringApiConstants.TYPE_PARAM;
import static org.apache.fineract.scoring.api.ScoringApiConstants.VALUE_PARAM;

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
import org.apache.fineract.scoring.data.ScorecardRequest;
import org.apache.fineract.scoring.data.ScorecardRequest.BinRequest;
import org.apache.fineract.scoring.data.ScorecardRequest.GradeBandRequest;
import org.apache.fineract.scoring.data.ScorecardRequest.RuleRequest;
import org.apache.fineract.scoring.data.ScoringProductConfigRequest;
import org.apache.fineract.scoring.domain.RuleOperator;
import org.apache.fineract.scoring.domain.ScorecardType;
import org.apache.fineract.scoring.domain.ScoringMode;
import org.apache.fineract.scoring.domain.ScoringOutcome;
import org.apache.fineract.scoring.domain.ScoringRuleType;
import org.apache.fineract.scoring.input.ScoringCharacteristics;
import org.springframework.stereotype.Component;

/**
 * Field-level validation of scorecard and product configuration payloads. Whole-scorecard rules (bands must not
 * overlap, every characteristic must be known) are checked again at activation by the write service.
 */
@Component
@RequiredArgsConstructor
public class ScorecardDataValidator {

    private static final String CODE_PATTERN = "[A-Z0-9_]{1,100}";
    private static final List<String> TYPES = names(ScorecardType.values());
    private static final List<String> OUTCOMES = names(ScoringOutcome.values());
    private static final List<String> RULE_OUTCOMES = List.of(ScoringOutcome.REFER.name(), ScoringOutcome.DECLINE.name());
    private static final List<String> RULE_TYPES = names(ScoringRuleType.values());
    private static final List<String> OPERATORS = names(RuleOperator.values());
    private static final List<String> MODES = names(ScoringMode.values());

    private final FromJsonHelper fromApiJsonHelper;

    public ScorecardRequest validateAndParseScorecard(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final ScorecardRequest request = this.fromApiJsonHelper.fromJson(json, ScorecardRequest.class);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(SCORECARD_RESOURCE_NAME);

        validator.reset().parameter(CODE_PARAM).value(request.getCode()).notBlank().matchesRegularExpression(CODE_PATTERN,
                "The parameter `code` must use capital letters, digits and underscores only");
        validator.reset().parameter(NAME_PARAM).value(request.getName()).notBlank().notExceedingLengthOf(255);
        validator.reset().parameter(DESCRIPTION_PARAM).value(request.getDescription()).ignoreIfNull().notExceedingLengthOf(1000);
        validator.reset().parameter(TYPE_PARAM).value(request.getType()).notBlank().isOneOfTheseStringValues(TYPES);
        validator.reset().parameter(BASE_POINTS_PARAM).value(request.getBasePoints()).notNull();

        final List<BinRequest> bins = nullToEmpty(request.getBins());
        for (int i = 0; i < bins.size(); i++) {
            validateBin(bins.get(i), BINS_PARAM + "[" + i + "].", validator);
        }
        final List<GradeBandRequest> bands = nullToEmpty(request.getGradeBands());
        for (int i = 0; i < bands.size(); i++) {
            validateBand(bands.get(i), GRADE_BANDS_PARAM + "[" + i + "].", validator);
        }
        final List<RuleRequest> rules = nullToEmpty(request.getRules());
        for (int i = 0; i < rules.size(); i++) {
            validateRule(rules.get(i), RULES_PARAM + "[" + i + "].", validator);
        }

        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
        return request;
    }

    public ScoringProductConfigRequest validateAndParseProductConfig(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final ScoringProductConfigRequest request = this.fromApiJsonHelper.fromJson(json, ScoringProductConfigRequest.class);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(SCORING_PRODUCT_CONFIG_RESOURCE_NAME);
        validator.reset().parameter(SCORECARD_CODE_PARAM).value(request.getScorecardCode()).notBlank().notExceedingLengthOf(100);
        validator.reset().parameter(MODE_PARAM).value(request.getMode()).notBlank().isOneOfTheseStringValues(MODES);
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
        return request;
    }

    private static void validateBin(final BinRequest bin, final String prefix, final DataValidatorBuilder validator) {
        validator.reset().parameter(prefix + CHARACTERISTIC_PARAM).value(bin.getCharacteristic()).notBlank();
        if (StringUtils.isNotBlank(bin.getCharacteristic()) && !ScoringCharacteristics.isKnown(bin.getCharacteristic())) {
            validator.reset().parameter(prefix + CHARACTERISTIC_PARAM).value(bin.getCharacteristic()).failWithCode("unknown",
                    bin.getCharacteristic());
        }
        validator.reset().parameter(prefix + POINTS_PARAM).value(bin.getPoints()).notNull();
        validator.reset().parameter(prefix + MATCH_VALUE_PARAM).value(bin.getMatchValue()).ignoreIfNull().notExceedingLengthOf(100);
        validator.reset().parameter(prefix + REASON_CODE_PARAM).value(bin.getReasonCode()).ignoreIfNull().notExceedingLengthOf(50);
        if (bin.getLower() != null && bin.getUpper() != null && bin.getLower().compareTo(bin.getUpper()) >= 0) {
            validator.reset().parameter(prefix + "lower").value(bin.getLower()).failWithCode("not.less.than.upper", bin.getUpper());
        }
        final boolean isMissing = Boolean.TRUE.equals(bin.getMissing());
        if (isMissing && (bin.getMatchValue() != null || bin.getLower() != null || bin.getUpper() != null)) {
            validator.reset().parameter(prefix + "missing").value(bin.getMissing()).failWithCode("cannot.combine.with.range.or.value");
        }
        if (bin.getMatchValue() != null && (bin.getLower() != null || bin.getUpper() != null)) {
            validator.reset().parameter(prefix + MATCH_VALUE_PARAM).value(bin.getMatchValue()).failWithCode("cannot.combine.with.range");
        }
    }

    private static void validateBand(final GradeBandRequest band, final String prefix, final DataValidatorBuilder validator) {
        validator.reset().parameter(prefix + GRADE_PARAM).value(band.getGrade()).notBlank().notExceedingLengthOf(10);
        validator.reset().parameter(prefix + MIN_SCORE_PARAM).value(band.getMinScore()).notNull();
        validator.reset().parameter(prefix + MAX_SCORE_PARAM).value(band.getMaxScore()).notNull();
        validator.reset().parameter(prefix + OUTCOME_PARAM).value(band.getOutcome()).notBlank().isOneOfTheseStringValues(OUTCOMES);
        validator.reset().parameter(prefix + EXPECTED_PD_PARAM).value(band.getExpectedPd()).ignoreIfNull().inMinAndMaxAmountRange(BigDecimal.ZERO,
                BigDecimal.ONE);
        if (band.getMinScore() != null && band.getMaxScore() != null && band.getMinScore().compareTo(band.getMaxScore()) > 0) {
            validator.reset().parameter(prefix + MIN_SCORE_PARAM).value(band.getMinScore()).failWithCode("greater.than.max.score",
                    band.getMaxScore());
        }
    }

    private static void validateRule(final RuleRequest rule, final String prefix, final DataValidatorBuilder validator) {
        validator.reset().parameter(prefix + RULE_TYPE_PARAM).value(rule.getRuleType()).notBlank().isOneOfTheseStringValues(RULE_TYPES);
        validator.reset().parameter(prefix + OUTCOME_PARAM).value(rule.getOutcome()).notBlank().isOneOfTheseStringValues(RULE_OUTCOMES);
        validator.reset().parameter(prefix + REASON_CODE_PARAM).value(rule.getReasonCode()).notBlank().notExceedingLengthOf(50);
        if (!ScoringRuleType.CHARACTERISTIC.name().equals(rule.getRuleType())) {
            return;
        }
        validator.reset().parameter(prefix + CHARACTERISTIC_PARAM).value(rule.getCharacteristic()).notBlank();
        if (StringUtils.isNotBlank(rule.getCharacteristic()) && !ScoringCharacteristics.isKnown(rule.getCharacteristic())) {
            validator.reset().parameter(prefix + CHARACTERISTIC_PARAM).value(rule.getCharacteristic()).failWithCode("unknown",
                    rule.getCharacteristic());
        }
        validator.reset().parameter(prefix + OPERATOR_PARAM).value(rule.getOperator()).notBlank().isOneOfTheseStringValues(OPERATORS);
        if (!RuleOperator.MISSING.name().equals(rule.getOperator())) {
            validator.reset().parameter(prefix + VALUE_PARAM).value(rule.getValue()).notBlank().notExceedingLengthOf(100);
        }
    }

    private static <T> List<T> nullToEmpty(final List<T> list) {
        return list == null ? List.of() : list;
    }

    private static List<String> names(final Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
