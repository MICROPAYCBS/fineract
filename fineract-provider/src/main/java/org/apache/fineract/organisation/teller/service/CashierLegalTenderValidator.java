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
package org.apache.fineract.organisation.teller.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.MathUtil;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.teller.domain.CashierTransactionLegalTender;
import org.apache.fineract.organisation.teller.domain.CurrencyLegalTender;
import org.apache.fineract.organisation.teller.domain.CurrencyLegalTenderRepository;
import org.apache.fineract.organisation.teller.exception.CashierLegalTenderValidationException;
import org.apache.fineract.organisation.teller.exception.LegalTenderNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CashierLegalTenderValidator {

    public static final String LEGAL_TENDER_LINES = "legalTenderLines";
    public static final String LEGAL_TENDER_ID = "legalTenderId";
    public static final String QUANTITY = "quantity";

    private final FromJsonHelper fromApiJsonHelper;
    private final CurrencyLegalTenderRepository legalTenderRepository;
    private final ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository;

    public List<CashierTransactionLegalTender> validateAndBuildLines(final JsonCommand command, final String currencyCode,
            final BigDecimal txnAmount) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new CashierLegalTenderValidationException("error.msg.cashier.legal.tender.currency.mismatch",
                    "Currency code is required for legal tender breakdown.");
        }
        final JsonElement element = command.parsedJson();
        if (!this.fromApiJsonHelper.parameterExists(LEGAL_TENDER_LINES, element)) {
            throw new CashierLegalTenderValidationException("error.msg.cashier.legal.tender.lines.required",
                    "At least one legal tender line is required for this transaction.");
        }
        final JsonArray linesArray = this.fromApiJsonHelper.extractJsonArrayNamed(LEGAL_TENDER_LINES, element);
        if (linesArray == null || linesArray.isEmpty()) {
            throw new CashierLegalTenderValidationException("error.msg.cashier.legal.tender.lines.required",
                    "At least one legal tender line is required for this transaction.");
        }

        final ApplicationCurrency currency = this.applicationCurrencyRepository.findOneWithNotFoundDetection(currencyCode);
        final int decimalPlaces = currency.getDecimalPlaces();
        final Set<Long> seenLegalTenderIds = new HashSet<>();
        final List<CashierTransactionLegalTender> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(decimalPlaces, RoundingMode.HALF_UP);

        for (JsonElement lineElement : linesArray) {
            if (!lineElement.isJsonObject()) {
                throw new CashierLegalTenderValidationException("error.msg.cashier.legal.tender.lines.required",
                        "Legal tender lines must be JSON objects.");
            }
            final JsonObject lineObject = lineElement.getAsJsonObject();
            final Long legalTenderId = this.fromApiJsonHelper.extractLongNamed(LEGAL_TENDER_ID, lineObject);
            final Integer quantity = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(QUANTITY, lineObject);
            if (legalTenderId == null || quantity == null || quantity <= 0) {
                throw new CashierLegalTenderValidationException("error.msg.cashier.legal.tender.lines.required",
                        "Each legal tender line requires a valid legalTenderId and a positive quantity.");
            }
            if (!seenLegalTenderIds.add(legalTenderId)) {
                throw new CashierLegalTenderValidationException("error.msg.cashier.legal.tender.duplicate",
                        "Duplicate legal tender entries are not allowed in the same transaction.");
            }
            final CurrencyLegalTender legalTender = this.legalTenderRepository.findByIdAndCurrencyCode(legalTenderId, currencyCode)
                    .orElseThrow(() -> new LegalTenderNotFoundException(legalTenderId));
            if (!Boolean.TRUE.equals(legalTender.getIsActive())) {
                throw new CashierLegalTenderValidationException("error.msg.cashier.legal.tender.inactive",
                        "Legal tender `" + legalTender.getLabel() + "` is inactive.");
            }
            if (!currencyCode.equalsIgnoreCase(legalTender.getCurrencyCode())) {
                throw new CashierLegalTenderValidationException("error.msg.cashier.legal.tender.currency.mismatch",
                        "Legal tender currency does not match transaction currency.");
            }
            final BigDecimal lineAmount = legalTender.getValue().multiply(BigDecimal.valueOf(quantity.longValue())).setScale(decimalPlaces,
                    RoundingMode.HALF_UP);
            total = total.add(lineAmount);
            lines.add(CashierTransactionLegalTender.createNew(null, legalTender, quantity, lineAmount));
        }

        final BigDecimal normalizedTxnAmount = txnAmount.setScale(decimalPlaces, RoundingMode.HALF_UP);
        if (!MathUtil.isEqualTo(total, normalizedTxnAmount)) {
            throw new CashierLegalTenderValidationException("error.msg.cashier.legal.tender.sum.mismatch",
                    "Legal tender breakdown total does not match transaction amount.");
        }
        return lines;
    }
}
