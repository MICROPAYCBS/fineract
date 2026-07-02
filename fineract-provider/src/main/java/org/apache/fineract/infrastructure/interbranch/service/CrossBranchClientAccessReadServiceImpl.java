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
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.interbranch.api.CrossBranchServicingConstants;
import org.apache.fineract.infrastructure.interbranch.domain.OfficeServicingAccessRepository;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrossBranchClientAccessReadServiceImpl implements CrossBranchClientAccessReadService {

    private final ConfigurationDomainService configurationDomainService;
    private final PlatformSecurityContext context;
    private final OfficeServicingAccessRepository officeServicingAccessRepository;

    @Override
    public boolean isCrossBranchClientAccessEnabledForCurrentUser() {
        if (!this.configurationDomainService.isCrossBranchServicingEnabled()) {
            return false;
        }
        final AppUser user = this.context.authenticatedUser();
        return user.hasSpecificPermissionTo(CrossBranchServicingConstants.VIEW_OTHER_BRANCH_CLIENT_PERMISSION);
    }

    @Override
    public List<Long> retrieveAccessibleBookOfficeIds(final Long servicingOfficeId) {
        if (servicingOfficeId == null) {
            return Collections.emptyList();
        }
        final LocalDate onDate = DateUtils.getBusinessLocalDate();
        return this.officeServicingAccessRepository.findAccessibleBookOfficeIds(servicingOfficeId, onDate);
    }

    @Override
    public List<Long> accessibleBookOfficeIdsForCurrentUser() {
        if (!this.configurationDomainService.isCrossBranchServicingEnabled()) {
            return Collections.emptyList();
        }
        final AppUser user = this.context.getAuthenticatedUserIfPresent();
        if (user == null || !user.hasSpecificPermissionTo(CrossBranchServicingConstants.VIEW_OTHER_BRANCH_CLIENT_PERMISSION)) {
            return Collections.emptyList();
        }
        return retrieveAccessibleBookOfficeIds(user.getOffice().getId());
    }
}
