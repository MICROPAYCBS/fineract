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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import org.apache.fineract.infrastructure.entityaccess.domain.FineractEntityAccessType;
import org.apache.fineract.infrastructure.entityaccess.domain.FineractEntityRelation;
import org.apache.fineract.infrastructure.entityaccess.domain.FineractEntityRelationRepositoryWrapper;
import org.apache.fineract.infrastructure.entityaccess.domain.FineractEntityToEntityMapping;
import org.apache.fineract.infrastructure.entityaccess.domain.FineractEntityToEntityMappingRepository;
import org.apache.fineract.infrastructure.entityaccess.exception.DepartmentNotMappedToOfficeException;
import org.apache.fineract.portfolio.department.domain.Department;
import org.apache.fineract.portfolio.department.domain.DepartmentRepository;
import org.apache.fineract.portfolio.department.exception.DepartmentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficeDepartmentMappingValidatorTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private FineractEntityRelationRepositoryWrapper fineractEntityRelationRepositoryWrapper;

    @Mock
    private FineractEntityToEntityMappingRepository entityMappingRepository;

    @InjectMocks
    private OfficeDepartmentMappingValidator validator;

    private final FineractEntityRelation relation = new FineractEntityRelation();
    private Department department;

    @BeforeEach
    void setUp() {
        this.department = new Department();
        this.department.setDepartmentCode("IT");
        this.department.setDepartmentName("Information Technology");
        this.department.setActive(true);
    }

    @Test
    void acceptsMappedActiveDepartment() {
        when(this.departmentRepository.findById(3L)).thenReturn(Optional.of(this.department));
        when(this.fineractEntityRelationRepositoryWrapper.findOneByCodeName(FineractEntityAccessType.OFFICE_ACCESS_TO_DEPARTMENTS.getStr()))
                .thenReturn(this.relation);
        when(this.entityMappingRepository.findListByProductId(this.relation, 3L, 1L))
                .thenReturn(FineractEntityToEntityMapping.newMap(this.relation, 1L, 3L, null, null));

        this.validator.validateDepartmentAvailableForOffice(3L, 1L, LocalDate.of(2026, 7, 21));
    }

    @Test
    void rejectsUnmappedDepartment() {
        when(this.departmentRepository.findById(3L)).thenReturn(Optional.of(this.department));
        when(this.fineractEntityRelationRepositoryWrapper.findOneByCodeName(anyString())).thenReturn(this.relation);
        when(this.entityMappingRepository.findListByProductId(eq(this.relation), eq(3L), eq(1L))).thenReturn(null);

        assertThatThrownBy(() -> this.validator.validateDepartmentAvailableForOffice(3L, 1L, LocalDate.of(2026, 7, 21)))
                .isInstanceOf(DepartmentNotMappedToOfficeException.class);
    }

    @Test
    void rejectsUnknownDepartment() {
        when(this.departmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.validator.validateDepartmentAvailableForOffice(99L, 1L, LocalDate.of(2026, 7, 21)))
                .isInstanceOf(DepartmentNotFoundException.class);
    }

    @Test
    void rejectsInactiveDepartment() {
        this.department.setActive(false);
        when(this.departmentRepository.findById(3L)).thenReturn(Optional.of(this.department));

        assertThatThrownBy(() -> this.validator.validateDepartmentAvailableForOffice(3L, 1L, LocalDate.of(2026, 7, 21)))
                .isInstanceOf(DepartmentNotFoundException.class);
    }

    @Test
    void rejectsMappingOutsideDateWindow() {
        when(this.departmentRepository.findById(3L)).thenReturn(Optional.of(this.department));
        when(this.fineractEntityRelationRepositoryWrapper.findOneByCodeName(anyString())).thenReturn(this.relation);
        when(this.entityMappingRepository.findListByProductId(this.relation, 3L, 1L)).thenReturn(FineractEntityToEntityMapping
                .newMap(this.relation, 1L, 3L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));

        assertThatThrownBy(() -> this.validator.validateDepartmentAvailableForOffice(3L, 1L, LocalDate.of(2026, 7, 21)))
                .isInstanceOf(DepartmentNotMappedToOfficeException.class);
    }

    @Test
    void isMappedTrueWhenWithinInclusiveWindow() {
        when(this.fineractEntityRelationRepositoryWrapper.findOneByCodeName(anyString())).thenReturn(this.relation);
        when(this.entityMappingRepository.findListByProductId(any(), eq(3L), eq(1L))).thenReturn(FineractEntityToEntityMapping
                .newMap(this.relation, 1L, 3L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        assertThat(this.validator.isMapped(1L, 3L, LocalDate.of(2026, 7, 21))).isTrue();
        verify(this.entityMappingRepository).findListByProductId(this.relation, 3L, 1L);
    }
}
