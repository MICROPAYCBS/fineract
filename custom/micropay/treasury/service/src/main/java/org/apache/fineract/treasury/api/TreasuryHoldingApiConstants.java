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
 * Resources, commands and parameters of holdings: pledges, market prices, fair value revaluation and expected credit
 * losses.
 */
public final class TreasuryHoldingApiConstants {

    private TreasuryHoldingApiConstants() {}

    public static final String TREASURY_PRICE_RESOURCE_NAME = "TREASURYPRICE";
    public static final String TREASURY_ECL_PARAMETER_RESOURCE_NAME = "TREASURYECLPARAMETER";

    public static final String COMMAND_PLEDGE = "pledge";
    public static final String COMMAND_RELEASE_PLEDGE = "releasePledge";
    public static final String COMMAND_SET_ECL_STAGE = "setEclStage";
    public static final String COMMAND_RECALCULATE_ECL = "recalculateEcl";

    public static final String PLEDGEE_COUNTERPARTY_ID_PARAM = "pledgeeCounterpartyId";
    public static final String PURPOSE_PARAM = "purpose";
    public static final String AMOUNT_PARAM = "amount";
    public static final String LINKED_DEAL_ID_PARAM = "linkedDealId";
    public static final String START_DATE_PARAM = "startDate";
    public static final String EXPECTED_END_DATE_PARAM = "expectedEndDate";
    public static final String REASON_PARAM = "reason";
    public static final String NOTE_PARAM = "note";
    public static final String PLEDGE_ID_PARAM = "pledgeId";
    public static final String RELEASE_DATE_PARAM = "releaseDate";

    public static final String STAGE_PARAM = "stage";
    public static final String MANUAL_PD_PARAM = "manualPd";
    public static final String MANUAL_LGD_PARAM = "manualLgd";
    public static final String MANUAL_ECL_PARAM = "manualEcl";
    public static final String MANUAL_ECL_REASON_PARAM = "manualEclReason";
    public static final String CLEAR_OVERRIDES_PARAM = "clearOverrides";

    public static final String PRICES_PARAM = "prices";
    public static final String INSTRUMENT_ID_PARAM = "instrumentId";
    public static final String PRICE_DATE_PARAM = "priceDate";
    public static final String CLEAN_PRICE_PARAM = "cleanPrice";
    public static final String YIELD_PARAM = "yield";
    public static final String SOURCE_PARAM = "source";

    public static final String RATING_CODE_PARAM = "ratingCode";
    public static final String PD_12_MONTH_PARAM = "pd12Month";
    public static final String PD_LIFETIME_PARAM = "pdLifetime";
    public static final String LGD_PARAM = "lgd";

    public static final String DATE_FORMAT_PARAM = "dateFormat";
    public static final String LOCALE_PARAM = "locale";

    public static final Set<String> PLEDGE_PARAMS = Set.of(PLEDGEE_COUNTERPARTY_ID_PARAM, PURPOSE_PARAM, AMOUNT_PARAM, LINKED_DEAL_ID_PARAM,
            START_DATE_PARAM, EXPECTED_END_DATE_PARAM, REASON_PARAM, DATE_FORMAT_PARAM, LOCALE_PARAM);

    public static final Set<String> RELEASE_PLEDGE_PARAMS = Set.of(PLEDGE_ID_PARAM, RELEASE_DATE_PARAM, REASON_PARAM, DATE_FORMAT_PARAM,
            LOCALE_PARAM);

    public static final Set<String> ECL_STAGE_PARAMS = Set.of(STAGE_PARAM, REASON_PARAM, MANUAL_PD_PARAM, MANUAL_LGD_PARAM,
            MANUAL_ECL_PARAM, MANUAL_ECL_REASON_PARAM, CLEAR_OVERRIDES_PARAM, LOCALE_PARAM);

    public static final Set<String> RECALCULATE_ECL_PARAMS = Set.of(NOTE_PARAM, LOCALE_PARAM);

    /** One price, at the top level or as an element of {@code prices}. */
    public static final Set<String> PRICE_PARAMS = Set.of(INSTRUMENT_ID_PARAM, PRICE_DATE_PARAM, CLEAN_PRICE_PARAM, YIELD_PARAM,
            SOURCE_PARAM, DATE_FORMAT_PARAM, LOCALE_PARAM);

    public static final Set<String> PRICE_BATCH_PARAMS = Set.of(PRICES_PARAM, DATE_FORMAT_PARAM, LOCALE_PARAM);

    public static final Set<String> ECL_PARAMETER_PARAMS = Set.of(RATING_CODE_PARAM, STAGE_PARAM, PD_12_MONTH_PARAM, PD_LIFETIME_PARAM,
            LGD_PARAM, LOCALE_PARAM);
}
