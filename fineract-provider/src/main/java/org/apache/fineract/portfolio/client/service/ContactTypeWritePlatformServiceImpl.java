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
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.ContactType;
import org.apache.fineract.portfolio.client.domain.ContactTypeRepository;
import org.apache.fineract.portfolio.client.exception.ContactTypeNotFoundException;
import org.apache.fineract.portfolio.client.serialization.ContactTypeCommandFromApiJsonDeserializer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactTypeWritePlatformServiceImpl implements ContactTypeWritePlatformService {

    private final PlatformSecurityContext context;
    private final ContactTypeRepository contactTypeRepository;
    private final ContactTypeCommandFromApiJsonDeserializer apiJsonDeserializer;
    private final FromJsonHelper fromApiJsonHelper;

    @Override
    @Transactional
    public CommandProcessingResult createContactType(final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForCreate(command.json());

        final JsonObject json = command.parsedJson().getAsJsonObject();
        final String typeCode = this.fromApiJsonHelper.extractStringNamed(ContactTypeCommandFromApiJsonDeserializer.TYPE_CODE, json);
        if (this.contactTypeRepository.existsByTypeCode(typeCode)) {
            throw new PlatformDataIntegrityException("error.msg.contact.type.duplicate.code",
                    "Contact type with code `" + typeCode + "` already exists.", ContactTypeCommandFromApiJsonDeserializer.TYPE_CODE,
                    typeCode);
        }

        final ContactType contactType = mapFromJson(new ContactType(), json, true);
        this.contactTypeRepository.saveAndFlush(contactType);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(contactType.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateContactType(final Long contactTypeId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForUpdate(contactTypeId, command.json());

        final ContactType contactType = findWithNotFoundDetection(contactTypeId);
        final JsonObject json = command.parsedJson().getAsJsonObject();
        if (json.has(ContactTypeCommandFromApiJsonDeserializer.TYPE_CODE)) {
            final String typeCode = this.fromApiJsonHelper.extractStringNamed(ContactTypeCommandFromApiJsonDeserializer.TYPE_CODE, json);
            if (this.contactTypeRepository.existsByTypeCodeAndIdNot(typeCode, contactTypeId)) {
                throw new PlatformDataIntegrityException("error.msg.contact.type.duplicate.code",
                        "Contact type with code `" + typeCode + "` already exists.", ContactTypeCommandFromApiJsonDeserializer.TYPE_CODE,
                        typeCode);
            }
        }
        mapFromJson(contactType, json, false);
        this.contactTypeRepository.saveAndFlush(contactType);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(contactType.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteContactType(final Long contactTypeId, final JsonCommand command) {
        this.context.authenticatedUser();
        final ContactType contactType = findWithNotFoundDetection(contactTypeId);
        try {
            this.contactTypeRepository.delete(contactType);
        } catch (final DataIntegrityViolationException e) {
            throw new PlatformDataIntegrityException("error.msg.contact.type.in.use",
                    "Contact type cannot be deleted because it is referenced by other records.");
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(contactTypeId).build();
    }

    @Override
    public ContactType findWithNotFoundDetection(final Long contactTypeId) {
        return this.contactTypeRepository.findById(contactTypeId).orElseThrow(() -> new ContactTypeNotFoundException(contactTypeId));
    }

    private ContactType mapFromJson(final ContactType contactType, final JsonObject json, final boolean create) {
        if (create || json.has(ContactTypeCommandFromApiJsonDeserializer.TYPE_CODE)) {
            contactType.setTypeCode(
                    this.fromApiJsonHelper.extractStringNamed(ContactTypeCommandFromApiJsonDeserializer.TYPE_CODE, json));
        }
        if (create || json.has(ContactTypeCommandFromApiJsonDeserializer.TYPE_NAME)) {
            contactType.setTypeName(
                    this.fromApiJsonHelper.extractStringNamed(ContactTypeCommandFromApiJsonDeserializer.TYPE_NAME, json));
        }
        if (json.has(ContactTypeCommandFromApiJsonDeserializer.EXAMPLE)) {
            contactType.setExample(this.fromApiJsonHelper.extractStringNamed(ContactTypeCommandFromApiJsonDeserializer.EXAMPLE, json));
        }
        if (json.has(ContactTypeCommandFromApiJsonDeserializer.VALIDATION_REGEX)) {
            contactType.setValidationRegex(
                    this.fromApiJsonHelper.extractStringNamed(ContactTypeCommandFromApiJsonDeserializer.VALIDATION_REGEX, json));
        }
        if (json.has(ContactTypeCommandFromApiJsonDeserializer.MANDATORY)) {
            contactType.setMandatory(this.fromApiJsonHelper.extractBooleanNamed(ContactTypeCommandFromApiJsonDeserializer.MANDATORY, json));
        }
        if (json.has(ContactTypeCommandFromApiJsonDeserializer.DISPLAY_ORDER)) {
            contactType.setDisplayOrder(
                    this.fromApiJsonHelper.extractIntegerSansLocaleNamed(ContactTypeCommandFromApiJsonDeserializer.DISPLAY_ORDER, json));
        }
        if (create || json.has(ContactTypeCommandFromApiJsonDeserializer.STATUS)) {
            final String status = this.fromApiJsonHelper.extractStringNamed(ContactTypeCommandFromApiJsonDeserializer.STATUS, json);
            if (status != null) {
                contactType.setStatus(status);
            }
        }
        return contactType;
    }
}
