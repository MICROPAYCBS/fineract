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
package org.apache.fineract.portfolio.department.service;

import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.portfolio.department.domain.Department;
import org.apache.fineract.portfolio.department.domain.DepartmentRepository;
import org.apache.fineract.portfolio.department.exception.DepartmentNotFoundException;
import org.apache.fineract.portfolio.department.serialization.DepartmentCommandFromApiJsonDeserializer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartmentWritePlatformServiceImpl implements DepartmentWritePlatformService {

    private final PlatformSecurityContext context;
    private final DepartmentRepository departmentRepository;
    private final DepartmentCommandFromApiJsonDeserializer apiJsonDeserializer;
    private final FromJsonHelper fromApiJsonHelper;
    private final OfficeReadPlatformService officeReadPlatformService;

    @Override
    @Transactional
    public CommandProcessingResult createDepartment(final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForCreate(command.json());

        final JsonObject json = command.parsedJson().getAsJsonObject();
        final String departmentCode = this.fromApiJsonHelper.extractStringNamed(DepartmentCommandFromApiJsonDeserializer.DEPARTMENT_CODE,
                json);
        if (this.departmentRepository.existsByDepartmentCode(departmentCode)) {
            throw new PlatformDataIntegrityException("error.msg.department.duplicate.code",
                    "Department with code `" + departmentCode + "` already exists.",
                    DepartmentCommandFromApiJsonDeserializer.DEPARTMENT_CODE, departmentCode);
        }

        final Department department = mapFromJson(new Department(), json, true);
        validateOffice(department.getOfficeId());
        this.departmentRepository.saveAndFlush(department);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(department.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateDepartment(final Long departmentId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForUpdate(departmentId, command.json());

        final Department department = findWithNotFoundDetection(departmentId);
        final JsonObject json = command.parsedJson().getAsJsonObject();
        if (json.has(DepartmentCommandFromApiJsonDeserializer.DEPARTMENT_CODE)) {
            final String departmentCode = this.fromApiJsonHelper
                    .extractStringNamed(DepartmentCommandFromApiJsonDeserializer.DEPARTMENT_CODE, json);
            if (this.departmentRepository.existsByDepartmentCodeAndIdNot(departmentCode, departmentId)) {
                throw new PlatformDataIntegrityException("error.msg.department.duplicate.code",
                        "Department with code `" + departmentCode + "` already exists.",
                        DepartmentCommandFromApiJsonDeserializer.DEPARTMENT_CODE, departmentCode);
            }
        }
        mapFromJson(department, json, false);
        validateOffice(department.getOfficeId());
        this.departmentRepository.saveAndFlush(department);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(department.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteDepartment(final Long departmentId, final JsonCommand command) {
        this.context.authenticatedUser();
        final Department department = findWithNotFoundDetection(departmentId);
        try {
            this.departmentRepository.delete(department);
        } catch (final DataIntegrityViolationException e) {
            throw new PlatformDataIntegrityException("error.msg.department.in.use",
                    "Department cannot be deleted because it is referenced by other records.");
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(departmentId).build();
    }

    @Override
    public Department findWithNotFoundDetection(final Long departmentId) {
        return this.departmentRepository.findById(departmentId).orElseThrow(() -> new DepartmentNotFoundException(departmentId));
    }

    private void validateOffice(final Long officeId) {
        if (officeId != null) {
            this.officeReadPlatformService.retrieveOffice(officeId);
        }
    }

    private Department mapFromJson(final Department department, final JsonObject json, final boolean create) {
        if (create || json.has(DepartmentCommandFromApiJsonDeserializer.DEPARTMENT_CODE)) {
            department.setDepartmentCode(
                    this.fromApiJsonHelper.extractStringNamed(DepartmentCommandFromApiJsonDeserializer.DEPARTMENT_CODE, json));
        }
        if (create || json.has(DepartmentCommandFromApiJsonDeserializer.DEPARTMENT_NAME)) {
            department.setDepartmentName(
                    this.fromApiJsonHelper.extractStringNamed(DepartmentCommandFromApiJsonDeserializer.DEPARTMENT_NAME, json));
        }
        if (json.has(DepartmentCommandFromApiJsonDeserializer.OFFICE_ID)) {
            department.setOfficeId(this.fromApiJsonHelper.extractLongNamed(DepartmentCommandFromApiJsonDeserializer.OFFICE_ID, json));
        }
        if (create || json.has(DepartmentCommandFromApiJsonDeserializer.ACTIVE)) {
            final Boolean active = this.fromApiJsonHelper.extractBooleanNamed(DepartmentCommandFromApiJsonDeserializer.ACTIVE, json);
            if (active != null) {
                department.setActive(active);
            }
        }
        return department;
    }
}
