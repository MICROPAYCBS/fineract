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

/** Resources, commands and parameters of FX rates, revaluation, currency positions and core capital. */
public final class TreasuryFxApiConstants {

    private TreasuryFxApiConstants() {}

    public static final String TREASURY_FX_RATE_RESOURCE_NAME = "TREASURYFXRATE";
    public static final String TREASURY_FX_REVAL_CONFIG_RESOURCE_NAME = "TREASURYFXREVALCONFIG";
    public static final String TREASURY_FX_REVALUATION_RESOURCE_NAME = "TREASURYFXREVALUATION";
    public static final String TREASURY_FX_POSITION_RESOURCE_NAME = "TREASURYFXPOSITION";
    public static final String TREASURY_CORE_CAPITAL_RESOURCE_NAME = "TREASURYCORECAPITAL";
    public static final String TREASURY_TENANT_SETTING_RESOURCE_NAME = "TREASURYTENANTSETTING";

    public static final String COMMAND_CANCEL = "cancel";
    public static final String COMMAND_RUN = "run";
    public static final String COMMAND_REVERSE = "reverse";

    public static final String BASE_CURRENCY_PARAM = "baseCurrency";
    public static final String QUOTE_CURRENCY_PARAM = "quoteCurrency";
    public static final String RATE_DATE_PARAM = "rateDate";
    public static final String RATE_TYPE_PARAM = "rateType";
    public static final String RATE_PARAM = "rate";
    public static final String SOURCE_PARAM = "source";
    public static final String RATES_PARAM = "rates";
    public static final String NOTE_PARAM = "note";
    public static final String GL_ACCOUNT_ID_PARAM = "glAccountId";
    public static final String CURRENCY_CODE_PARAM = "currencyCode";
    public static final String ADJUSTMENT_GL_ACCOUNT_ID_PARAM = "adjustmentGlAccountId";
    public static final String GAIN_GL_ACCOUNT_ID_PARAM = "gainGlAccountId";
    public static final String LOSS_GL_ACCOUNT_ID_PARAM = "lossGlAccountId";
    public static final String ACTIVE_PARAM = "active";
    public static final String AS_OF_DATE_PARAM = "asOfDate";
    public static final String EFFECTIVE_DATE_PARAM = "effectiveDate";
    public static final String AMOUNT_PARAM = "amount";
    public static final String DATE_FORMAT_PARAM = "dateFormat";
    public static final String LOCALE_PARAM = "locale";
    public static final String FX_MAX_RATE_AGE_DAYS_PARAM = "fxMaxRateAgeDays";
    public static final String NOP_CAPITAL_BASIS_PARAM = "nopCapitalBasis";

    public static final Set<String> RATE_PARAMS = Set.of(BASE_CURRENCY_PARAM, QUOTE_CURRENCY_PARAM, RATE_DATE_PARAM, RATE_TYPE_PARAM,
            RATE_PARAM, SOURCE_PARAM, NOTE_PARAM, RATES_PARAM, DATE_FORMAT_PARAM, LOCALE_PARAM);
    public static final Set<String> BATCH_RATE_PARAMS = Set.of(BASE_CURRENCY_PARAM, QUOTE_CURRENCY_PARAM, RATE_DATE_PARAM, RATE_TYPE_PARAM,
            RATE_PARAM, SOURCE_PARAM, NOTE_PARAM);
    public static final Set<String> CONFIG_PARAMS = Set.of(GL_ACCOUNT_ID_PARAM, CURRENCY_CODE_PARAM, ADJUSTMENT_GL_ACCOUNT_ID_PARAM,
            GAIN_GL_ACCOUNT_ID_PARAM, LOSS_GL_ACCOUNT_ID_PARAM, ACTIVE_PARAM, NOTE_PARAM, LOCALE_PARAM);
    public static final Set<String> RUN_PARAMS = Set.of(AS_OF_DATE_PARAM, CURRENCY_CODE_PARAM, DATE_FORMAT_PARAM, LOCALE_PARAM);
    public static final Set<String> CORE_CAPITAL_PARAMS = Set.of(EFFECTIVE_DATE_PARAM, AMOUNT_PARAM, NOTE_PARAM, DATE_FORMAT_PARAM,
            LOCALE_PARAM);
    public static final Set<String> NOTE_PARAMS = Set.of(NOTE_PARAM, LOCALE_PARAM);
    public static final String CLIENT_TYPE_PARAM = "clientType";
    public static final String EMONEY_SOURCE_PARAM = "emoneySource";
    public static final String EMONEY_CUSTOMER_PRODUCTS_PARAM = "emoneyCustomerProductIds";
    public static final String EMONEY_AGENT_PRODUCTS_PARAM = "emoneyAgentProductIds";
    public static final String EMONEY_OTHER_PRODUCTS_PARAM = "emoneyOtherProductIds";
    public static final String TRUST_INTEREST_GL_PARAM = "trustInterestGlAccountId";
    public static final Set<String> TENANT_SETTING_PARAMS = Set.of(BASE_CURRENCY_PARAM, FX_MAX_RATE_AGE_DAYS_PARAM, NOP_CAPITAL_BASIS_PARAM,
            CLIENT_TYPE_PARAM, EMONEY_SOURCE_PARAM, EMONEY_CUSTOMER_PRODUCTS_PARAM, EMONEY_AGENT_PRODUCTS_PARAM,
            EMONEY_OTHER_PRODUCTS_PARAM, TRUST_INTEREST_GL_PARAM, LOCALE_PARAM);
}
