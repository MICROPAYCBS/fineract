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
    public static final String TREASURY_TRANSFER_RESOURCE_NAME = "TREASURYTRANSFER";
    public static final String TREASURY_SETTING_RESOURCE_NAME = "TREASURYSETTING";
    public static final String TREASURY_AGENT_FLOAT_RESOURCE_NAME = "TREASURYAGENTFLOAT";
    public static final String TREASURY_FLOAT_REQUEST_RESOURCE_NAME = "TREASURYFLOATREQUEST";

    public static final String COMMAND_CLOSE = "close";
    public static final String COMMAND_APPROVE = "approve";
    public static final String COMMAND_SEND = "send";
    public static final String COMMAND_RECEIVE = "receive";
    public static final String COMMAND_CANCEL = "cancel";
    public static final String COMMAND_EXECUTE = "execute";
    public static final String COMMAND_REVERSE = "reverse";

    /** Prefix of the journal transaction ids treasury posts, followed by the event and its id. */
    public static final String TRANSACTION_ID_PREFIX = "TR";

    public static final String FROM_ACCOUNT_ID_PARAM = "fromAccountId";
    public static final String TO_ACCOUNT_ID_PARAM = "toAccountId";
    public static final String AMOUNT_PARAM = "amount";
    public static final String TRANSFER_DATE_PARAM = "transferDate";
    public static final String TRANSACTION_DATE_PARAM = "transactionDate";
    public static final String REFERENCE_PARAM = "reference";
    public static final String DESCRIPTION_PARAM = "description";
    public static final String NOTE_PARAM = "note";
    public static final String REASON_PARAM = "reason";
    public static final String DATE_FORMAT_PARAM = "dateFormat";
    public static final String LOCALE_PARAM = "locale";

    public static final String CASH_IN_TRANSIT_GL_ACCOUNT_ID_PARAM = "cashInTransitGlAccountId";
    public static final String FLOAT_PAYMENT_TYPE_ID_PARAM = "floatPaymentTypeId";
    public static final String ALLOW_INSUFFICIENT_BALANCE_PARAM = "allowInsufficientBalance";

    public static final String CLIENT_ID_PARAM = "clientId";
    public static final String SAVINGS_ACCOUNT_ID_PARAM = "savingsAccountId";
    public static final String TREASURY_ACCOUNT_ID_PARAM = "treasuryAccountId";
    public static final String MIN_FLOAT_PARAM = "minFloat";
    public static final String MAX_FLOAT_PARAM = "maxFloat";
    public static final String REQUEST_TYPE_PARAM = "requestType";

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

    public static final Set<String> TRANSFER_CREATE_PARAMS = Set.of(FROM_ACCOUNT_ID_PARAM, TO_ACCOUNT_ID_PARAM, AMOUNT_PARAM,
            TRANSFER_DATE_PARAM, REFERENCE_PARAM, DESCRIPTION_PARAM, DATE_FORMAT_PARAM, LOCALE_PARAM);

    /** Body of approve, send, receive, cancel and execute; every field is optional. */
    public static final Set<String> ACTION_PARAMS = Set.of(TRANSACTION_DATE_PARAM, NOTE_PARAM, REASON_PARAM, DATE_FORMAT_PARAM,
            LOCALE_PARAM);

    public static final Set<String> SETTING_PARAMS = Set.of(CURRENCY_CODE_PARAM, CASH_IN_TRANSIT_GL_ACCOUNT_ID_PARAM,
            FLOAT_PAYMENT_TYPE_ID_PARAM, ALLOW_INSUFFICIENT_BALANCE_PARAM);

    public static final Set<String> AGENT_FLOAT_CREATE_PARAMS = Set.of(CLIENT_ID_PARAM, SAVINGS_ACCOUNT_ID_PARAM, TREASURY_ACCOUNT_ID_PARAM,
            MIN_FLOAT_PARAM, MAX_FLOAT_PARAM);

    public static final Set<String> AGENT_FLOAT_UPDATE_PARAMS = Set.of(TREASURY_ACCOUNT_ID_PARAM, MIN_FLOAT_PARAM, MAX_FLOAT_PARAM);

    public static final Set<String> FLOAT_REQUEST_CREATE_PARAMS = Set.of(REQUEST_TYPE_PARAM, AMOUNT_PARAM, NOTE_PARAM, LOCALE_PARAM);
}
