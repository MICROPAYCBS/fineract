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
package org.apache.fineract.infrastructure.entityaccess.data;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.department.domain.Department;
import org.apache.fineract.portfolio.department.domain.DepartmentRepository;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsProductRepository;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FineractEntityDataValidatorOfficeDepartmentTest {

    @Mock
    private FromJsonHelper fromApiJsonHelper;
    @Mock
    private OfficeRepositoryWrapper officeRepositoryWrapper;
    @Mock
    private LoanProductRepository loanProductRepository;
    @Mock
    private SavingsProductRepository savingsProductRepository;
    @Mock
    private ChargeRepositoryWrapper chargeRepositoryWrapper;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    private FineractEntityDataValidator validator;

    @BeforeEach
    void setUp() {
        this.validator = new FineractEntityDataValidator(this.fromApiJsonHelper, this.officeRepositoryWrapper, this.loanProductRepository,
                this.savingsProductRepository, this.chargeRepositoryWrapper, this.roleRepository, this.departmentRepository);
    }

    @Test
    void checkForEntityRel6ValidatesOfficeAndDepartment() {
        when(this.departmentRepository.findById(3L)).thenReturn(Optional.of(new Department()));

        this.validator.checkForEntity("6", 1L, 3L);

        verify(this.officeRepositoryWrapper).findOneWithNotFoundDetection(1L);
        verify(this.departmentRepository).findById(3L);
    }
}
