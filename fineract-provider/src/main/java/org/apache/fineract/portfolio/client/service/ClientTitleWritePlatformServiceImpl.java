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

import com.google.gson.JsonObject;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.ClientTitle;
import org.apache.fineract.portfolio.client.domain.ClientTitleRepository;
import org.apache.fineract.portfolio.client.exception.ClientTitleNotFoundException;
import org.apache.fineract.portfolio.client.serialization.ClientTitleCommandFromApiJsonDeserializer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientTitleWritePlatformServiceImpl implements ClientTitleWritePlatformService {

    private final PlatformSecurityContext context;
    private final ClientTitleRepository clientTitleRepository;
    private final ClientTitleCommandFromApiJsonDeserializer apiJsonDeserializer;
    private final FromJsonHelper fromApiJsonHelper;

    @Override
    @Transactional
    public CommandProcessingResult createClientTitle(final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForCreate(command.json());

        final JsonObject json = command.parsedJson().getAsJsonObject();
        final String titleCode = this.fromApiJsonHelper.extractStringNamed(ClientTitleCommandFromApiJsonDeserializer.TITLE_CODE, json);
        if (this.clientTitleRepository.existsByTitleCode(titleCode)) {
            throw new PlatformDataIntegrityException("error.msg.client.title.duplicate.code",
                    "Client title with code `" + titleCode + "` already exists.", "titleCode", titleCode);
        }

        final ClientTitle clientTitle = mapFromJson(new ClientTitle(), json, true);
        this.clientTitleRepository.saveAndFlush(clientTitle);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(clientTitle.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateClientTitle(final Long clientTitleId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForUpdate(clientTitleId, command.json());

        final ClientTitle clientTitle = findWithNotFoundDetection(clientTitleId);
        final JsonObject json = command.parsedJson().getAsJsonObject();
        if (json.has(ClientTitleCommandFromApiJsonDeserializer.TITLE_CODE)) {
            final String titleCode = this.fromApiJsonHelper.extractStringNamed(ClientTitleCommandFromApiJsonDeserializer.TITLE_CODE, json);
            if (this.clientTitleRepository.existsByTitleCodeAndIdNot(titleCode, clientTitleId)) {
                throw new PlatformDataIntegrityException("error.msg.client.title.duplicate.code",
                        "Client title with code `" + titleCode + "` already exists.", "titleCode", titleCode);
            }
        }
        mapFromJson(clientTitle, json, false);
        this.clientTitleRepository.saveAndFlush(clientTitle);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(clientTitle.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteClientTitle(final Long clientTitleId, final JsonCommand command) {
        this.context.authenticatedUser();
        final ClientTitle clientTitle = findWithNotFoundDetection(clientTitleId);
        try {
            this.clientTitleRepository.delete(clientTitle);
        } catch (final DataIntegrityViolationException e) {
            throw new PlatformDataIntegrityException("error.msg.client.title.in.use",
                    "Client title cannot be deleted because it is referenced by other records.");
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(clientTitleId).build();
    }

    @Override
    public ClientTitle findWithNotFoundDetection(final Long clientTitleId) {
        return this.clientTitleRepository.findById(clientTitleId).orElseThrow(() -> new ClientTitleNotFoundException(clientTitleId));
    }

    @Override
    public void validateTitleForGender(final Long titleId, final Integer genderEnum) {
        if (titleId == null) {
            return;
        }
        final ClientTitle clientTitle = findWithNotFoundDetection(titleId);
        if (clientTitle.getGenderEnum() != null && genderEnum != null && !clientTitle.getGenderEnum().equals(genderEnum)) {
            final ApiParameterError error = ApiParameterError.parameterError("validation.msg.client.titleId.gender.mismatch",
                    "The selected title is not valid for the customer's gender.", "titleId", titleId);
            throw new PlatformApiDataValidationException(List.of(error));
        }
    }

    private ClientTitle mapFromJson(final ClientTitle clientTitle, final JsonObject json, final boolean create) {
        if (create || json.has(ClientTitleCommandFromApiJsonDeserializer.TITLE_CODE)) {
            clientTitle.setTitleCode(
                    this.fromApiJsonHelper.extractStringNamed(ClientTitleCommandFromApiJsonDeserializer.TITLE_CODE, json));
        }
        if (create || json.has(ClientTitleCommandFromApiJsonDeserializer.TITLE_NAME)) {
            clientTitle.setTitleName(
                    this.fromApiJsonHelper.extractStringNamed(ClientTitleCommandFromApiJsonDeserializer.TITLE_NAME, json));
        }
        if (json.has(ClientTitleCommandFromApiJsonDeserializer.GENDER_ID)) {
            clientTitle.setGenderEnum(
                    this.fromApiJsonHelper.extractIntegerSansLocaleNamed(ClientTitleCommandFromApiJsonDeserializer.GENDER_ID, json));
        }
        if (json.has(ClientTitleCommandFromApiJsonDeserializer.DISPLAY_ORDER)) {
            clientTitle.setDisplayOrder(
                    this.fromApiJsonHelper.extractIntegerSansLocaleNamed(ClientTitleCommandFromApiJsonDeserializer.DISPLAY_ORDER, json));
        }
        if (create || json.has(ClientTitleCommandFromApiJsonDeserializer.STATUS)) {
            final String status = this.fromApiJsonHelper.extractStringNamed(ClientTitleCommandFromApiJsonDeserializer.STATUS, json);
            if (status != null) {
                clientTitle.setStatus(status);
            }
        }
        return clientTitle;
    }
}
