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
package org.apache.fineract.infrastructure.interbranch.service;

import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.interbranch.domain.OfficeServicingAccess;
import org.apache.fineract.infrastructure.interbranch.domain.OfficeServicingAccessRepository;
import org.apache.fineract.infrastructure.interbranch.exception.OfficeServicingAccessNotFoundException;
import org.apache.fineract.infrastructure.interbranch.serialization.OfficeServicingAccessCommandFromApiJsonDeserializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OfficeServicingAccessWritePlatformServiceImpl implements OfficeServicingAccessWritePlatformService {

    private final PlatformSecurityContext context;
    private final OfficeServicingAccessRepository officeServicingAccessRepository;
    private final OfficeRepositoryWrapper officeRepository;
    private final OfficeServicingAccessCommandFromApiJsonDeserializer apiJsonDeserializer;
    private final FromJsonHelper fromApiJsonHelper;

    @Override
    @Transactional
    public CommandProcessingResult createOfficeServicingAccess(final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForCreate(command.json());

        final JsonObject json = command.parsedJson().getAsJsonObject();
        final OfficeServicingAccess access = mapFromJson(new OfficeServicingAccess(), json, true);
        validateOffices(access);
        try {
            this.officeServicingAccessRepository.saveAndFlush(access);
        } catch (final DataIntegrityViolationException e) {
            throw new PlatformDataIntegrityException("error.msg.office.servicing.access.duplicate",
                    "An access rule already exists for this servicing office, book office, and effective date.");
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(access.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateOfficeServicingAccess(final Long accessId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForUpdate(accessId, command.json());

        final OfficeServicingAccess access = findWithNotFoundDetection(accessId);
        final JsonObject json = command.parsedJson().getAsJsonObject();
        mapFromJson(access, json, false);
        validateOffices(access);
        try {
            this.officeServicingAccessRepository.saveAndFlush(access);
        } catch (final DataIntegrityViolationException e) {
            throw new PlatformDataIntegrityException("error.msg.office.servicing.access.duplicate",
                    "An access rule already exists for this servicing office, book office, and effective date.");
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(access.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteOfficeServicingAccess(final Long accessId, final JsonCommand command) {
        this.context.authenticatedUser();
        final OfficeServicingAccess access = findWithNotFoundDetection(accessId);
        this.officeServicingAccessRepository.delete(access);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(accessId).build();
    }

    @Override
    public OfficeServicingAccess findWithNotFoundDetection(final Long accessId) {
        return this.officeServicingAccessRepository.findById(accessId)
                .orElseThrow(() -> new OfficeServicingAccessNotFoundException(accessId));
    }

    private OfficeServicingAccess mapFromJson(final OfficeServicingAccess access, final JsonObject json, final boolean create) {
        if (create || json.has(OfficeServicingAccessCommandFromApiJsonDeserializer.SERVICING_OFFICE_ID)) {
            final Long servicingOfficeId = this.fromApiJsonHelper
                    .extractLongNamed(OfficeServicingAccessCommandFromApiJsonDeserializer.SERVICING_OFFICE_ID, json);
            access.setServicingOffice(this.officeRepository.findOneWithNotFoundDetection(servicingOfficeId));
        }
        if (create || json.has(OfficeServicingAccessCommandFromApiJsonDeserializer.BOOK_OFFICE_ID)) {
            final Long bookOfficeId = this.fromApiJsonHelper.extractLongNamed(OfficeServicingAccessCommandFromApiJsonDeserializer.BOOK_OFFICE_ID,
                    json);
            access.setBookOffice(this.officeRepository.findOneWithNotFoundDetection(bookOfficeId));
        }
        if (create || json.has(OfficeServicingAccessCommandFromApiJsonDeserializer.EFFECTIVE_FROM)) {
            access.setEffectiveFrom(this.fromApiJsonHelper
                    .extractLocalDateNamed(OfficeServicingAccessCommandFromApiJsonDeserializer.EFFECTIVE_FROM, json));
        }
        if (json.has(OfficeServicingAccessCommandFromApiJsonDeserializer.EFFECTIVE_TO)) {
            access.setEffectiveTo(this.fromApiJsonHelper.extractLocalDateNamed(OfficeServicingAccessCommandFromApiJsonDeserializer.EFFECTIVE_TO,
                    json));
        }
        if (create || json.has(OfficeServicingAccessCommandFromApiJsonDeserializer.STATUS)) {
            final String status = this.fromApiJsonHelper.extractStringNamed(OfficeServicingAccessCommandFromApiJsonDeserializer.STATUS,
                    json);
            if (StringUtils.isNotBlank(status)) {
                access.setStatus(status);
            }
        }
        return access;
    }

    private void validateOffices(final OfficeServicingAccess access) {
        if (access.getServicingOffice() != null && access.getBookOffice() != null
                && Objects.equals(access.getServicingOffice().getId(), access.getBookOffice().getId())) {
            throw new PlatformDataIntegrityException("error.msg.office.servicing.access.same.office",
                    "Servicing office and book office must differ.");
        }
        if (access.getEffectiveFrom() == null) {
            access.setEffectiveFrom(DateUtils.getBusinessLocalDate());
        }
        final LocalDate effectiveTo = access.getEffectiveTo();
        if (effectiveTo != null && effectiveTo.isBefore(access.getEffectiveFrom())) {
            throw new PlatformDataIntegrityException("error.msg.office.servicing.access.invalid.date.range",
                    "Effective to date cannot be before effective from date.");
        }
    }
}
