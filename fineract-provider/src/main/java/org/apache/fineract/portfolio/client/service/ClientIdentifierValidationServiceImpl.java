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

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.client.domain.IdentityType;
import org.apache.fineract.portfolio.client.domain.IdentityTypeRepository;
import org.apache.fineract.portfolio.client.serialization.ClientIdentifierCommandFromApiJsonDeserializer;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientIdentifierValidationServiceImpl implements ClientIdentifierValidationService {

    private final IdentityTypeRepository identityTypeRepository;

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
