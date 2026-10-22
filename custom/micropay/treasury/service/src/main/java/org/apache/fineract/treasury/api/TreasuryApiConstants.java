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

public final class TreasuryApiConstants {

    private TreasuryApiConstants() {}

    public static final String MODULE_ENABLED_PROPERTY = "fineract.module.treasury.enabled";

    public static final String TREASURY_ACCOUNT_RESOURCE_NAME = "TREASURYACCOUNT";

    public static final String COMMAND_CLOSE = "close";

    public static final String CODE_PARAM = "code";
    public static final String NAME_PARAM = "name";
    public static final String ACCOUNT_TYPE_PARAM = "accountType";
    public static final String GL_ACCOUNT_ID_PARAM = "glAccountId";
    public static final String OFFICE_ID_PARAM = "officeId";
    public static final String CURRENCY_CODE_PARAM = "currencyCode";
    public static final String COUNTERPARTY_NAME_PARAM = "counterpartyName";
    public static final String EXTERNAL_ACCOUNT_NUMBER_PARAM = "externalAccountNumber";
    public static final String MIN_BALANCE_PARAM = "minBalance";
    public static final String MAX_BALANCE_PARAM = "maxBalance";

    public static final Set<String> ACCOUNT_CREATE_PARAMS = Set.of(CODE_PARAM, NAME_PARAM, ACCOUNT_TYPE_PARAM, GL_ACCOUNT_ID_PARAM,
            OFFICE_ID_PARAM, CURRENCY_CODE_PARAM, COUNTERPARTY_NAME_PARAM, EXTERNAL_ACCOUNT_NUMBER_PARAM, MIN_BALANCE_PARAM,
            MAX_BALANCE_PARAM);

    /** The code is the account's business key and cannot be changed. */
    public static final Set<String> ACCOUNT_UPDATE_PARAMS = Set.of(NAME_PARAM, ACCOUNT_TYPE_PARAM, GL_ACCOUNT_ID_PARAM, OFFICE_ID_PARAM,
            CURRENCY_CODE_PARAM, COUNTERPARTY_NAME_PARAM, EXTERNAL_ACCOUNT_NUMBER_PARAM, MIN_BALANCE_PARAM, MAX_BALANCE_PARAM);
}
