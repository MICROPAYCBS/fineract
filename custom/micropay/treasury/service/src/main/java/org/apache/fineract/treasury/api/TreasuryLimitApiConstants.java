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

/** Resources, commands and parameters of the limits engine and the breach log. */
public final class TreasuryLimitApiConstants {

    private TreasuryLimitApiConstants() {}

    public static final String TREASURY_LIMIT_RESOURCE_NAME = "TREASURYLIMIT";
    public static final String TREASURY_BREACH_RESOURCE_NAME = "TREASURYBREACH";

    public static final String COMMAND_ACTIVATE = "activate";
    public static final String COMMAND_DEACTIVATE = "deactivate";
    public static final String COMMAND_EVALUATE = "evaluate";
    public static final String COMMAND_ACKNOWLEDGE = "acknowledge";
    public static final String COMMAND_RESOLVE = "resolve";

    public static final String CODE_PARAM = "code";
    public static final String NAME_PARAM = "name";
    public static final String DESCRIPTION_PARAM = "description";
    public static final String METRIC_PARAM = "metric";
    public static final String METRIC_KEY_PARAM = "metricKey";
    public static final String GAP_TYPE_PARAM = "gapType";
    public static final String GAP_BASIS_PARAM = "gapBasis";
    public static final String TOP_N_PARAM = "topN";
    public static final String SCOPE_TYPE_PARAM = "scopeType";
    public static final String SCOPE_ID_PARAM = "scopeId";
    public static final String CURRENCY_CODE_PARAM = "currencyCode";
    public static final String COMPARISON_PARAM = "comparison";
    public static final String WARNING_LEVEL_PARAM = "warningLevel";
    public static final String HARD_LEVEL_PARAM = "hardLevel";
    public static final String UNIT_PARAM = "unit";
    public static final String USE_OWN_LEVELS_PARAM = "useOwnLevels";
    public static final String EFFECTIVE_FROM_PARAM = "effectiveFrom";
    public static final String EFFECTIVE_TO_PARAM = "effectiveTo";
    public static final String NOTIFY_ROLE_ID_PARAM = "notifyRoleId";
    public static final String NOTE_PARAM = "note";
    public static final String COMMENT_PARAM = "comment";
    public static final String AS_OF_DATE_PARAM = "asOfDate";
    public static final String OFFICE_ID_PARAM = "officeId";
    public static final String RUN_ID_PARAM = "runId";
    public static final String DATE_FORMAT_PARAM = "dateFormat";
    public static final String LOCALE_PARAM = "locale";

    public static final Set<String> LIMIT_PARAMS = Set.of(CODE_PARAM, NAME_PARAM, DESCRIPTION_PARAM, METRIC_PARAM, METRIC_KEY_PARAM,
            GAP_TYPE_PARAM, GAP_BASIS_PARAM, TOP_N_PARAM, SCOPE_TYPE_PARAM, SCOPE_ID_PARAM, CURRENCY_CODE_PARAM, COMPARISON_PARAM,
            WARNING_LEVEL_PARAM, HARD_LEVEL_PARAM, UNIT_PARAM, USE_OWN_LEVELS_PARAM, EFFECTIVE_FROM_PARAM, EFFECTIVE_TO_PARAM,
            NOTIFY_ROLE_ID_PARAM, DATE_FORMAT_PARAM, LOCALE_PARAM);
    public static final Set<String> NOTE_PARAMS = Set.of(NOTE_PARAM, LOCALE_PARAM);
    public static final Set<String> COMMENT_PARAMS = Set.of(COMMENT_PARAM, LOCALE_PARAM);
    public static final Set<String> EVALUATE_PARAMS = Set.of(AS_OF_DATE_PARAM, CURRENCY_CODE_PARAM, OFFICE_ID_PARAM, RUN_ID_PARAM,
            DATE_FORMAT_PARAM, LOCALE_PARAM);
}
