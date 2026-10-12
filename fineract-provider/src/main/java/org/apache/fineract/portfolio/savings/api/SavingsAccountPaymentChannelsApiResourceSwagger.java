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
package org.apache.fineract.portfolio.savings.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Request and response models for savings account payment channels. LocalDate values are Gson arrays.
 */
final class SavingsAccountPaymentChannelsApiResourceSwagger {

    private SavingsAccountPaymentChannelsApiResourceSwagger() {}

    @Schema(description = "Product payment channel on a savings account, with subscription and block status")
    public static final class GetSavingsAccountPaymentChannelResponse {

        private GetSavingsAccountPaymentChannelResponse() {}

        @Schema(description = "Subscription row id. Omitted when the account has never subscribed to this channel.", example = "12")
        public Long id;
        @Schema(description = "Product payment-channel catalog id", example = "4")
        public Long productPaymentChannelId;
        @Schema(description = "Payment type id", example = "1")
        public Long paymentTypeId;
        @Schema(description = "Payment type linked to the channel")
        public GetPaymentTypeResponse paymentType;
        @Schema(description = "True when the channel requires a subscription before deposits", example = "true")
        public Boolean isPremium;
        @Schema(description = "True when the product channel is active", example = "true")
        public Boolean isActive;
        @Schema(description = "Channel display name", example = "Mobile money")
        public String name;
        @Schema(description = "Channel description", example = "Premium mobile-money deposits")
        public String description;
        @Schema(description = "Latest subscription status. Omitted when the account has never subscribed.")
        public GetEnumOptionResponse subscriptionStatus;
        @Schema(description = "LocalDate serialised as [year, month, day]. Date the current or latest subscription started.", example = "[2026, 7, 1]")
        public List<Integer> subscribedOnDate;
        @Schema(description = "LocalDate serialised as [year, month, day]. Date the subscription ended. Omitted while it is active.", example = "[2026, 8, 1]")
        public List<Integer> unsubscribedOnDate;
        @Schema(description = "True when a deposit may use this channel", example = "true")
        public Boolean allowedForDeposit;
        @Schema(description = "True when an open block exists on this channel", example = "false")
        public Boolean blocked;
        @Schema(description = "LocalDate serialised as [year, month, day]. Date the open block started. Omitted when the channel is not blocked.", example = "[2026, 7, 10]")
        public List<Integer> blockedOnDate;
        @Schema(description = "Charges copied from the product channel")
        public List<GetPaymentChannelChargeResponse> charges;
    }

    @Schema(description = "Payment type attached to a channel")
    public static final class GetPaymentTypeResponse {

        private GetPaymentTypeResponse() {}

        @Schema(description = "Payment type id", example = "1")
        public Long id;
        @Schema(description = "Payment type name", example = "Cash")
        public String name;
        @Schema(description = "Payment type description", example = "Cash payment")
        public String description;
        @Schema(description = "True when the type is a cash payment", example = "true")
        public Boolean isCashPayment;
        @Schema(description = "Display position", example = "1")
        public Long position;
        @Schema(description = "Stable code name", example = "cash")
        public String codeName;
        @Schema(description = "True when the type is defined by the platform", example = "false")
        public Boolean isSystemDefined;
        @Schema(description = "True when the payment type is active", example = "true")
        public Boolean isActive;
    }

    @Schema(description = "Enumeration option. value is the enum name.")
    public static final class GetEnumOptionResponse {

        private GetEnumOptionResponse() {}

        @Schema(description = "Enum numeric id", example = "100")
        public Long id;
        @Schema(description = "Enum code", example = "savingsPaymentChannelStatus.active")
        public String code;
        @Schema(description = "Enum label. A subscription status uses ACTIVE or INACTIVE.", example = "ACTIVE")
        public String value;
    }

    @Schema(description = "Charge linked to a product payment channel, including the charge definition")
    public static final class GetPaymentChannelChargeResponse {

        private GetPaymentChannelChargeResponse() {}

