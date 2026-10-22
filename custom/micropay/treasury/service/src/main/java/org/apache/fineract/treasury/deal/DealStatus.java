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
package org.apache.fineract.treasury.deal;

/**
 * Life of a deal: CAPTURED, APPROVED, CONFIRMED (by the counterparty), SETTLED (cash moved); REJECTED at approval, or
 * CANCELLED before settlement. MATURED, TERMINATED and ROLLED_OVER follow settlement and come with accrual and maturity
 * processing.
 */
public enum DealStatus {

    CAPTURED, //
    APPROVED, //
    CONFIRMED, //
    SETTLED, //
    REJECTED, //
    CANCELLED, //
    MATURED, //
    TERMINATED, //
    ROLLED_OVER; //

    public boolean canUpdate() {
        return this == CAPTURED;
    }

    public boolean canApprove() {
        return this == CAPTURED;
    }

    public boolean canReject() {
        return this == CAPTURED;
    }

    public boolean canConfirm() {
        return this == APPROVED;
    }

    public boolean canSettle() {
        return this == CONFIRMED;
    }

    public boolean canCancel() {
        return this == CAPTURED || this == APPROVED || this == CONFIRMED;
    }

    /** Deals that use a counterparty's limit. */
    public boolean countsTowardsExposure() {
        return this == APPROVED || this == CONFIRMED || this == SETTLED;
    }
}
