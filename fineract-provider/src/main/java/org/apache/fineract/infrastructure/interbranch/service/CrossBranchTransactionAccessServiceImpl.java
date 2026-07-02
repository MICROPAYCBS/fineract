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

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.interbranch.api.CrossBranchServicingConstants;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrossBranchTransactionAccessServiceImpl implements CrossBranchTransactionAccessService {

    private final ConfigurationDomainService configurationDomainService;
    private final PlatformSecurityContext context;

    @Override
    public boolean isCrossBranchTransactionEnabledForCurrentUser() {
        if (!this.configurationDomainService.isCrossBranchServicingEnabled()) {
            return false;
        }
        final AppUser user = this.context.getAuthenticatedUserIfPresent();
        return user != null && user.hasSpecificPermissionTo(CrossBranchServicingConstants.TRANSACT_CROSSOFFICE_PERMISSION);
    }

    @Override
    public Optional<Office> resolveTransactionOffice(final Office homeOffice) {
        final AppUser user = this.context.getAuthenticatedUserIfPresent();
        if (user == null || homeOffice == null) {
            return Optional.empty();
        }

        final Office servicingOffice = user.getOffice();
        if (servicingOffice.getId().equals(homeOffice.getId())) {
            return Optional.empty();
        }

        final String homeHierarchy = homeOffice.getHierarchy();
        final String servicingHierarchy = servicingOffice.getHierarchy();
        if (homeHierarchy.startsWith(servicingHierarchy)) {
            return Optional.empty();
        }

        validateCrossBranchTransaction();
        return Optional.of(servicingOffice);
    }

    private void validateCrossBranchTransaction() {
        if (!isCrossBranchTransactionEnabledForCurrentUser()) {
            throw new NoAuthorizationException("Cross-branch transactions are not enabled for the current user.");
        }
    }
}