        @Schema(description = "Product channel charge row id", example = "8")
        public Long id;
        @Schema(description = "Charge definition id", example = "3")
        public Long chargeId;
        @Schema(description = "Charge definition")
        public GetChargeResponse charge;
        @Schema(description = "Amount overridden on the channel, or the charge definition amount", example = "500.00")
        public BigDecimal amount;
    }

    @Schema(description = "Charge definition returned inside a payment-channel charge")
    public static final class GetChargeResponse {

        private GetChargeResponse() {}

        @Schema(description = "Charge id", example = "3")
        public Long id;
        @Schema(description = "Charge name", example = "Channel fee")
        public String name;
        @Schema(description = "True when the charge is active", example = "true")
        public Boolean active;
        @Schema(description = "True when the charge is a penalty", example = "false")
        public Boolean penalty;
        @Schema(description = "True when free withdrawals are enabled", example = "false")
        public Boolean freeWithdrawal;
        @Schema(description = "True when the charge is restricted to a payment type", example = "true")
        public Boolean isPaymentType;
        @Schema(description = "How often a free withdrawal is allowed", example = "1")
        public Integer freeWithdrawalChargeFrequency;
        @Schema(description = "Restart frequency count", example = "1")
        public Integer restartFrequency;
        @Schema(description = "Restart frequency period enum", example = "2")
        public Integer restartFrequencyEnum;
        @Schema(description = "Payment type the charge applies to. Only id and name are populated.")
        public GetChargePaymentTypeResponse paymentTypeOptions;
        @Schema(description = "Charge currency")
        public GetCurrencyResponse currency;
        @Schema(description = "Charge amount", example = "500.00")
        public BigDecimal amount;
        @Schema(description = "When the charge is applied")
        public GetEnumOptionResponse chargeTimeType;
        @Schema(description = "Portfolio the charge applies to")
        public GetEnumOptionResponse chargeAppliesTo;
        @Schema(description = "How the charge amount is calculated")
        public GetEnumOptionResponse chargeCalculationType;
        @Schema(description = "How the charge is paid")
        public GetEnumOptionResponse chargePaymentMode;
        @Schema(description = "Annual or monthly fee day. java.time.MonthDay, not a LocalDate array.")
        public GetMonthDayResponse feeOnMonthDay;
        @Schema(description = "Fee interval", example = "1")
        public Integer feeInterval;
        @Schema(description = "Minimum charge cap", example = "100.00")
        public BigDecimal minCap;
        @Schema(description = "Maximum charge cap", example = "1000.00")
        public BigDecimal maxCap;
        @Schema(description = "True when tiered amounts are used", example = "false")
        public Boolean useChargeTiers;
        @Schema(description = "Fee frequency")
        public GetEnumOptionResponse feeFrequency;
        @Schema(description = "Income or liability GL account. Only id, name and glCode are populated.")
        public GetGlAccountLookupResponse incomeOrLiabilityAccount;
        @Schema(description = "Tax group. Only id and name are populated.")
        public GetTaxGroupLookupResponse taxGroup;
        @Schema(description = "Amount tiers. Empty array when the charge has no tiers.")
        public List<GetChargeTierResponse> chargeTiers;
    }

    @Schema(description = "Payment type id and name on a charge")
    public static final class GetChargePaymentTypeResponse {

        private GetChargePaymentTypeResponse() {}

        @Schema(description = "Payment type id", example = "1")
        public Long id;
        @Schema(description = "Payment type name", example = "Cash")
        public String name;
    }

    @Schema(description = "Currency on a charge. Name and symbol are not populated on this response.")
    public static final class GetCurrencyResponse {

        private GetCurrencyResponse() {}

        @Schema(description = "ISO currency code", example = "UGX")
        public String code;
        @Schema(description = "Decimal places stored on this lookup. Zero when the charge lookup does not load them.", example = "0")
        public Integer decimalPlaces;
        @Schema(description = "Currency multiple. Zero when the charge lookup does not load it.", example = "0")
        public Integer inMultiplesOf;
        @Schema(description = "Display label built from the code when the name is absent", example = "[UGX]")
        public String displayLabel;
    }

