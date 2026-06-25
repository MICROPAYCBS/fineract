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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.client.domain.ClientContact;
import org.apache.fineract.portfolio.client.domain.ClientContactRepository;
import org.apache.fineract.portfolio.client.domain.ContactType;
import org.apache.fineract.portfolio.client.domain.ContactTypeRepository;
import org.apache.fineract.portfolio.client.serialization.ClientContactCommandFromApiJsonDeserializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientContactValidationServiceImpl implements ClientContactValidationService {

    private final ContactTypeRepository contactTypeRepository;
    private final ClientContactRepository clientContactRepository;

    @Override
    public void validateContactValue(final ContactType contactType, final String value) {
        if (contactType == null || StringUtils.isBlank(value)) {
            return;
        }
        final String regex = contactType.getValidationRegex();
        if (StringUtils.isBlank(regex)) {
            return;
        }
        try {
            if (!Pattern.compile(regex).matcher(value.trim()).matches()) {
                final String message = StringUtils.defaultIfBlank(contactType.getExample(),
                        "Contact value does not match the required format for type `" + contactType.getTypeName() + "`.");
                final ApiParameterError error = ApiParameterError.parameterError("validation.msg.client.contact.invalid.format", message,
                        ClientContactCommandFromApiJsonDeserializer.CONTACT_VALUE, value);
                throw new PlatformApiDataValidationException(List.of(error));
            }
        } catch (final PatternSyntaxException e) {
            final ApiParameterError error = ApiParameterError.parameterError("validation.msg.contact.type.invalid.regex.config",
                    "Contact type validation regex is invalid.", ClientContactCommandFromApiJsonDeserializer.CONTACT_VALUE, value);
            throw new PlatformApiDataValidationException(List.of(error));
        }
    }

    @Override
    @Transactional
    public void clearOtherPrimaryFlags(final Long clientId, final Long contactTypeId, final Long exceptContactId) {
        final List<ClientContact> primaryContacts = this.clientContactRepository.findByClient_IdAndContactTypeIdAndIsPrimary(clientId,
                contactTypeId, "Y");
        for (final ClientContact contact : primaryContacts) {
            if (exceptContactId == null || !exceptContactId.equals(contact.getId())) {
                contact.setPrimary(false);
                this.clientContactRepository.save(contact);
            }
        }
    }

    @Override
    public void validateMandatoryContactsForClient(final Long clientId) {
        final List<ContactType> mandatoryTypes = this.contactTypeRepository.findByStatusAndMandatoryInd("ACTIVE", "Y");
        final List<ApiParameterError> errors = new ArrayList<>();
        for (final ContactType contactType : mandatoryTypes) {
            final long count = this.clientContactRepository.countByClient_IdAndContactTypeId(clientId, contactType.getId());
            if (count < 1) {
                errors.add(ApiParameterError.parameterError("validation.msg.client.contact.mandatory.missing",
                        "Client must have at least one contact of type `" + contactType.getTypeName() + "`.",
                        ClientContactCommandFromApiJsonDeserializer.CONTACT_TYPE_ID, contactType.getId()));
            }
        }
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }
}
