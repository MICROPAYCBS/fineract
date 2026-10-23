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

/** Resources, commands and parameters of stress scenarios and the contingency funding plan. */
public final class TreasuryStressApiConstants {

    private TreasuryStressApiConstants() {}

    public static final String TREASURY_SCENARIO_RESOURCE_NAME = "TREASURYSCENARIO";
    public static final String TREASURY_FUNDING_LINE_RESOURCE_NAME = "TREASURYFUNDINGLINE";
    public static final String TREASURY_CFP_TRIGGER_RESOURCE_NAME = "TREASURYCFPTRIGGER";
    public static final String TREASURY_CFP_ACTIVATION_RESOURCE_NAME = "TREASURYCFPACTIVATION";

    public static final String COMMAND_ACTIVATE = "activate";
    public static final String COMMAND_DEACTIVATE = "deactivate";
    public static final String COMMAND_FIRE = "fire";
    public static final String COMMAND_COMPLETE_ACTION = "complete-action";
    public static final String COMMAND_CLOSE = "close";

    public static final String CODE_PARAM = "code";
    public static final String NAME_PARAM = "name";
    public static final String DESCRIPTION_PARAM = "description";
    public static final String SCENARIO_TYPE_PARAM = "scenarioType";
    public static final String SEVERITY_PARAM = "severity";
    public static final String EFFECTIVE_DATE_PARAM = "effectiveDate";
    public static final String SHOCKS_PARAM = "shocks";
    public static final String COPY_FROM_ID_PARAM = "copyFromId";
    public static final String NOTE_PARAM = "note";
    public static final String COMMENT_PARAM = "comment";
    public static final String ACTION_ID_PARAM = "actionId";
    public static final String DATE_FORMAT_PARAM = "dateFormat";
    public static final String LOCALE_PARAM = "locale";

    public static final Set<String> SCENARIO_PARAMS = Set.of(CODE_PARAM, NAME_PARAM, DESCRIPTION_PARAM, SCENARIO_TYPE_PARAM, SEVERITY_PARAM,
            EFFECTIVE_DATE_PARAM, SHOCKS_PARAM, COPY_FROM_ID_PARAM, DATE_FORMAT_PARAM, LOCALE_PARAM);
    public static final Set<String> SHOCK_PARAMS = Set.of("type", "label", "source", "lineCode", "productId", "bucketCode", "side",
            "factor", "assetClass", "fundingSourceType", "fundingLineId");
    public static final Set<String> FUNDING_LINE_PARAMS = Set.of(CODE_PARAM, NAME_PARAM, DESCRIPTION_PARAM, "sourceType", "counterpartyId",
            "currencyCode", "committedAmount", "drawnAmount", "availableAmount", "daysToAccess", "expiryDate", "ownerUserId", "ownerName",
            "status", "includeInCapacity", DATE_FORMAT_PARAM, LOCALE_PARAM);
    public static final Set<String> TRIGGER_PARAMS = Set.of(CODE_PARAM, NAME_PARAM, DESCRIPTION_PARAM, "conditionType", "limitId", "metric",
            "level", "survivalDays", "scenarioCode", "stage", "notifyRoleId", "actions", LOCALE_PARAM);
    public static final Set<String> ACTION_PARAMS = Set.of("description", "ownerRoleId", "ownerUserId", "fundingLineId");
    public static final Set<String> NOTE_PARAMS = Set.of(NOTE_PARAM, LOCALE_PARAM);
    public static final Set<String> COMMENT_PARAMS = Set.of(COMMENT_PARAM, ACTION_ID_PARAM, LOCALE_PARAM);
}