    @Schema(description = "Month and day of an annual or monthly fee")
    public static final class GetMonthDayResponse {

        private GetMonthDayResponse() {}

        @Schema(description = "Month of year, from 1 to 12", example = "7")
        public Integer month;
        @Schema(description = "Day of month", example = "1")
        public Integer day;
    }

    @Schema(description = "GL account id, name and code on a charge")
    public static final class GetGlAccountLookupResponse {

        private GetGlAccountLookupResponse() {}

        @Schema(description = "GL account id", example = "15")
        public Long id;
        @Schema(description = "GL account name", example = "Fee income")
        public String name;
        @Schema(description = "GL code", example = "400001")
        public String glCode;
    }

    @Schema(description = "Tax group id and name on a charge")
    public static final class GetTaxGroupLookupResponse {

        private GetTaxGroupLookupResponse() {}

        @Schema(description = "Tax group id", example = "1")
        public Long id;
        @Schema(description = "Tax group name", example = "VAT")
        public String name;
    }

    @Schema(description = "One amount tier of a charge")
    public static final class GetChargeTierResponse {

        private GetChargeTierResponse() {}

        @Schema(description = "Tier id", example = "1")
        public Long id;
        @Schema(description = "Inclusive start of the amount range", example = "0.00")
        public BigDecimal amountRangeFrom;
        @Schema(description = "Inclusive end of the amount range", example = "10000.00")
        public BigDecimal amountRangeTo;
        @Schema(description = "Charge amount for this range", example = "100.00")
        public BigDecimal amount;
    }

    @Schema(description = "Bank ceiling, customer limit, pending increase and remaining usage for one direction")
    public static final class GetSavingsAccountChannelLimitResponse {

        private GetSavingsAccountChannelLimitResponse() {}

        @Schema(description = "Customer limit row id. Omitted when the customer has not set a limit.", example = "9")
        public Long id;
        @Schema(description = "Savings account id", example = "1")
        public Long savingsAccountId;
        @Schema(description = "Product payment-channel catalog id", example = "4")
        public Long productPaymentChannelId;
        @Schema(description = "Payment type id", example = "1")
        public Long paymentTypeId;
        @Schema(description = "Limit direction", example = "DEBIT", allowableValues = { "DEBIT", "CREDIT" })
        public String direction;
        @Schema(description = "Bank ceiling per transaction", example = "1000000.00")
        public BigDecimal ceilingPerTxn;
        @Schema(description = "Bank ceiling per day", example = "5000000.00")
        public BigDecimal ceilingPerDay;
        @Schema(description = "Bank ceiling per month", example = "20000000.00")
        public BigDecimal ceilingPerMonth;
        @Schema(description = "Bank maximum transaction count per day", example = "10")
        public Integer ceilingCountPerDay;
        @Schema(description = "Bank maximum transaction count per month", example = "100")
        public Integer ceilingCountPerMonth;
        @Schema(description = "Customer maximum per transaction currently in force", example = "500000.00")
        public BigDecimal maxPerTxn;
        @Schema(description = "Customer maximum per day currently in force", example = "1000000.00")
        public BigDecimal maxPerDay;
        @Schema(description = "Customer maximum per month currently in force", example = "5000000.00")
        public BigDecimal maxPerMonth;
        @Schema(description = "Customer maximum transaction count per day currently in force", example = "5")
        public Integer maxCountPerDay;
        @Schema(description = "Customer maximum transaction count per month currently in force", example = "40")
        public Integer maxCountPerMonth;
        @Schema(description = "Pending increase per transaction, waiting for the cooling period", example = "750000.00")
        public BigDecimal pendingMaxPerTxn;
        @Schema(description = "Pending increase per day", example = "2000000.00")
        public BigDecimal pendingMaxPerDay;
        @Schema(description = "Pending increase per month", example = "8000000.00")
        public BigDecimal pendingMaxPerMonth;
        @Schema(description = "Pending transaction-count increase per day", example = "8")
        public Integer pendingMaxCountPerDay;
        @Schema(description = "Pending transaction-count increase per month", example = "60")
        public Integer pendingMaxCountPerMonth;
        @Schema(description = "When a pending increase becomes effective, as an ISO-8601 offset date-time", example = "2026-07-12T09:00:00+03:00")
        public String pendingEffectiveOn;
        @Schema(description = "Effective maximum per transaction, the tighter of the customer limit and the bank ceiling", example = "500000.00")
        public BigDecimal effectivePerTxn;
        @Schema(description = "Effective maximum per day", example = "1000000.00")
        public BigDecimal effectivePerDay;
        @Schema(description = "Effective maximum per month", example = "5000000.00")
        public BigDecimal effectivePerMonth;
        @Schema(description = "Effective transaction-count maximum per day", example = "5")
        public Integer effectiveCountPerDay;
        @Schema(description = "Effective transaction-count maximum per month", example = "40")
        public Integer effectiveCountPerMonth;
        @Schema(description = "Amount already used today", example = "100000.00")
        public BigDecimal usedToday;
        @Schema(description = "Transaction count already used today", example = "1")
        public Integer countToday;
        @Schema(description = "Amount already used this month", example = "250000.00")
        public BigDecimal usedThisMonth;
        @Schema(description = "Transaction count already used this month", example = "4")
        public Integer countThisMonth;
        @Schema(description = "Amount still available today", example = "900000.00")
        public BigDecimal remainingToday;
        @Schema(description = "Amount still available this month", example = "4750000.00")
        public BigDecimal remainingThisMonth;
        @Schema(description = "Transaction count still available today", example = "4")
        public Integer remainingCountToday;
        @Schema(description = "Transaction count still available this month", example = "36")
        public Integer remainingCountThisMonth;
    }

