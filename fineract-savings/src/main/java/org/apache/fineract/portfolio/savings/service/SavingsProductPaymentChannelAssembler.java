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
package org.apache.fineract.portfolio.savings.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.charge.exception.ChargeCannotBeAppliedToException;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepository;
import org.apache.fineract.portfolio.paymenttype.exception.PaymentTypeNotFoundException;
import org.apache.fineract.portfolio.savings.data.SavingsChannelLimitValues;
import org.apache.fineract.portfolio.savings.data.SavingsProductPaymentChannelLimits;
import org.apache.fineract.portfolio.savings.data.SavingsProductPaymentChannelLink;
import org.apache.fineract.portfolio.savings.data.SavingsProductPaymentChannelLink.ChannelChargeLink;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingsProductPaymentChannelAssembler {

    private final ChargeRepositoryWrapper chargeRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final FromJsonHelper fromApiJsonHelper;

    public List<SavingsProductPaymentChannelLink> assemble(final JsonCommand command, final String currencyCode) {
        final List<SavingsProductPaymentChannelLink> links = new ArrayList<>();
        if (!command.parameterExists("paymentChannels")) {
            return links;
        }
        final JsonArray channelsArray = command.arrayOfParameterNamed("paymentChannels");
        if (channelsArray == null) {
            return links;
        }

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource("savingsproduct.paymentChannels");
        final Locale locale = command.extractLocale();
        final Set<Long> seenPaymentTypes = new HashSet<>();
        int activeTransferChannels = 0;

        for (int i = 0; i < channelsArray.size(); i++) {
            final JsonObject jsonObject = channelsArray.get(i).getAsJsonObject();
            if (!jsonObject.has("paymentTypeId")) {
                baseDataValidator.reset().parameter("paymentChannels").failWithCode("paymentTypeId.required");
                continue;
            }
            final Long paymentTypeId = jsonObject.get("paymentTypeId").getAsLong();
            if (!seenPaymentTypes.add(paymentTypeId)) {
                baseDataValidator.reset().parameter("paymentChannels[" + i + "].paymentTypeId").value(paymentTypeId)
                        .failWithCode("duplicated");
                continue;
            }
            final PaymentType paymentType = this.paymentTypeRepository.findById(paymentTypeId)
                    .orElseThrow(() -> new PaymentTypeNotFoundException(paymentTypeId));

            final boolean isPremium = jsonObject.has("isPremium") && !jsonObject.get("isPremium").isJsonNull()
                    && jsonObject.get("isPremium").getAsBoolean();
            final boolean isActive = !jsonObject.has("isActive") || jsonObject.get("isActive").isJsonNull()
                    || jsonObject.get("isActive").getAsBoolean();
            final String name = jsonObject.has("name") && !jsonObject.get("name").isJsonNull() ? jsonObject.get("name").getAsString()
                    : null;
            final String description = jsonObject.has("description") && !jsonObject.get("description").isJsonNull()
                    ? jsonObject.get("description").getAsString()
                    : null;

            final List<ChannelChargeLink> chargeLinks = new ArrayList<>();
            final Set<Long> seenChargeIds = new HashSet<>();
            if (jsonObject.has("charges") && jsonObject.get("charges").isJsonArray()) {
                final JsonArray chargesArray = jsonObject.getAsJsonArray("charges");
                for (int c = 0; c < chargesArray.size(); c++) {
                    final JsonElement chargeElement = chargesArray.get(c);
                    final JsonObject chargeObject = chargeElement.getAsJsonObject();
                    if (!chargeObject.has("id")) {
                        continue;
                    }
                    final Long chargeId = chargeObject.get("id").getAsLong();
                    if (!seenChargeIds.add(chargeId)) {
                        baseDataValidator.reset().parameter("paymentChannels[" + i + "].charges").value(chargeId)
                                .failWithCode("duplicated.chargeId");
                        continue;
                    }
                    final Charge charge = this.chargeRepository.findOneWithNotFoundDetection(chargeId);
                    if (!charge.isSavingsCharge()) {
                        final String errorMessage = "Charge with identifier " + charge.getId()
                                + " cannot be applied to Savings product payment channel.";
                        throw new ChargeCannotBeAppliedToException("savings.product.payment.channel", errorMessage, charge.getId());
                    }
                    if (currencyCode != null && !currencyCode.equalsIgnoreCase(charge.getCurrencyCode())) {
                        baseDataValidator.reset().parameter("paymentChannels[" + i + "].charges").value(chargeId)
                                .failWithCode("charge.currency.not.same.as.product");
                    }
                    if (!isAttachableOnChannelSubscribe(charge)) {
                        baseDataValidator.reset().parameter("paymentChannels[" + i + "].charges").value(chargeId)
                                .failWithCode("charge.time.not.supported.for.channel.subscription");
                    }
                    if ((charge.isMonthlyFee() || charge.isAnnualFee()) && charge.getFeeOnMonthDay() == null) {
                        baseDataValidator.reset().parameter("paymentChannels[" + i + "].charges").value(chargeId)
                                .failWithCode("charge.missing.feeOnMonthDay");
                    }
                    if (charge.isMonthlyFee() && (charge.feeInterval() == null || charge.feeInterval() < 1)) {
                        baseDataValidator.reset().parameter("paymentChannels[" + i + "].charges").value(chargeId)
                                .failWithCode("charge.missing.feeInterval");
                    }
                    BigDecimal amount = null;
                    if (chargeObject.has("amount") && !chargeObject.get("amount").isJsonNull()) {
                        amount = this.fromApiJsonHelper.extractBigDecimalNamed("amount", chargeObject, locale);
                        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
                            baseDataValidator.reset().parameter("paymentChannels[" + i + "].charges[" + c + "].amount").value(amount)
                                    .failWithCode("not.greater.than.zero");
                        }
                        if (charge.isTiered() && amount != null) {
                            baseDataValidator.reset().parameter("paymentChannels[" + i + "].charges[" + c + "].amount").value(amount)
                                    .failWithCode("not.supported.for.tiered.charges");
                        }
                    }
                    chargeLinks.add(new ChannelChargeLink(charge, amount));
                }
            }

            if (isPremium && chargeLinks.isEmpty()) {
                // Premium channels may omit charges (subscribe still required); allow empty
            }

            final String prefix = "paymentChannels[" + i + "].";
            final SavingsChannelLimitValues debit = readLimits(jsonObject, locale, baseDataValidator, prefix + "maxDebit", "maxDebit");
            final SavingsChannelLimitValues credit = readLimits(jsonObject, locale, baseDataValidator, prefix + "maxCredit", "maxCredit");
            final boolean accountTransferChannel = jsonObject.has("isAccountTransferChannel")
                    && !jsonObject.get("isAccountTransferChannel").isJsonNull() && jsonObject.get("isAccountTransferChannel").getAsBoolean();
            if (isActive && accountTransferChannel) {
                activeTransferChannels++;
            }
            links.add(new SavingsProductPaymentChannelLink(paymentType, isPremium, isActive, name, description, chargeLinks,
                    new SavingsProductPaymentChannelLimits(debit, credit, accountTransferChannel)));
        }

        if (activeTransferChannels > 1) {
            baseDataValidator.reset().parameter("paymentChannels").failWithCode("multiple.account.transfer.channels");
        }

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
        return links;
    }

    private SavingsChannelLimitValues readLimits(final JsonObject jsonObject, final Locale locale, final DataValidatorBuilder validator,
            final String parameterPrefix, final String jsonPrefix) {
        final BigDecimal perTxn = optionalAmount(jsonObject, jsonPrefix + "PerTxn", locale, validator, parameterPrefix + "PerTxn");
        final BigDecimal perDay = optionalAmount(jsonObject, jsonPrefix + "PerDay", locale, validator, parameterPrefix + "PerDay");
        final BigDecimal perMonth = optionalAmount(jsonObject, jsonPrefix + "PerMonth", locale, validator, parameterPrefix + "PerMonth");
        final Integer countPerDay = optionalCount(jsonObject, jsonPrefix + "CountPerDay", validator, parameterPrefix + "CountPerDay");
        final Integer countPerMonth = optionalCount(jsonObject, jsonPrefix + "CountPerMonth", validator, parameterPrefix + "CountPerMonth");
        final SavingsChannelLimitValues values = new SavingsChannelLimitValues(perTxn, perDay, perMonth, countPerDay, countPerMonth);
        SavingsChannelLimitRules.validateOrdering(values, validator, parameterPrefix);
        return values;
    }

    private BigDecimal optionalAmount(final JsonObject jsonObject, final String name, final Locale locale,
            final DataValidatorBuilder validator, final String parameter) {
        if (!jsonObject.has(name) || jsonObject.get(name).isJsonNull()) {
            return null;
        }
        final BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalNamed(name, jsonObject, locale);
        SavingsChannelLimitRules.validateNonNegative(amount, validator, parameter);
        return amount;
    }

    private Integer optionalCount(final JsonObject jsonObject, final String name, final DataValidatorBuilder validator,
            final String parameter) {
        if (!jsonObject.has(name) || jsonObject.get(name).isJsonNull()) {
            return null;
        }
        final Integer count = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(name, jsonObject);
        SavingsChannelLimitRules.validateNonNegative(count, validator, parameter);
        return count;
    }

    /**
     * Channel subscribe attaches charges without a due date. Types that require {@code dueDate} at account attach
     * (specified due date, weekly) cannot be mapped. Activation is also awkward (account-status gated).
     */
    private static boolean isAttachableOnChannelSubscribe(final Charge charge) {
        final Integer chargeTime = charge.getChargeTimeType();
        if (chargeTime == null) {
            return false;
        }
        final ChargeTimeType type = ChargeTimeType.fromInt(chargeTime);
        return type.isMonthlyFee() || type.isAnnualFee() || type.isWithdrawalFee() || type.isSavingsNoActivityFee() || type.isOverdraftFee()
                || type.isSavingsClosure();
    }
}
