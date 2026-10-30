/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.api;

public final class TransactionBatchApiConstants {

    private TransactionBatchApiConstants() {}

    public static final String MODULE_ENABLED_PROPERTY = "fineract.module.transactionbatch.enabled";

    public static final String BATCH_RESOURCE = "TRANSACTIONBATCH";
    public static final String ITEM_RESOURCE = "TRANSACTIONBATCHITEM";
    public static final String SETTING_RESOURCE = "TRANSACTIONBATCHSETTING";

    public static final String COMMAND_COPY = "copy";
    public static final String COMMAND_SUBMIT = "submit";
    public static final String COMMAND_REPAIR = "repair";

    public static final String DESCRIPTION = "description";
    public static final String RECURRING = "recurring";
    public static final String EFFECTIVE_DATE = "effectiveDate";
    public static final String FREQUENCY_NUMBER = "frequencyNumber";
    public static final String FREQUENCY_UNIT = "frequencyUnit";
    public static final String NEXT_POSTING_DATE = "nextPostingDate";
    public static final String EXPIRY_DATE = "expiryDate";
    public static final String LOCALE = "locale";
    public static final String DATE_FORMAT = "dateFormat";

    public static final String ACCOUNT_TYPE = "accountType";
    public static final String GL_ACCOUNT_ID = "glAccountId";
    public static final String SAVINGS_ACCOUNT_ID = "savingsAccountId";
    public static final String SIDE = "side";
    public static final String CURRENCY_CODE = "currencyCode";
    public static final String AMOUNT = "amount";
    public static final String NARRATIVE = "narrative";
    public static final String REFERENCE = "reference";
    public static final String VALUE_DATE = "valueDate";

    public static final String CODE_PREFIX = "codePrefix";
    public static final String IMPORT_ROW_CAP = "importRowCap";
    public static final String CURRENCIES = "currencies";
    public static final String CLEARING_GL_ACCOUNT_ID = "clearingGlAccountId";
    public static final String SUSPENSE_GL_ACCOUNT_ID = "suspenseGlAccountId";

    public static final String CROSS_CURRENCY_MESSAGE = "Cross-currency lines are not allowed";
}