    @Schema(description = "Body for subscribe, unsubscribe, block, unblock and updateLimit. updateLimit also accepts the limit fields.")
    public static final class PostSavingsAccountPaymentChannelsRequest {

        private PostSavingsAccountPaymentChannelsRequest() {}

        @Schema(description = "Payment type id of the product channel. Required for every command.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        public Long paymentTypeId;
        @Schema(description = "Required for updateLimit. DEBIT or CREDIT.", example = "DEBIT", allowableValues = { "DEBIT", "CREDIT" })
        public String direction;
        @Schema(description = "updateLimit only. Customer maximum per transaction. Omit to keep the current value.", example = "500000.00")
        public BigDecimal maxPerTxn;
        @Schema(description = "updateLimit only. Customer maximum per day. Omit to keep the current value.", example = "1000000.00")
        public BigDecimal maxPerDay;
        @Schema(description = "updateLimit only. Customer maximum per month. Omit to keep the current value.", example = "5000000.00")
        public BigDecimal maxPerMonth;
        @Schema(description = "updateLimit only. Customer maximum transaction count per day. Omit to keep the current value.", example = "5")
        public Integer maxCountPerDay;
        @Schema(description = "updateLimit only. Customer maximum transaction count per month. Omit to keep the current value.", example = "40")
        public Integer maxCountPerMonth;
    }

    @Schema(description = "Command result after a payment-channel write")
    public static final class PostSavingsAccountPaymentChannelsResponse {

        private PostSavingsAccountPaymentChannelsResponse() {}

        @Schema(description = "Maker-checker command id. Present when the command is held.", example = "42")
        public Long commandId;
        @Schema(description = "Office id of the savings account. Present when the command is processed immediately.", example = "1")
        public Long officeId;
        @Schema(description = "Group id of the savings account, when the account belongs to a group", example = "2")
        public Long groupId;
        @Schema(description = "Client id of the savings account. Present when the command is processed immediately.", example = "5")
        public Long clientId;
        @Schema(description = "Savings account id. Present when the command is processed immediately.", example = "1")
        public Long savingsId;
        @Schema(description = "Subscription, block or limit row id. On a held command this id was allocated inside the rolled-back transaction.", example = "12")
        public Long resourceId;
        @Schema(description = "True when maker-checker held the command and rolled the transaction back", example = "true")
        public Boolean rollbackTransaction;
    }
}
