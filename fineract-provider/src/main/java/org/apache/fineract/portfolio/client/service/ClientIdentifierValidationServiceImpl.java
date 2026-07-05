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
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;
import org.apache.fineract.portfolio.client.domain.ClientIdentifierRepository;
import org.apache.fineract.portfolio.client.domain.ClientIdentifierStatus;
import org.apache.fineract.portfolio.client.domain.IdentityType;
import org.apache.fineract.portfolio.client.domain.IdentityTypeRepository;
import org.apache.fineract.portfolio.client.domain.LegalForm;
import org.apache.fineract.portfolio.client.serialization.ClientIdentifierCommandFromApiJsonDeserializer;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientIdentifierValidationServiceImpl implements ClientIdentifierValidationService {

    private final IdentityTypeRepository identityTypeRepository;
    private final ClientIdentifierRepository clientIdentifierRepository;
    private final FromJsonHelper fromApiJsonHelper;

    @Override
    public void validateDocumentKey(final Long documentTypeId, final String documentKey) {
        if (documentTypeId == null || StringUtils.isBlank(documentKey)) {
            return;
        }
        final IdentityType identityType = this.identityTypeRepository.findByCodeValueId(documentTypeId).orElse(null);
        if (identityType == null || !"ACTIVE".equalsIgnoreCase(identityType.getStatus())) {
            return;
        }
        final String regex = identityType.getValidationRegex();
        if (StringUtils.isBlank(regex)) {
            return;
        }
        try {
            if (!Pattern.compile(regex).matcher(documentKey.trim()).matches()) {
                final String message = resolveValidationMessage(identityType);
                final ApiParameterError error = ApiParameterError.parameterError("validation.msg.client.identifier.invalid.format", message,
                        ClientIdentifierCommandFromApiJsonDeserializer.DOCUMENT_KEY, documentKey);
                throw new PlatformApiDataValidationException(List.of(error));
            }
        } catch (final PatternSyntaxException e) {
            final ApiParameterError error = ApiParameterError.parameterError("validation.msg.identity.type.invalid.regex.config",
                    "Identity type validation regex is invalid.", ClientIdentifierCommandFromApiJsonDeserializer.DOCUMENT_KEY,
                    documentKey);
            throw new PlatformApiDataValidationException(List.of(error));
        }
    }

    @Override
    public void validateAtLeastOneIdentifierForPersonCreate(final JsonElement clientCreateElement, final Integer legalFormId) {
        if (!isPersonLegalForm(legalFormId)) {
            return;
        }
        final JsonArray identifiers = this.fromApiJsonHelper.extractJsonArrayNamed(ClientApiConstants.clientIdentifiers,
                clientCreateElement);
        if (countValidIdentifiersInPayload(identifiers) < 1) {
            throw new PlatformApiDataValidationException(List.of(ApiParameterError.parameterError(
                    "validation.msg.client.identifiers.required",
                    "At least one client identifier is required when onboarding a person customer.",
                    ClientApiConstants.clientIdentifiers, identifiers)));
        }
    }

    @Override
    public void validateAtLeastOneIdentifierForPersonClient(final Long clientId, final Integer legalFormId) {
        if (!isPersonLegalForm(legalFormId) || clientId == null) {
            return;
        }
        final long identifierCount = this.clientIdentifierRepository.countByClient_IdAndStatus(clientId,
                ClientIdentifierStatus.ACTIVE.getValue());
        if (identifierCount < 1) {
            throw new PlatformApiDataValidationException(List.of(ApiParameterError.parameterError(
                    "validation.msg.client.identifiers.required",
                    "At least one client identifier is required when onboarding a person customer.",
                    ClientApiConstants.clientIdentifiers, null)));
        }
    }

    private boolean isPersonLegalForm(final Integer legalFormId) {
        return legalFormId != null && LegalForm.PERSON.getValue().equals(legalFormId);
    }

    private int countValidIdentifiersInPayload(final JsonArray identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return 0;
        }
        int validCount = 0;
        for (final JsonElement identifierElement : identifiers) {
            if (!identifierElement.isJsonObject()) {
                continue;
            }
            final JsonObject identifierObject = identifierElement.getAsJsonObject();
            final Long documentTypeId = this.fromApiJsonHelper.extractLongNamed(
                    ClientIdentifierCommandFromApiJsonDeserializer.DOCUMENT_TYPE_ID, identifierObject);
            final String documentKey = this.fromApiJsonHelper
                    .extractStringNamed(ClientIdentifierCommandFromApiJsonDeserializer.DOCUMENT_KEY, identifierObject);
            if (documentTypeId != null && documentTypeId > 0 && StringUtils.isNotBlank(documentKey)) {
                validCount++;
            }
        }
        return validCount;
    }

    private String resolveValidationMessage(final IdentityType identityType) {
        if (StringUtils.isNotBlank(identityType.getValidationMessage())) {
            return identityType.getValidationMessage();
        }
        if (StringUtils.isNotBlank(identityType.getFormatDescription())) {
            final String example = identityType.getExample();
            if (StringUtils.isNotBlank(example)) {
                return identityType.getFormatDescription() + " Example: " + example;
            }
            return identityType.getFormatDescription();
        }
        if (StringUtils.isNotBlank(identityType.getExample())) {
            return "Document number must match the required format. Example: " + identityType.getExample();
        }
        final String typeName = identityType.getCodeValue() != null ? identityType.getCodeValue().getLabel() : "selected identity type";
        return "Document number does not match the required format for `" + typeName + "`.";
    }
}
