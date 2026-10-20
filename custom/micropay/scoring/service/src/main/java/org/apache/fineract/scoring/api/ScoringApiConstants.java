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
package org.apache.fineract.scoring.api;

public final class ScoringApiConstants {

    private ScoringApiConstants() {}

    public static final String MODULE_ENABLED_PROPERTY = "fineract.module.scoring.enabled";

    public static final String SCORECARD_RESOURCE_NAME = "SCORECARD";
    public static final String SCORING_PRODUCT_CONFIG_RESOURCE_NAME = "SCORING_PRODUCT_CONFIG";
    public static final String SCORING_DECISION_RESOURCE_NAME = "SCORING_DECISION";
    public static final String SCORING_AUTHORITY_RULE_RESOURCE_NAME = "SCORING_AUTHORITY_RULE";

    public static final String OVERRIDE_REASON_CODE_NAME = "ScoringOverrideReason";
    public static final String DEFAULT_AUTHORITY_TASK = "APPROVE_LOAN";

    public static final String COMMAND_ACTIVATE = "activate";
    public static final String COMMAND_RETIRE = "retire";
    public static final String COMMAND_TEST = "test";
    public static final String COMMAND_SCORE = "score";
    public static final String COMMAND_OVERRIDE = "override";

    public static final String CODE_PARAM = "code";
    public static final String NAME_PARAM = "name";
    public static final String DESCRIPTION_PARAM = "description";
    public static final String TYPE_PARAM = "type";
    public static final String BASE_POINTS_PARAM = "basePoints";
    public static final String BINS_PARAM = "bins";
    public static final String GRADE_BANDS_PARAM = "gradeBands";
    public static final String RULES_PARAM = "rules";

    public static final String CHARACTERISTIC_PARAM = "characteristic";
    public static final String LOWER_PARAM = "lower";
    public static final String UPPER_PARAM = "upper";
    public static final String MATCH_VALUE_PARAM = "matchValue";
    public static final String MISSING_PARAM = "missing";
    public static final String POINTS_PARAM = "points";
    public static final String REASON_CODE_PARAM = "reasonCode";

    public static final String GRADE_PARAM = "grade";
    public static final String MIN_SCORE_PARAM = "minScore";
    public static final String MAX_SCORE_PARAM = "maxScore";
    public static final String EXPECTED_PD_PARAM = "expectedPd";
    public static final String OUTCOME_PARAM = "outcome";

    public static final String RULE_TYPE_PARAM = "ruleType";
    public static final String OPERATOR_PARAM = "operator";
    public static final String VALUE_PARAM = "value";

    public static final String SCORECARD_CODE_PARAM = "scorecardCode";
    public static final String MODE_PARAM = "mode";

    public static final String LOAN_ID_PARAM = "loanId";

    public static final String TO_OUTCOME_PARAM = "toOutcome";
    public static final String REASON_ID_PARAM = "reasonId";
    public static final String COMMENT_PARAM = "comment";

    public static final String TASK_PERMISSION_CODE_PARAM = "taskPermissionCode";
    public static final String STAGE_CODE_PARAM = "stageCode";
    public static final String LOAN_PRODUCT_ID_PARAM = "loanProductId";
    public static final String CURRENCY_CODE_PARAM = "currencyCode";
    public static final String MIN_AMOUNT_PARAM = "minAmount";
    public static final String MAX_AMOUNT_PARAM = "maxAmount";
    public static final String MIN_EXPOSURE_PARAM = "minExposure";
    public static final String MAX_EXPOSURE_PARAM = "maxExposure";
    public static final String GRADES_PARAM = "grades";
    public static final String OUTCOMES_PARAM = "outcomes";
    public static final String OVERRIDDEN_PARAM = "overridden";
}
