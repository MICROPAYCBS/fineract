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
package org.apache.fineract.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.apache.fineract.treasury.data.TreasuryAccountData;
import org.apache.fineract.treasury.data.TreasuryPositionData;
import org.apache.fineract.treasury.service.TreasuryBalanceReader.Pending;
import org.junit.jupiter.api.Test;

class TreasuryPendingTest {

    @Test
    void pendingAmountsAdd() {
        final Pending total = new Pending(new BigDecimal("100"), BigDecimal.ZERO).plus(new Pending(BigDecimal.ZERO, new BigDecimal("40")))
                .plus(Pending.NONE);

        assertEquals(new BigDecimal("100"), total.in());
        assertEquals(new BigDecimal("40"), total.out());
    }

    @Test
    void projectedBalanceUsesPendingButLevelUsesTheGlBalance() {
        final TreasuryAccountData account = new TreasuryAccountData(1L, "VAULT", "Vault", "VAULT", 10L, "1101", "Vault", 1L, "Head Office",
                "UGX", null, null, new BigDecimal("100"), new BigDecimal("1000"), "ACTIVE", null, null);

        final TreasuryPositionData position = TreasuryPositionCalculator.position(account, new BigDecimal("900"), new BigDecimal("300"),
                new BigDecimal("50"), null);

        assertEquals(new BigDecimal("1150"), position.projectedBalance());
        assertEquals("OK", position.levelStatus());
        assertEquals(new BigDecimal("300"), position.pendingIn());
        assertEquals(new BigDecimal("50"), position.pendingOut());
    }
}
