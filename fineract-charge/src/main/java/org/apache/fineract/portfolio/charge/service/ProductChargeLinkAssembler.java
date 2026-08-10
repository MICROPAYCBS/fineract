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
package org.apache.fineract.portfolio.charge.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.charge.data.ProductChargeLink;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.exception.ChargeCannotBeAppliedToException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductChargeLinkAssembler {

    private final ChargeRepositoryWrapper chargeRepository;
    private final FromJsonHelper fromApiJsonHelper;

    public List<ProductChargeLink> assembleLoanProductCharges(final JsonCommand command, final String currencyCode) {
        return assemble(command, currencyCode, true);
    }

    public List<ProductChargeLink> assembleSavingsProductCharges(final JsonCommand command, final String currencyCode) {
        return assemble(command, currencyCode, false);
    }

    private List<ProductChargeLink> assemble(final JsonCommand command, final String currencyCode, final boolean loanProduct) {
        final List<ProductChargeLink> links = new ArrayList<>();
        if (!command.parameterExists("charges")) {
            return links;
        }
        final JsonArray chargesArray = command.arrayOfParameterNamed("charges");
        if (chargesArray == null) {
            return links;
        }

        String productCurrencyCode = command.stringValueOfParameterNamed("currencyCode");
        if (productCurrencyCode == null) {
            productCurrencyCode = currencyCode;
        }

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("product.charges");
        final Locale locale = command.extractLocale();

        for (int i = 0; i < chargesArray.size(); i++) {
            final JsonObject jsonObject = chargesArray.get(i).getAsJsonObject();
            if (!jsonObject.has("id")) {
                continue;
            }
            final Long id = jsonObject.get("id").getAsLong();
            final Charge charge = this.chargeRepository.findOneWithNotFoundDetection(id);

            if (loanProduct) {
                if (!charge.isLoanCharge()) {
                    final String errorMessage = "Charge with identifier " + charge.getId() + " cannot be applied to Loan product.";
                    throw new ChargeCannotBeAppliedToException("loan.product", errorMessage, charge.getId());
                }
            } else if (!charge.isSavingsCharge()) {
                final String errorMessage = "Charge with identifier " + charge.getId() + " cannot be applied to Savings product.";
                throw new ChargeCannotBeAppliedToException("savings.product", errorMessage, charge.getId());
            }

            if (!productCurrencyCode.equals(charge.getCurrencyCode())) {
                baseDataValidator.reset().parameter("charges[" + i + "].id").value(id)
                        .failWithCodeNoParameterAddedToErrorCode("charge.and.product.currency.not.same");
            }

            BigDecimal amount = null;
            if (this.fromApiJsonHelper.parameterExists("amount", jsonObject)
                    && !jsonObject.get("amount").isJsonNull()) {
                amount = this.fromApiJsonHelper.extractBigDecimalNamed("amount", jsonObject, locale);
                baseDataValidator.reset().parameter("charges[" + i + "].amount").value(amount).notNull().positiveAmount();
                if (charge.isTiered()) {
                    baseDataValidator.reset().parameter("charges[" + i + "].amount")
                            .failWithCode("not.supported.when.charge.uses.tiers");
                }
            }
            links.add(new ProductChargeLink(charge, amount));
        }

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
        return links;
    }

    public static List<Charge> toCharges(final List<ProductChargeLink> links) {
        return links.stream().map(ProductChargeLink::charge).toList();
    }
}
