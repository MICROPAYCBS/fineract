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
package org.apache.fineract.organisation.teller.service;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.teller.api.TellerCashierAccessConstants;
import org.apache.fineract.organisation.teller.exception.CashierNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CashierAccessReadServiceImpl implements CashierAccessReadService {

    private static final String INSUFFICIENT_CASHIER_READ = "Insufficient permission to read cashier data.";
    private static final String INSUFFICIENT_TELLER_READ = "Insufficient permission to read teller data.";

    private final PlatformSecurityContext context;

    @Override
    public boolean canReadAllCashiersForCurrentUser() {
        final AppUser user = this.context.authenticatedUser();
        return !user.hasNotPermissionForAnyOf("ALL_FUNCTIONS", "ALL_FUNCTIONS_READ",
                TellerCashierAccessConstants.READ_TELLER_PERMISSION);
    }

    @Override
    public boolean canReadOwnCashierForCurrentUser() {
        return this.context.authenticatedUser().hasSpecificPermissionTo(TellerCashierAccessConstants.READ_MY_CASHIER_PERMISSION);
    }

    @Override
    public void validateCanAccessTellerOrCashierRead() {
        if (canReadAllCashiersForCurrentUser() || canReadOwnCashierForCurrentUser()) {
            return;
        }
        throw new NoAuthorizationException(INSUFFICIENT_TELLER_READ);
    }

    @Override
    public void validateCanAccessCashierAdminRead() {
        if (canReadAllCashiersForCurrentUser()) {
            return;
        }
        throw new NoAuthorizationException(INSUFFICIENT_CASHIER_READ);
    }

    @Override
    public void validateCanReadCashier(final Long cashierStaffId, final Long cashierId) {
        validateCanAccessTellerOrCashierRead();
        if (canReadAllCashiersForCurrentUser()) {
            return;
        }
        final Long userStaffId = this.context.authenticatedUser().getStaffId();
        if (userStaffId == null || !userStaffId.equals(cashierStaffId)) {
            throw new CashierNotFoundException(cashierId);
        }
    }

    @Override
    public Long selfReadStaffIdFilterOrNull() {
        if (canReadAllCashiersForCurrentUser()) {
            return null;
        }
        if (canReadOwnCashierForCurrentUser()) {
            return this.context.authenticatedUser().getStaffId();
        }
        return null;
    }
}
