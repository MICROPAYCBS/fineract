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
package org.apache.fineract.treasury.api;

import java.util.Set;

/**
 * Resources, commands and parameters of the liquidity engine: rule sets and their buckets, behavioural assumption sets,
 * ladder items and runs.
 */
public final class TreasuryLiquidityApiConstants {

    private TreasuryLiquidityApiConstants() {}

    public static final String TREASURY_RULE_SET_RESOURCE_NAME = "TREASURYRULESET";
    public static final String TREASURY_ASSUMPTION_SET_RESOURCE_NAME = "TREASURYASSUMPTIONSET";
    public static final String TREASURY_LADDER_ITEM_RESOURCE_NAME = "TREASURYLADDERITEM";
    public static final String TREASURY_RUN_RESOURCE_NAME = "TREASURYRUN";

    public static final String COMMAND_ACTIVATE = "activate";

    public static final String CODE_PARAM = "code";
    public static final String NAME_PARAM = "name";
    public static final String EFFECTIVE_DATE_PARAM = "effectiveDate";
    public static final String DESCRIPTION_PARAM = "description";
    public static final String IS_DEFAULT_PARAM = "isDefault";
    public static final String BUCKETS_PARAM = "buckets";
    public static final String LABEL_PARAM = "label";
    public static final String FROM_DAY_PARAM = "fromDay";
    public static final String TO_DAY_PARAM = "toDay";
    public static final String ASSUMPTIONS_PARAM = "assumptions";
    public static final String TYPE_PARAM = "type";
    public static final String SOURCE_PARAM = "source";
    public static final String PRODUCT_ID_PARAM = "productId";
    public static final String BUCKET_CODE_PARAM = "bucketCode";
    public static final String MIN_DAYS_PARAM = "minDays";
    public static final String MAX_DAYS_PARAM = "maxDays";
    public static final String RATE_PARAM = "rate";
    public static final String DIRECTION_PARAM = "direction";
    public static final String AMOUNT_PARAM = "amount";
    public static final String CURRENCY_CODE_PARAM = "currencyCode";
    public static final String OFFICE_ID_PARAM = "officeId";
    public static final String ONE_OFF_DATE_PARAM = "oneOffDate";
    public static final String DAY_OF_MONTH_PARAM = "dayOfMonth";
    public static final String ACTIVE_FROM_PARAM = "activeFrom";
    public static final String ACTIVE_TO_PARAM = "activeTo";
    public static final String AS_OF_DATE_PARAM = "asOfDate";
    public static final String RULE_SET_ID_PARAM = "ruleSetId";
    public static final String ASSUMPTION_SET_ID_PARAM = "assumptionSetId";
    public static final String NOTE_PARAM = "note";
    public static final String COMMAND_SUBMIT = "submit";
    public static final String COPY_FROM_ID_PARAM = "copyFromId";
    public static final String RATIOS_PARAM = "ratios";
    public static final String ASSET_CLASSES_PARAM = "assetClasses";
    public static final String SOURCE_RUN_ID_PARAM = "sourceRunId";
    public static final String SCENARIO_ID_PARAM = "scenarioId";
    public static final String SHOCKS_PARAM = "shocks";
    public static final String DATE_FORMAT_PARAM = "dateFormat";
    public static final String LOCALE_PARAM = "locale";

    public static final Set<String> RULE_SET_PARAMS = Set.of(CODE_PARAM, NAME_PARAM, EFFECTIVE_DATE_PARAM, DESCRIPTION_PARAM,
            IS_DEFAULT_PARAM, BUCKETS_PARAM, COPY_FROM_ID_PARAM, RATIOS_PARAM, ASSET_CLASSES_PARAM, DATE_FORMAT_PARAM, LOCALE_PARAM);
    public static final Set<String> RATIO_PARAMS = Set.of("code", "name", "direction", "limit", "warning", "inReturn", "numeratorLabel",
            "denominatorLabel", "description", "components");
    public static final Set<String> COMPONENT_PARAMS = Set.of("part", "type", "label", "glAccountId", "glCodePrefix", "glCodeFrom",
            "glCodeTo", "assetClass", "ladderMetric", "metricDays", "sign", "weight");
    public static final Set<String> ASSET_CLASS_PARAMS = Set.of("code", "label", "liquid", "accountTypes", "instrumentKinds", "dealTypeIds",
            "maxMaturityDays", "haircut");
    public static final Set<String> BUCKET_PARAMS = Set.of(CODE_PARAM, LABEL_PARAM, FROM_DAY_PARAM, TO_DAY_PARAM);
    public static final Set<String> ASSUMPTION_SET_PARAMS = Set.of(CODE_PARAM, NAME_PARAM, EFFECTIVE_DATE_PARAM, DESCRIPTION_PARAM,
            ASSUMPTIONS_PARAM, DATE_FORMAT_PARAM, LOCALE_PARAM);
    public static final Set<String> ASSUMPTION_PARAMS = Set.of(TYPE_PARAM, SOURCE_PARAM, PRODUCT_ID_PARAM, BUCKET_CODE_PARAM,
            MIN_DAYS_PARAM, MAX_DAYS_PARAM, RATE_PARAM);
    public static final Set<String> LADDER_ITEM_PARAMS = Set.of(NAME_PARAM, DIRECTION_PARAM, AMOUNT_PARAM, CURRENCY_CODE_PARAM,
            OFFICE_ID_PARAM, ONE_OFF_DATE_PARAM, DAY_OF_MONTH_PARAM, ACTIVE_FROM_PARAM, ACTIVE_TO_PARAM, DATE_FORMAT_PARAM, LOCALE_PARAM);
    public static final Set<String> RUN_PARAMS = Set.of(TYPE_PARAM, AS_OF_DATE_PARAM, CURRENCY_CODE_PARAM, OFFICE_ID_PARAM,
            RULE_SET_ID_PARAM, ASSUMPTION_SET_ID_PARAM, SOURCE_RUN_ID_PARAM, SCENARIO_ID_PARAM, SHOCKS_PARAM, DATE_FORMAT_PARAM,
            LOCALE_PARAM);
    public static final Set<String> ACTIVATE_PARAMS = Set.of(NOTE_PARAM, LOCALE_PARAM);
}
