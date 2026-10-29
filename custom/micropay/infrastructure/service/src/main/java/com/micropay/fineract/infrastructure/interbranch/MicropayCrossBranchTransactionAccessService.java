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
package com.micropay.fineract.infrastructure.interbranch;

import com.micropay.fineract.platform.configuration.MicropayConfigurationService;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.interbranch.api.CrossBranchServicingConstants;
import org.apache.fineract.infrastructure.interbranch.service.CrossBranchTransactionAccessService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MicropayCrossBranchTransactionAccessService implements CrossBranchTransactionAccessService {

    private static final ThreadLocal<Office> SERVICING_OFFICE_OVERRIDE = new ThreadLocal<>();

    private final MicropayConfigurationService micropayConfigurationService;
    private final PlatformSecurityContext context;

    /**
     * Names the office that services the next savings post. A no-op when cross-branch servicing is off, so the line
     * falls back to the authenticated user's office. Callers clear the override in a finally block.
     */
    public void setServicingOfficeOverride(final Office office) {
        if (office == null || !this.micropayConfigurationService.isCrossBranchServicingEnabled()) {
            SERVICING_OFFICE_OVERRIDE.remove();
            return;
        }
        SERVICING_OFFICE_OVERRIDE.set(office);
    }

    public void clearServicingOfficeOverride() {
        SERVICING_OFFICE_OVERRIDE.remove();
    }

    public <T> T withServicingOffice(final Office office, final Supplier<T> action) {
        setServicingOfficeOverride(office);
        try {
            return action.get();
        } finally {
            clearServicingOfficeOverride();
        }
    }

    @Override
    public boolean isCrossBranchTransactionEnabledForCurrentUser() {
        if (!this.micropayConfigurationService.isCrossBranchServicingEnabled()) {
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

        final Office override = SERVICING_OFFICE_OVERRIDE.get();
        final Office servicingOffice = override != null ? override : user.getOffice();
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
