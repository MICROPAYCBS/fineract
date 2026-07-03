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

public interface CashierAccessReadService {

    /**
     * True when the current user may list or view any cashier in their office hierarchy ({@code READ_TELLER}).
     */
    boolean canReadAllCashiersForCurrentUser();

    /**
     * True when the current user may view only the cashier linked to their staff record ({@code READ_MY_CASHIER}).
     */
    boolean canReadOwnCashierForCurrentUser();

    /**
     * Ensures the current user has at least one cashier/teller read permission.
     */
    void validateCanAccessTellerOrCashierRead();

    /**
     * Ensures the current user may read teller/cashier admin templates ({@code READ_TELLER} only).
     */
    void validateCanAccessCashierAdminRead();

    /**
     * Ensures the current user may read the cashier with the given staff assignment. Returns 404 when a self-read user
     * requests another staff member's cashier.
     */
    void validateCanReadCashier(Long cashierStaffId, Long cashierId);

    /**
     * When the user has only {@code READ_MY_CASHIER}, returns their staff id for SQL filtering; otherwise {@code null}.
     */
    Long selfReadStaffIdFilterOrNull();
}
