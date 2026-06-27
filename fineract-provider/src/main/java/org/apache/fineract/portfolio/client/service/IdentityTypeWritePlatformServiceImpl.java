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
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.api.IdentityTypeConstants;
import org.apache.fineract.portfolio.client.domain.IdentityType;
import org.apache.fineract.portfolio.client.domain.IdentityTypeRepository;
import org.apache.fineract.portfolio.client.exception.IdentityTypeNotFoundException;
import org.apache.fineract.portfolio.client.serialization.IdentityTypeCommandFromApiJsonDeserializer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdentityTypeWritePlatformServiceImpl implements IdentityTypeWritePlatformService {

    private final PlatformSecurityContext context;
    private final IdentityTypeRepository identityTypeRepository;
    private final CodeValueRepositoryWrapper codeValueRepository;
    private final IdentityTypeCommandFromApiJsonDeserializer apiJsonDeserializer;
    private final FromJsonHelper fromApiJsonHelper;

    @Override
    @Transactional
    public CommandProcessingResult createIdentityType(final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForCreate(command.json());

        final JsonObject json = command.parsedJson().getAsJsonObject();
        final Long codeValueId = this.fromApiJsonHelper.extractLongNamed(IdentityTypeCommandFromApiJsonDeserializer.CODE_VALUE_ID, json);
        if (this.identityTypeRepository.existsByCodeValueId(codeValueId)) {
            throw new PlatformDataIntegrityException("error.msg.identity.type.duplicate.code.value",
                    "Identity type configuration already exists for code value `" + codeValueId + "`.",
                    IdentityTypeCommandFromApiJsonDeserializer.CODE_VALUE_ID, codeValueId);
        }

        final IdentityType identityType = mapFromJson(new IdentityType(), json, true);
        identityType.setCodeValue(resolveCustomerIdentifierCodeValue(codeValueId));
        this.identityTypeRepository.saveAndFlush(identityType);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(identityType.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateIdentityType(final Long identityTypeId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForUpdate(identityTypeId, command.json());

        final IdentityType identityType = findWithNotFoundDetection(identityTypeId);
        final JsonObject json = command.parsedJson().getAsJsonObject();
        if (json.has(IdentityTypeCommandFromApiJsonDeserializer.CODE_VALUE_ID)) {
            final Long codeValueId = this.fromApiJsonHelper.extractLongNamed(IdentityTypeCommandFromApiJsonDeserializer.CODE_VALUE_ID,
                    json);
            if (this.identityTypeRepository.existsByCodeValueIdAndIdNot(codeValueId, identityTypeId)) {
                throw new PlatformDataIntegrityException("error.msg.identity.type.duplicate.code.value",
                        "Identity type configuration already exists for code value `" + codeValueId + "`.",
                        IdentityTypeCommandFromApiJsonDeserializer.CODE_VALUE_ID, codeValueId);
            }
            identityType.setCodeValue(resolveCustomerIdentifierCodeValue(codeValueId));
        }
        mapFromJson(identityType, json, false);
        this.identityTypeRepository.saveAndFlush(identityType);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(identityType.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteIdentityType(final Long identityTypeId, final JsonCommand command) {
        this.context.authenticatedUser();
        final IdentityType identityType = findWithNotFoundDetection(identityTypeId);
        try {
            this.identityTypeRepository.delete(identityType);
        } catch (final DataIntegrityViolationException e) {
            throw new PlatformDataIntegrityException("error.msg.identity.type.in.use",
                    "Identity type configuration cannot be deleted because it is referenced by other records.");
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(identityTypeId).build();
    }

    @Override
    public IdentityType findWithNotFoundDetection(final Long identityTypeId) {
        return this.identityTypeRepository.findById(identityTypeId).orElseThrow(() -> new IdentityTypeNotFoundException(identityTypeId));
    }

    private CodeValue resolveCustomerIdentifierCodeValue(final Long codeValueId) {
        final CodeValue codeValue = this.codeValueRepository.findOneWithNotFoundDetection(codeValueId);
        if (codeValue.getCode() == null
                || !IdentityTypeConstants.CUSTOMER_IDENTIFIER_CODE_NAME.equals(codeValue.getCode().getName())) {
            final List<ApiParameterError> errors = new ArrayList<>();
            errors.add(ApiParameterError.parameterError("validation.msg.identity.type.invalid.code.value",
                    "Code value must belong to the `" + IdentityTypeConstants.CUSTOMER_IDENTIFIER_CODE_NAME + "` code list.",
                    IdentityTypeCommandFromApiJsonDeserializer.CODE_VALUE_ID, codeValueId));
            throw new PlatformApiDataValidationException(errors);
        }
        return codeValue;
    }

    private IdentityType mapFromJson(final IdentityType identityType, final JsonObject json, final boolean create) {
        if (json.has(IdentityTypeCommandFromApiJsonDeserializer.EXAMPLE)) {
            identityType.setExample(this.fromApiJsonHelper.extractStringNamed(IdentityTypeCommandFromApiJsonDeserializer.EXAMPLE, json));
        }
        if (json.has(IdentityTypeCommandFromApiJsonDeserializer.FORMAT_DESCRIPTION)) {
            identityType.setFormatDescription(
                    this.fromApiJsonHelper.extractStringNamed(IdentityTypeCommandFromApiJsonDeserializer.FORMAT_DESCRIPTION, json));
        }
        if (json.has(IdentityTypeCommandFromApiJsonDeserializer.VALIDATION_MESSAGE)) {
            identityType.setValidationMessage(
                    this.fromApiJsonHelper.extractStringNamed(IdentityTypeCommandFromApiJsonDeserializer.VALIDATION_MESSAGE, json));
        }
        if (json.has(IdentityTypeCommandFromApiJsonDeserializer.VALIDATION_REGEX)) {
            identityType.setValidationRegex(
                    this.fromApiJsonHelper.extractStringNamed(IdentityTypeCommandFromApiJsonDeserializer.VALIDATION_REGEX, json));
        }
        if (json.has(IdentityTypeCommandFromApiJsonDeserializer.DISPLAY_ORDER)) {
            identityType.setDisplayOrder(this.fromApiJsonHelper
                    .extractIntegerSansLocaleNamed(IdentityTypeCommandFromApiJsonDeserializer.DISPLAY_ORDER, json));
        }
        if (create || json.has(IdentityTypeCommandFromApiJsonDeserializer.STATUS)) {
            final String status = this.fromApiJsonHelper.extractStringNamed(IdentityTypeCommandFromApiJsonDeserializer.STATUS, json);
            if (StringUtils.isNotBlank(status)) {
                identityType.setStatus(status);
            }
        }
        return identityType;
    }
}
