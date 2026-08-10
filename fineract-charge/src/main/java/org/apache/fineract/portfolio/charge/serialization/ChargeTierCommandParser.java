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
package org.apache.fineract.portfolio.charge.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeAppliesTo;
import org.apache.fineract.portfolio.charge.domain.ChargeTier;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChargeTierCommandParser {

    public static final String CHARGE_TIERS = "chargeTiers";
    public static final String USE_CHARGE_TIERS = "useChargeTiers";
    public static final String AMOUNT_RANGE_FROM = "amountRangeFrom";
    public static final String AMOUNT_RANGE_TO = "amountRangeTo";
    public static final String AMOUNT = "amount";
    public static final String MIN_CAP = "minCap";
    public static final String MAX_CAP = "maxCap";

    private final FromJsonHelper fromApiJsonHelper;

    public boolean hasChargeTiers(final JsonElement element) {
        return this.fromApiJsonHelper.parameterExists(CHARGE_TIERS, element)
                && element.getAsJsonObject().get(CHARGE_TIERS).isJsonArray()
                && !element.getAsJsonObject().getAsJsonArray(CHARGE_TIERS).isEmpty();
    }

    public boolean extractUseChargeTiers(final JsonElement element) {
        if (!this.fromApiJsonHelper.parameterExists(USE_CHARGE_TIERS, element)) {
            return false;
        }
        final Boolean value = this.fromApiJsonHelper.extractBooleanNamed(USE_CHARGE_TIERS, element);
        return Boolean.TRUE.equals(value);
    }

    /**
     * Charge times that already allow percentage calculation (and thus have a defined base for lookup).
     * Flat calendar fees are excluded.
     */
    public static boolean supportsChargeTiers(final ChargeAppliesTo appliesTo, final ChargeTimeType chargeTimeType) {
        if (appliesTo == null || chargeTimeType == null || chargeTimeType == ChargeTimeType.INVALID) {
            return false;
        }
        if (appliesTo.isLoanCharge()) {
            return chargeTimeType.isTimeOfDisbursement() || chargeTimeType.isTrancheDisbursement() || chargeTimeType.isOnSpecifiedDueDate()
                    || chargeTimeType.isInstalmentFee() || chargeTimeType.isOverdueInstallment();
        }
        if (appliesTo.isSavingsCharge()) {
            return chargeTimeType.isWithdrawalFee() || chargeTimeType.isSavingsNoActivityFee();
        }
        return false;
    }

    public static boolean supportsChargeTiers(final Integer chargeAppliesTo, final Integer chargeTimeType) {
        if (chargeAppliesTo == null || chargeTimeType == null) {
            return false;
        }
        return supportsChargeTiers(ChargeAppliesTo.fromInt(chargeAppliesTo), ChargeTimeType.fromInt(chargeTimeType));
    }

    /**
     * Validates mutual exclusivity of legacy amount/caps vs tiered mode.
     *
     * @param useChargeTiers effective mode for this request (create: from payload; update: resolved by caller)
     * @param requireTiers when true, non-empty chargeTiers are mandatory
     * @param chargeTimeType charge time for allow-list check; when null, time-type allow-list is skipped (caller must enforce)
     */
    public void validateMode(final JsonElement element, final Integer chargeAppliesTo, final Integer chargeTimeType,
            final boolean useChargeTiers, final boolean requireTiers, final DataValidatorBuilder baseDataValidator) {
        if (useChargeTiers) {
            final ChargeAppliesTo appliesTo = ChargeAppliesTo.fromInt(chargeAppliesTo);
            if (chargeAppliesTo != null && !appliesTo.isLoanCharge() && !appliesTo.isSavingsCharge()) {
                baseDataValidator.reset().parameter(USE_CHARGE_TIERS).failWithCode("not.supported.for.charge.applies.to");
                return;
            }
            if (chargeTimeType != null && !supportsChargeTiers(chargeAppliesTo, chargeTimeType)) {
                baseDataValidator.reset().parameter(USE_CHARGE_TIERS).failWithCode("not.supported.for.charge.time.type");
            }
            if (this.fromApiJsonHelper.parameterExists(MIN_CAP, element)) {
                baseDataValidator.reset().parameter(MIN_CAP).failWithCode("not.supported.when.useChargeTiers");
            }
            if (this.fromApiJsonHelper.parameterExists(MAX_CAP, element)) {
                baseDataValidator.reset().parameter(MAX_CAP).failWithCode("not.supported.when.useChargeTiers");
            }
            if (requireTiers && !hasChargeTiers(element)) {
                baseDataValidator.reset().parameter(CHARGE_TIERS).failWithCode("required.when.useChargeTiers");
            } else if (hasChargeTiers(element)) {
                validate(element, chargeAppliesTo, baseDataValidator);
            }
        } else if (hasChargeTiers(element)) {
            baseDataValidator.reset().parameter(CHARGE_TIERS).failWithCode("not.allowed.when.useChargeTiers.false");
        }
    }

    public void validate(final JsonElement element, final Integer chargeAppliesTo, final DataValidatorBuilder baseDataValidator) {
        if (!this.fromApiJsonHelper.parameterExists(CHARGE_TIERS, element)) {
            return;
        }
        final JsonArray array = element.getAsJsonObject().getAsJsonArray(CHARGE_TIERS);
        if (array == null || array.isEmpty()) {
            return;
        }

        final ChargeAppliesTo appliesTo = ChargeAppliesTo.fromInt(chargeAppliesTo);
        if (!appliesTo.isLoanCharge() && !appliesTo.isSavingsCharge()) {
            baseDataValidator.reset().parameter(CHARGE_TIERS).failWithCode("not.supported.for.charge.applies.to");
            return;
        }

        final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(element.getAsJsonObject());
        final List<ParsedTier> parsed = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            final JsonObject tierObject = array.get(i).getAsJsonObject();
            final String prefix = CHARGE_TIERS + "[" + i + "]";
            final BigDecimal from = this.fromApiJsonHelper.extractBigDecimalNamed(AMOUNT_RANGE_FROM, tierObject, locale);
            final BigDecimal to = this.fromApiJsonHelper.parameterExists(AMOUNT_RANGE_TO, tierObject)
                    && !tierObject.get(AMOUNT_RANGE_TO).isJsonNull()
                            ? this.fromApiJsonHelper.extractBigDecimalNamed(AMOUNT_RANGE_TO, tierObject, locale)
                            : null;
            final BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalNamed(AMOUNT, tierObject, locale);

            baseDataValidator.reset().parameter(prefix + "." + AMOUNT_RANGE_FROM).value(from).notNull().zeroOrPositiveAmount();
            baseDataValidator.reset().parameter(prefix + "." + AMOUNT).value(amount).notNull().positiveAmount();
            if (to != null) {
                baseDataValidator.reset().parameter(prefix + "." + AMOUNT_RANGE_TO).value(to).positiveAmount();
                if (from != null && to.compareTo(from) <= 0) {
                    baseDataValidator.reset().parameter(prefix + "." + AMOUNT_RANGE_TO).failWithCode("must.be.greater.than.from");
                }
            }
            parsed.add(new ParsedTier(from, to, amount));
        }

        parsed.sort(Comparator.comparing(ParsedTier::from, Comparator.nullsFirst(Comparator.naturalOrder())));
        for (int i = 0; i < parsed.size(); i++) {
            final ParsedTier current = parsed.get(i);
            if (i == 0) {
                if (current.from() == null || current.from().compareTo(BigDecimal.ZERO) != 0) {
                    baseDataValidator.reset().parameter(CHARGE_TIERS + "[0]." + AMOUNT_RANGE_FROM).failWithCode("must.start.at.zero");
                }
            } else {
                final ParsedTier previous = parsed.get(i - 1);
                if (previous.to() == null) {
                    baseDataValidator.reset().parameter(CHARGE_TIERS + "[" + (i - 1) + "]." + AMOUNT_RANGE_TO)
                            .failWithCode("only.last.tier.may.be.open.ended");
                } else if (current.from() == null || previous.to().compareTo(current.from()) != 0) {
                    baseDataValidator.reset().parameter(CHARGE_TIERS + "[" + i + "]." + AMOUNT_RANGE_FROM)
                            .failWithCode("must.equal.previous.to");
                }
            }
            if (i < parsed.size() - 1 && current.to() == null) {
                baseDataValidator.reset().parameter(CHARGE_TIERS + "[" + i + "]." + AMOUNT_RANGE_TO)
                        .failWithCode("only.last.tier.may.be.open.ended");
            }
        }
    }

    public List<ChargeTier> parseTiers(final JsonCommand command, final Charge charge) {
        if (!command.parameterExists(CHARGE_TIERS)) {
            return List.of();
        }
        final JsonElement element = this.fromApiJsonHelper.parse(command.json());
        final JsonArray array = element.getAsJsonObject().getAsJsonArray(CHARGE_TIERS);
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        final Locale locale = command.extractLocale();
        final List<ChargeTier> tiers = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            final JsonObject tierObject = array.get(i).getAsJsonObject();
            final BigDecimal from = this.fromApiJsonHelper.extractBigDecimalNamed(AMOUNT_RANGE_FROM, tierObject, locale);
            final BigDecimal to = this.fromApiJsonHelper.parameterExists(AMOUNT_RANGE_TO, tierObject)
                    && !tierObject.get(AMOUNT_RANGE_TO).isJsonNull()
                            ? this.fromApiJsonHelper.extractBigDecimalNamed(AMOUNT_RANGE_TO, tierObject, locale)
                            : null;
            final BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalNamed(AMOUNT, tierObject, locale);
            tiers.add(ChargeTier.create(charge, from, to, amount));
        }
        tiers.sort(Comparator.comparing(ChargeTier::getAmountRangeFrom));
        return tiers;
    }

    public void throwIfErrors(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    private record ParsedTier(BigDecimal from, BigDecimal to, BigDecimal amount) {}
}
