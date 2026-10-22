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
 * Resources, commands and parameters of counterparties, deal types and deals.
 */
public final class TreasuryDealApiConstants {

    private TreasuryDealApiConstants() {}

    public static final String TREASURY_COUNTERPARTY_RESOURCE_NAME = "TREASURYCOUNTERPARTY";
    public static final String TREASURY_DEAL_TYPE_RESOURCE_NAME = "TREASURYDEALTYPE";
    public static final String TREASURY_DEAL_RESOURCE_NAME = "TREASURYDEAL";

    /** Needed, besides APPROVE_TREASURYDEAL, to approve a deal captured above its counterparty's limit. */
    public static final String APPROVE_OVERLIMIT_PERMISSION = "APPROVE_TREASURYDEAL_OVERLIMIT";
    /** Code of the optional counterparty rating values. */
    public static final String COUNTERPARTY_RATING_CODE = "TreasuryCounterpartyRating";

    public static final String COMMAND_CONFIRM = "confirm";
    public static final String COMMAND_REJECT = "reject";
    public static final String COMMAND_SETTLE = "settle";
    public static final String COMMAND_SETTLE_CASHFLOW = "settleCashflow";
    public static final String COMMAND_TERMINATE = "terminate";
    public static final String COMMAND_ROLLOVER = "rollover";

    public static final String NAME_PARAM = "name";
    public static final String SHORT_CODE_PARAM = "shortCode";
    public static final String COUNTERPARTY_TYPE_PARAM = "counterpartyType";
    public static final String RATING_PARAM = "rating";
    public static final String RATING_CODE_VALUE_ID_PARAM = "ratingCodeValueId";
    public static final String EXPOSURE_LIMIT_PARAM = "exposureLimit";
    public static final String LIMIT_CURRENCY_CODE_PARAM = "limitCurrencyCode";
    public static final String WARNING_PERCENT_PARAM = "warningPercent";

    public static final String CODE_PARAM = "code";
    public static final String INSTRUMENT_KIND_PARAM = "instrumentKind";
    public static final String INTEREST_METHOD_PARAM = "interestMethod";
    public static final String DEFAULT_DAY_COUNT_PARAM = "defaultDayCount";
    public static final String DEFAULT_FREQUENCY_PARAM = "defaultFrequency";
    public static final String PRINCIPAL_GL_PARAM = "principalGlAccountId";
    public static final String INTEREST_RECEIVABLE_GL_PARAM = "interestReceivableGlAccountId";
    public static final String INTEREST_PAYABLE_GL_PARAM = "interestPayableGlAccountId";
    public static final String INTEREST_INCOME_GL_PARAM = "interestIncomeGlAccountId";
    public static final String INTEREST_EXPENSE_GL_PARAM = "interestExpenseGlAccountId";
    public static final String PREMIUM_GL_PARAM = "premiumGlAccountId";
    public static final String DISCOUNT_GL_PARAM = "discountGlAccountId";
    public static final String FEE_INCOME_GL_PARAM = "feeIncomeGlAccountId";
    public static final String FEE_EXPENSE_GL_PARAM = "feeExpenseGlAccountId";
    public static final String ACTIVE_PARAM = "active";
    public static final String FEE_METHOD_PARAM = "feeMethod";
    public static final String AMORTISATION_METHOD_PARAM = "amortisationMethod";
    public static final String DEFERRED_FEE_GL_PARAM = "deferredFeeGlAccountId";
    public static final String REALISED_GAIN_GL_PARAM = "realisedGainGlAccountId";
    public static final String REALISED_LOSS_GL_PARAM = "realisedLossGlAccountId";
    public static final String FAIR_VALUE_ADJUSTMENT_GL_PARAM = "fairValueAdjustmentGlAccountId";
    public static final String FAIR_VALUE_GAIN_GL_PARAM = "fairValueGainGlAccountId";
    public static final String FAIR_VALUE_LOSS_GL_PARAM = "fairValueLossGlAccountId";
    public static final String FAIR_VALUE_RESERVE_GL_PARAM = "fairValueReserveGlAccountId";
    public static final String IMPAIRMENT_EXPENSE_GL_PARAM = "impairmentExpenseGlAccountId";
    public static final String ECL_ALLOWANCE_GL_PARAM = "eclAllowanceGlAccountId";

