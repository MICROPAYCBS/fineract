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

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.entityaccess.domain.FineractEntityAccessType;
import org.apache.fineract.infrastructure.entityaccess.domain.FineractEntityRelation;
import org.apache.fineract.infrastructure.entityaccess.domain.FineractEntityRelationRepositoryWrapper;
import org.apache.fineract.infrastructure.entityaccess.domain.FineractEntityToEntityMapping;
import org.apache.fineract.infrastructure.entityaccess.domain.FineractEntityToEntityMappingRepository;
import org.apache.fineract.infrastructure.entityaccess.exception.DepartmentNotMappedToOfficeException;
import org.apache.fineract.portfolio.department.domain.Department;
import org.apache.fineract.portfolio.department.domain.DepartmentRepository;
import org.apache.fineract.portfolio.department.exception.DepartmentNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OfficeDepartmentMappingValidator {

    private final DepartmentRepository departmentRepository;
    private final FineractEntityRelationRepositoryWrapper fineractEntityRelationRepositoryWrapper;
    private final FineractEntityToEntityMappingRepository entityMappingRepository;

    /**
     * Ensures department exists, is active, and is mapped to the office for the given as-of date (transaction date).
     */
    public void validateDepartmentAvailableForOffice(final Long departmentId, final Long officeId, final LocalDate asOfDate) {
        final Department department = this.departmentRepository.findById(departmentId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));
        if (!department.isActive()) {
            throw new DepartmentNotFoundException(departmentId);
        }
        if (!isMapped(officeId, departmentId, asOfDate != null ? asOfDate : DateUtils.getBusinessLocalDate())) {
            throw new DepartmentNotMappedToOfficeException(departmentId, officeId);
        }
    }

    public boolean isMapped(final Long officeId, final Long departmentId, final LocalDate asOfDate) {
        final FineractEntityRelation relation = this.fineractEntityRelationRepositoryWrapper
                .findOneByCodeName(FineractEntityAccessType.OFFICE_ACCESS_TO_DEPARTMENTS.getStr());
        final FineractEntityToEntityMapping mapping = this.entityMappingRepository.findListByProductId(relation, departmentId, officeId);
        if (mapping == null) {
            return false;
        }
        final LocalDate effective = asOfDate != null ? asOfDate : DateUtils.getBusinessLocalDate();
        if (mapping.getStartDate() != null && DateUtils.isBefore(effective, mapping.getStartDate())) {
            return false;
        }
        if (mapping.getEndDate() != null && DateUtils.isAfter(effective, mapping.getEndDate())) {
            return false;
        }
        return true;
    }
}
