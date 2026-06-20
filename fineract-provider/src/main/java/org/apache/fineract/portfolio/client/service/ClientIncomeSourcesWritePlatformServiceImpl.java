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

package org.apache.fineract.portfolio.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientIncomeSource;
import org.apache.fineract.portfolio.client.domain.ClientIncomeSourceRepository;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.serialization.ClientIncomeSourceCommandFromApiJsonDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientIncomeSourcesWritePlatformServiceImpl implements ClientIncomeSourcesWritePlatformService {

    private static final Logger LOG = LoggerFactory.getLogger(ClientIncomeSourcesWritePlatformServiceImpl.class);

    private final PlatformSecurityContext context;
    private final ClientIncomeSourceRepository incomeSourceRepository;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final ClientIncomeSourceCommandFromApiJsonDeserializer apiJsonDeserializer;

    @Override
    @Transactional
    public CommandProcessingResult addIncomeSource(final long clientId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForCreate(clientId, command.json());

        final Client client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);
        final ClientIncomeSource incomeSource = mapFromCommand(client, command.parsedJson().getAsJsonObject());
        applyPrimarySource(client, incomeSource, incomeSource.getIsPrimarySource());
        this.incomeSourceRepository.saveAndFlush(incomeSource);

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(incomeSource.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult addClientIncomeSources(final Client client, final JsonCommand command) {
        this.context.authenticatedUser();
        final JsonArray incomeSources = command.arrayOfParameterNamed(ClientIncomeSourceCommandFromApiJsonDeserializer.INCOME_SOURCES);
        ClientIncomeSource lastSaved = null;
        for (JsonElement element : incomeSources) {
            this.apiJsonDeserializer.validateForCreate(client.getId(), element.toString());
            final ClientIncomeSource incomeSource = mapFromCommand(client, element.getAsJsonObject());
            applyPrimarySource(client, incomeSource, incomeSource.getIsPrimarySource());
            lastSaved = this.incomeSourceRepository.saveAndFlush(incomeSource);
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId())
                .withEntityId(lastSaved != null ? lastSaved.getId() : null).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateIncomeSource(final long incomeSourceId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForUpdate(incomeSourceId, command.json());

        final ClientIncomeSource incomeSource = this.incomeSourceRepository.findById(incomeSourceId)
                .orElseThrow(() -> new org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException(
                        "error.msg.income.source.not.found", "Income source not found.", "incomeSourceId", incomeSourceId));

        final JsonObject json = command.parsedJson().getAsJsonObject();
        patchFromCommand(incomeSource, json);
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.IS_PRIMARY_SOURCE)) {
            applyPrimarySource(incomeSource.getClient(), incomeSource, incomeSource.getIsPrimarySource());
        }
        this.incomeSourceRepository.saveAndFlush(incomeSource);

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(incomeSource.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteIncomeSource(final long incomeSourceId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForDelete(incomeSourceId);

        final ClientIncomeSource incomeSource = this.incomeSourceRepository.findById(incomeSourceId)
                .orElseThrow(() -> new org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException(
                        "error.msg.income.source.not.found", "Income source not found.", "incomeSourceId", incomeSourceId));
        this.incomeSourceRepository.delete(incomeSource);

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(incomeSourceId).build();
    }

    private ClientIncomeSource mapFromCommand(final Client client, final JsonObject json) {
        final ClientIncomeSource incomeSource = new ClientIncomeSource();
        incomeSource.setClient(client);
        patchFromCommand(incomeSource, json);
        if (StringUtils.isBlank(incomeSource.getStatus())) {
            incomeSource.setStatus("ACTIVE");
        }
        if (StringUtils.isBlank(incomeSource.getIsPrimarySource())) {
            incomeSource.setIsPrimarySource("N");
        }
        return incomeSource;
    }

    private void patchFromCommand(final ClientIncomeSource incomeSource, final JsonObject json) {
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.INCOME_SOURCE_TYPE_ID)
                && !json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.INCOME_SOURCE_TYPE_ID).isJsonNull()) {
            incomeSource.setIncomeSourceTypeId(json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.INCOME_SOURCE_TYPE_ID).getAsLong());
        }
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.SOURCE_OF_FUNDS_ID)
                && !json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.SOURCE_OF_FUNDS_ID).isJsonNull()) {
            incomeSource.setSourceOfFundsId(json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.SOURCE_OF_FUNDS_ID).getAsLong());
        }
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.EMPLOYER_BUSINESS_NAME)) {
            incomeSource.setEmployerBusinessName(
                    StringUtils.trimToNull(json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.EMPLOYER_BUSINESS_NAME).getAsString()));
        }
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.OCCUPATION)) {
            incomeSource.setOccupation(
                    StringUtils.trimToNull(json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.OCCUPATION).getAsString()));
        }
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.SUB_INDUSTRY_ID)
                && !json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.SUB_INDUSTRY_ID).isJsonNull()) {
            incomeSource.setSubIndustryId(json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.SUB_INDUSTRY_ID).getAsLong());
        }
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.MONTHLY_INCOME)
                && !json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.MONTHLY_INCOME).isJsonNull()) {
            incomeSource.setMonthlyIncome(json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.MONTHLY_INCOME).getAsBigDecimal());
        }
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.INCOME_CURRENCY_CODE)) {
            incomeSource.setIncomeCurrencyCode(
                    StringUtils.trimToNull(json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.INCOME_CURRENCY_CODE).getAsString()));
        }
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.INCOME_FREQUENCY_ID)
                && !json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.INCOME_FREQUENCY_ID).isJsonNull()) {
            incomeSource.setIncomeFrequencyId(json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.INCOME_FREQUENCY_ID).getAsLong());
        }
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.START_DATE)) {
            incomeSource.setStartDate(parseLocalDate(json, ClientIncomeSourceCommandFromApiJsonDeserializer.START_DATE));
        }
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.END_DATE)) {
            incomeSource.setEndDate(parseLocalDate(json, ClientIncomeSourceCommandFromApiJsonDeserializer.END_DATE));
        }
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.IS_PRIMARY_SOURCE)
                && !json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.IS_PRIMARY_SOURCE).isJsonNull()) {
            final boolean primary = json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.IS_PRIMARY_SOURCE).getAsBoolean();
            incomeSource.setIsPrimarySource(primary ? "Y" : "N");
        }
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.VERIFICATION_STATUS_ID)
                && !json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.VERIFICATION_STATUS_ID).isJsonNull()) {
            incomeSource.setVerificationStatusId(
                    json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.VERIFICATION_STATUS_ID).getAsLong());
        }
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.SUPPORTING_DOCUMENT)) {
            incomeSource.setSupportingDocument(
                    StringUtils.trimToNull(json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.SUPPORTING_DOCUMENT).getAsString()));
        }
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.REMARKS)) {
            incomeSource.setRemarks(StringUtils.trimToNull(json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.REMARKS).getAsString()));
        }
        if (json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.STATUS)) {
            incomeSource.setStatus(StringUtils.defaultIfBlank(
                    json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.STATUS).getAsString(), incomeSource.getStatus()));
        }
    }

    private LocalDate parseLocalDate(final JsonObject json, final String fieldName) {
        if (json.get(fieldName).isJsonNull()) {
            return null;
        }
        final String dateFormat = json.has(ClientIncomeSourceCommandFromApiJsonDeserializer.DATE_FORMAT)
                ? json.get(ClientIncomeSourceCommandFromApiJsonDeserializer.DATE_FORMAT).getAsString()
                : "dd MMMM yyyy";
        final DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern(dateFormat).toFormatter();
        try {
            return LocalDate.parse(json.get(fieldName).getAsString(), formatter);
        } catch (DateTimeParseException e) {
            LOG.error("Problem parsing date field {}", fieldName, e);
            return null;
        }
    }

    private void applyPrimarySource(final Client client, final ClientIncomeSource incomeSource, final String primaryFlag) {
        if (!"Y".equalsIgnoreCase(primaryFlag)) {
            return;
        }
        final List<ClientIncomeSource> existingPrimary = this.incomeSourceRepository.findByClient_IdAndIsPrimarySource(client.getId(), "Y");
        for (ClientIncomeSource other : existingPrimary) {
            if (incomeSource.getId() == null || !other.getId().equals(incomeSource.getId())) {
                other.setIsPrimarySource("N");
                this.incomeSourceRepository.save(other);
            }
        }
        incomeSource.setIsPrimarySource("Y");
    }
}