    public static final String DEAL_TYPE_ID_PARAM = "dealTypeId";
    public static final String COUNTERPARTY_ID_PARAM = "counterpartyId";
    public static final String SETTLEMENT_ACCOUNT_ID_PARAM = "settlementAccountId";
    public static final String EXTERNAL_REFERENCE_PARAM = "externalReference";
    public static final String ISIN_PARAM = "isin";
    public static final String TRADE_DATE_PARAM = "tradeDate";
    public static final String VALUE_DATE_PARAM = "valueDate";
    public static final String MATURITY_DATE_PARAM = "maturityDate";
    public static final String ISSUE_DATE_PARAM = "issueDate";
    public static final String AMOUNT_PARAM = "amount";
    public static final String RATE_PARAM = "rate";
    public static final String CLEAN_PRICE_PARAM = "cleanPrice";
    public static final String DAY_COUNT_PARAM = "dayCount";
    public static final String FREQUENCY_PARAM = "frequency";
    public static final String AMORTISING_PARAM = "amortising";
    public static final String FEES_PARAM = "fees";
    public static final String CLASSIFICATION_PARAM = "classification";
    public static final String NOTES_PARAM = "notes";
    public static final String CONFIRMATION_REFERENCE_PARAM = "confirmationReference";
    public static final String ROLLOVER_PRE_APPROVED_PARAM = "rolloverPreApproved";
    public static final String CASHFLOW_ID_PARAM = "cashflowId";
    public static final String TRANSACTION_DATE_PARAM = "transactionDate";
    public static final String PROCEEDS_PARAM = "proceeds";
    public static final String BREAK_COST_PARAM = "breakCost";
    public static final String CAPITALISE_INTEREST_PARAM = "capitaliseInterest";
    public static final String REASON_PARAM = "reason";
    public static final String NOTE_PARAM = "note";
    public static final String DATE_FORMAT_PARAM = "dateFormat";
    public static final String LOCALE_PARAM = "locale";

    public static final Set<String> COUNTERPARTY_PARAMS = Set.of(NAME_PARAM, SHORT_CODE_PARAM, COUNTERPARTY_TYPE_PARAM, RATING_PARAM,
            RATING_CODE_VALUE_ID_PARAM, EXPOSURE_LIMIT_PARAM, LIMIT_CURRENCY_CODE_PARAM, WARNING_PERCENT_PARAM, LOCALE_PARAM);

    public static final Set<String> DEAL_TYPE_PARAMS = Set.of(CODE_PARAM, NAME_PARAM, INSTRUMENT_KIND_PARAM, INTEREST_METHOD_PARAM,
            DEFAULT_DAY_COUNT_PARAM, DEFAULT_FREQUENCY_PARAM, PRINCIPAL_GL_PARAM, INTEREST_RECEIVABLE_GL_PARAM, INTEREST_PAYABLE_GL_PARAM,
            INTEREST_INCOME_GL_PARAM, INTEREST_EXPENSE_GL_PARAM, PREMIUM_GL_PARAM, DISCOUNT_GL_PARAM, FEE_INCOME_GL_PARAM,
            FEE_EXPENSE_GL_PARAM, ACTIVE_PARAM, FEE_METHOD_PARAM, AMORTISATION_METHOD_PARAM, DEFERRED_FEE_GL_PARAM, REALISED_GAIN_GL_PARAM,
            REALISED_LOSS_GL_PARAM, FAIR_VALUE_ADJUSTMENT_GL_PARAM, FAIR_VALUE_GAIN_GL_PARAM, FAIR_VALUE_LOSS_GL_PARAM,
            FAIR_VALUE_RESERVE_GL_PARAM, IMPAIRMENT_EXPENSE_GL_PARAM, ECL_ALLOWANCE_GL_PARAM, LOCALE_PARAM);

    public static final Set<String> DEAL_PARAMS = Set.of(DEAL_TYPE_ID_PARAM, COUNTERPARTY_ID_PARAM, SETTLEMENT_ACCOUNT_ID_PARAM,
            EXTERNAL_REFERENCE_PARAM, ISIN_PARAM, TRADE_DATE_PARAM, VALUE_DATE_PARAM, MATURITY_DATE_PARAM, ISSUE_DATE_PARAM, AMOUNT_PARAM,
            RATE_PARAM, CLEAN_PRICE_PARAM, DAY_COUNT_PARAM, FREQUENCY_PARAM, AMORTISING_PARAM, FEES_PARAM, CLASSIFICATION_PARAM,
            NOTES_PARAM, ROLLOVER_PRE_APPROVED_PARAM, DATE_FORMAT_PARAM, LOCALE_PARAM);

    /** Body of approve, reject, confirm, cancel and settle. */
    public static final Set<String> DEAL_ACTION_PARAMS = Set.of(CONFIRMATION_REFERENCE_PARAM, REASON_PARAM, NOTE_PARAM, LOCALE_PARAM);

    public static final Set<String> SETTLE_CASHFLOW_PARAMS = Set.of(CASHFLOW_ID_PARAM, AMOUNT_PARAM, TRANSACTION_DATE_PARAM, NOTE_PARAM,
            DATE_FORMAT_PARAM, LOCALE_PARAM);

    public static final Set<String> TERMINATE_PARAMS = Set.of(TRANSACTION_DATE_PARAM, PROCEEDS_PARAM, BREAK_COST_PARAM, REASON_PARAM,
            NOTE_PARAM, DATE_FORMAT_PARAM, LOCALE_PARAM);

    public static final Set<String> ROLLOVER_PARAMS = Set.of(RATE_PARAM, MATURITY_DATE_PARAM, CAPITALISE_INTEREST_PARAM,
            EXTERNAL_REFERENCE_PARAM, ROLLOVER_PRE_APPROVED_PARAM, NOTE_PARAM, DATE_FORMAT_PARAM, LOCALE_PARAM);
}
