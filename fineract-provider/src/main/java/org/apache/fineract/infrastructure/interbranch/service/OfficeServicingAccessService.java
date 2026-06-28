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

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.interbranch.domain.OfficeServicingAccessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfficeServicingAccessService {

    private final OfficeServicingAccessRepository officeServicingAccessRepository;

    public boolean canServeBookOffice(final Long servicingOfficeId, final Long bookOfficeId) {
        if (servicingOfficeId == null || bookOfficeId == null || servicingOfficeId.equals(bookOfficeId)) {
            return false;
        }
        final LocalDate onDate = DateUtils.getBusinessLocalDate();
        return this.officeServicingAccessRepository.existsActiveAccess(servicingOfficeId, bookOfficeId, onDate);
    }
}
