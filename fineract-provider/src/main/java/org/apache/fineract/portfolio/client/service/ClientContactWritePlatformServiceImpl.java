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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientContact;
import org.apache.fineract.portfolio.client.domain.ClientContactRepository;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ContactType;
import org.apache.fineract.portfolio.client.exception.ClientContactNotFoundException;
import org.apache.fineract.portfolio.client.serialization.ClientContactCommandFromApiJsonDeserializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientContactWritePlatformServiceImpl implements ClientContactWritePlatformService {

    private final PlatformSecurityContext context;
    private final ClientRepositoryWrapper clientRepository;
    private final ClientContactRepository clientContactRepository;
    private final ContactTypeWritePlatformService contactTypeWritePlatformService;
    private final ClientContactValidationService clientContactValidationService;
    private final ClientContactCommandFromApiJsonDeserializer apiJsonDeserializer;
    private final FromJsonHelper fromApiJsonHelper;

    @Override
    @Transactional
    public CommandProcessingResult createClientContact(final Long clientId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForCreate(command.json());

        final Client client = this.clientRepository.findOneWithNotFoundDetection(clientId);
        final JsonObject json = command.parsedJson().getAsJsonObject();
        final Long contactTypeId = this.fromApiJsonHelper.extractLongNamed(ClientContactCommandFromApiJsonDeserializer.CONTACT_TYPE_ID,
                json);
        final ContactType contactType = this.contactTypeWritePlatformService.findWithNotFoundDetection(contactTypeId);
        final String contactValue = this.fromApiJsonHelper.extractStringNamed(ClientContactCommandFromApiJsonDeserializer.CONTACT_VALUE,
                json);
        this.clientContactValidationService.validateContactValue(contactType, contactValue);

        final Boolean primary = this.fromApiJsonHelper.extractBooleanNamed(ClientContactCommandFromApiJsonDeserializer.PRIMARY, json);
        if (Boolean.TRUE.equals(primary)) {
            this.clientContactValidationService.clearOtherPrimaryFlags(clientId, contactTypeId, null);
        }

        final ClientContact clientContact = new ClientContact();
        clientContact.setClient(client);
        clientContact.setContactTypeId(contactTypeId);
        clientContact.setContactValue(contactValue);
        clientContact.setPrimary(primary);
        this.clientContactRepository.saveAndFlush(clientContact);

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withOfficeId(client.officeId())
                .withClientId(clientId).withEntityId(clientContact.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult addClientContacts(final Client client, final JsonCommand command) {
        this.context.authenticatedUser();

        final JsonArray contacts = command.arrayOfParameterNamed(ClientApiConstants.contacts);
        if (contacts == null || contacts.isEmpty()) {
            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withOfficeId(client.officeId())
                    .withClientId(client.getId()).build();
        }

        validateSinglePrimaryPerTypeInBatch(contacts);

        ClientContact lastSaved = null;
        for (final JsonElement contactElement : contacts) {
            this.apiJsonDeserializer.validateForCreate(contactElement.toString());

            final JsonObject json = contactElement.getAsJsonObject();
            final Long contactTypeId = this.fromApiJsonHelper.extractLongNamed(ClientContactCommandFromApiJsonDeserializer.CONTACT_TYPE_ID,
                    json);
            final ContactType contactType = this.contactTypeWritePlatformService.findWithNotFoundDetection(contactTypeId);
            final String contactValue = this.fromApiJsonHelper.extractStringNamed(ClientContactCommandFromApiJsonDeserializer.CONTACT_VALUE,
                    json);
            this.clientContactValidationService.validateContactValue(contactType, contactValue);

            final Boolean primary = this.fromApiJsonHelper.extractBooleanNamed(ClientContactCommandFromApiJsonDeserializer.PRIMARY, json);
            if (Boolean.TRUE.equals(primary)) {
                this.clientContactValidationService.clearOtherPrimaryFlags(client.getId(), contactTypeId, null);
            }

            final ClientContact clientContact = new ClientContact();
            clientContact.setClient(client);
            clientContact.setContactTypeId(contactTypeId);
            clientContact.setContactValue(contactValue);
            clientContact.setPrimary(primary);
            this.clientContactRepository.saveAndFlush(clientContact);
            lastSaved = clientContact;
        }

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withOfficeId(client.officeId())
                .withClientId(client.getId()).withEntityId(lastSaved != null ? lastSaved.getId() : null).build();
    }

    private void validateSinglePrimaryPerTypeInBatch(final JsonArray contacts) {
        final Set<Long> primaryContactTypeIds = new HashSet<>();
        for (final JsonElement contactElement : contacts) {
            final JsonObject json = contactElement.getAsJsonObject();
            final Boolean primary = this.fromApiJsonHelper.extractBooleanNamed(ClientContactCommandFromApiJsonDeserializer.PRIMARY, json);
            if (!Boolean.TRUE.equals(primary)) {
                continue;
            }
            final Long contactTypeId = this.fromApiJsonHelper.extractLongNamed(ClientContactCommandFromApiJsonDeserializer.CONTACT_TYPE_ID,
                    json);
            if (contactTypeId != null && !primaryContactTypeIds.add(contactTypeId)) {
                final ApiParameterError error = ApiParameterError.parameterError(
                        "validation.msg.client.contact.duplicate.primary.for.type",
                        "Only one primary contact is allowed per contact type in the contacts array.",
                        ClientContactCommandFromApiJsonDeserializer.PRIMARY, contactTypeId);
                throw new PlatformApiDataValidationException(List.of(error));
            }
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult updateClientContact(final Long clientId, final Long clientContactId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForUpdate(clientContactId, command.json());

        final Client client = this.clientRepository.findOneWithNotFoundDetection(clientId);
        final ClientContact clientContact = findClientContactWithNotFoundDetection(clientId, clientContactId);
        final JsonObject json = command.parsedJson().getAsJsonObject();

        Long contactTypeId = clientContact.getContactTypeId();
        if (json.has(ClientContactCommandFromApiJsonDeserializer.CONTACT_TYPE_ID)) {
            contactTypeId = this.fromApiJsonHelper.extractLongNamed(ClientContactCommandFromApiJsonDeserializer.CONTACT_TYPE_ID, json);
            this.contactTypeWritePlatformService.findWithNotFoundDetection(contactTypeId);
            clientContact.setContactTypeId(contactTypeId);
        }

        String contactValue = clientContact.getContactValue();
        if (json.has(ClientContactCommandFromApiJsonDeserializer.CONTACT_VALUE)) {
            contactValue = this.fromApiJsonHelper.extractStringNamed(ClientContactCommandFromApiJsonDeserializer.CONTACT_VALUE, json);
            clientContact.setContactValue(contactValue);
        }

        final ContactType contactType = this.contactTypeWritePlatformService.findWithNotFoundDetection(contactTypeId);
        this.clientContactValidationService.validateContactValue(contactType, contactValue);

        if (json.has(ClientContactCommandFromApiJsonDeserializer.PRIMARY)) {
            final Boolean primary = this.fromApiJsonHelper.extractBooleanNamed(ClientContactCommandFromApiJsonDeserializer.PRIMARY, json);
            if (Boolean.TRUE.equals(primary)) {
                this.clientContactValidationService.clearOtherPrimaryFlags(clientId, contactTypeId, clientContactId);
            }
            clientContact.setPrimary(primary);
        }

        this.clientContactRepository.saveAndFlush(clientContact);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withOfficeId(client.officeId())
                .withClientId(clientId).withEntityId(clientContactId).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteClientContact(final Long clientId, final Long clientContactId, final Long commandId) {
        this.context.authenticatedUser();
        final Client client = this.clientRepository.findOneWithNotFoundDetection(clientId);
        final ClientContact clientContact = findClientContactWithNotFoundDetection(clientId, clientContactId);
        this.clientContactRepository.delete(clientContact);
        return new CommandProcessingResultBuilder().withCommandId(commandId).withOfficeId(client.officeId()).withClientId(clientId)
                .withEntityId(clientContactId).build();
    }

    private ClientContact findClientContactWithNotFoundDetection(final Long clientId, final Long clientContactId) {
        final ClientContact clientContact = this.clientContactRepository.findById(clientContactId)
                .orElseThrow(() -> new ClientContactNotFoundException(clientContactId));
        if (!clientId.equals(clientContact.getClient().getId())) {
            throw new ClientContactNotFoundException(clientContactId);
        }
        return clientContact;
    }
}
