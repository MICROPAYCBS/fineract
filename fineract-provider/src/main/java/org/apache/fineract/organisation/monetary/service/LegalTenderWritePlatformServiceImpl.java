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
package org.apache.fineract.organisation.monetary.service;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.serialization.LegalTenderCommandFromApiJsonDeserializer;
import org.apache.fineract.organisation.teller.domain.CurrencyLegalTender;
import org.apache.fineract.organisation.teller.domain.CurrencyLegalTenderRepository;
import org.apache.fineract.organisation.teller.domain.LegalTenderType;
import org.apache.fineract.organisation.teller.exception.LegalTenderNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LegalTenderWritePlatformServiceImpl implements LegalTenderWritePlatformService {

    private static final String RESOURCE_NAME = "LEGAL_TENDER";

    private final PlatformSecurityContext context;
    private final CurrencyLegalTenderRepository legalTenderRepository;
    private final ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository;
    private final LegalTenderCommandFromApiJsonDeserializer apiJsonDeserializer;
    private final FromJsonHelper fromApiJsonHelper;

    @Override
    @Transactional
    public CommandProcessingResult createLegalTender(final String currencyCode, final JsonCommand command) {
        this.context.authenticatedUser().validateHasCreatePermission(RESOURCE_NAME);
        this.applicationCurrencyRepository.findOneWithNotFoundDetection(currencyCode);
        this.apiJsonDeserializer.validateForCreate(command.json());

        final JsonObject json = command.parsedJson().getAsJsonObject();
        final CurrencyLegalTender legalTender = mapFromJson(new CurrencyLegalTender(), currencyCode, json, true);
        if (this.legalTenderRepository.existsByCurrencyCodeAndValueAndTenderType(legalTender.getCurrencyCode(), legalTender.getValue(),
                legalTender.getTenderType())) {
            throw new PlatformDataIntegrityException("error.msg.legal.tender.duplicate",
                    "Legal tender with the same value and type already exists for this currency.", LegalTenderCommandFromApiJsonDeserializer.VALUE,
                    legalTender.getValue());
        }
        this.legalTenderRepository.saveAndFlush(legalTender);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(legalTender.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateLegalTender(final String currencyCode, final Long legalTenderId, final JsonCommand command) {
        this.context.authenticatedUser().validateHasUpdatePermission(RESOURCE_NAME);
        this.applicationCurrencyRepository.findOneWithNotFoundDetection(currencyCode);
        this.apiJsonDeserializer.validateForUpdate(command.json());

        final CurrencyLegalTender legalTender = findWithNotFoundDetection(currencyCode, legalTenderId);
        final JsonObject json = command.parsedJson().getAsJsonObject();
        mapFromJson(legalTender, currencyCode, json, false);
        if (this.legalTenderRepository.existsByCurrencyCodeAndValueAndTenderTypeAndIdNot(legalTender.getCurrencyCode(), legalTender.getValue(),
                legalTender.getTenderType(), legalTenderId)) {
            throw new PlatformDataIntegrityException("error.msg.legal.tender.duplicate",
                    "Legal tender with the same value and type already exists for this currency.", LegalTenderCommandFromApiJsonDeserializer.VALUE,
                    legalTender.getValue());
        }
        this.legalTenderRepository.saveAndFlush(legalTender);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(legalTender.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteLegalTender(final String currencyCode, final Long legalTenderId, final JsonCommand command) {
        this.context.authenticatedUser().validateHasDeletePermission(RESOURCE_NAME);
        final CurrencyLegalTender legalTender = findWithNotFoundDetection(currencyCode, legalTenderId);
        if (this.legalTenderRepository.existsInCashierTransactions(legalTenderId)) {
            throw new PlatformDataIntegrityException("error.msg.legal.tender.in.use",
                    "Legal tender cannot be deleted because it is referenced by cashier transactions.");
        }
        try {
            this.legalTenderRepository.delete(legalTender);
        } catch (final DataIntegrityViolationException e) {
            throw new PlatformDataIntegrityException("error.msg.legal.tender.in.use",
                    "Legal tender cannot be deleted because it is referenced by other records.");
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(legalTenderId).build();
    }

    private CurrencyLegalTender findWithNotFoundDetection(final String currencyCode, final Long legalTenderId) {
        return this.legalTenderRepository.findByIdAndCurrencyCode(legalTenderId, currencyCode)
                .orElseThrow(() -> new LegalTenderNotFoundException(legalTenderId));
    }

    private CurrencyLegalTender mapFromJson(final CurrencyLegalTender legalTender, final String currencyCode, final JsonObject json,
            final boolean create) {
        legalTender.setCurrencyCode(currencyCode);
        if (create || json.has(LegalTenderCommandFromApiJsonDeserializer.VALUE)) {
            final BigDecimal value = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed(LegalTenderCommandFromApiJsonDeserializer.VALUE,
                    json);
            legalTender.setValue(value);
        }
        if (create || json.has(LegalTenderCommandFromApiJsonDeserializer.TENDER_TYPE)) {
            final String tenderType = this.fromApiJsonHelper.extractStringNamed(LegalTenderCommandFromApiJsonDeserializer.TENDER_TYPE, json);
            legalTender.setLegalTenderTypeEnum(LegalTenderMapper.parseTenderType(tenderType));
        }
        if (create || json.has(LegalTenderCommandFromApiJsonDeserializer.LABEL)) {
            legalTender.setLabel(this.fromApiJsonHelper.extractStringNamed(LegalTenderCommandFromApiJsonDeserializer.LABEL, json));
        }
        if (create || json.has(LegalTenderCommandFromApiJsonDeserializer.DISPLAY_ORDER)) {
            legalTender.setDisplayOrder(
                    this.fromApiJsonHelper.extractIntegerSansLocaleNamed(LegalTenderCommandFromApiJsonDeserializer.DISPLAY_ORDER, json));
        }
        if (create) {
            legalTender.setIsActive(true);
        }
        if (json.has(LegalTenderCommandFromApiJsonDeserializer.ACTIVE)) {
            legalTender.setIsActive(this.fromApiJsonHelper.extractBooleanNamed(LegalTenderCommandFromApiJsonDeserializer.ACTIVE, json));
        }
        return legalTender;
    }
}
