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
package org.apache.fineract.treasury.setup;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The default roles split making from approving. */
class TreasuryRolesTest {

    private static TreasuryRoles.Role role(final String name) {
        return TreasuryRoles.ROLES.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }

    private static TreasuryRoles.Permission p(final String grouping, final String code, final String entity, final String action) {
        return new TreasuryRoles.Permission(grouping, code, entity, action);
    }

    @Test
    void theDealerMakesDealsButApprovesNothing() {
        final TreasuryRoles.Role dealer = role("Treasury dealer");
        assertTrue(TreasuryRoles.grants(dealer, p("treasury", "CREATE_TREASURYDEAL", "TREASURYDEAL", "CREATE")));
        assertTrue(TreasuryRoles.grants(dealer, p("treasury_fx", "CREATE_TREASURYFXRATE", "TREASURYFXRATE", "CREATE")));
        assertFalse(TreasuryRoles.grants(dealer, p("treasury", "APPROVE_TREASURYDEAL", "TREASURYDEAL", "APPROVE")));
        assertFalse(TreasuryRoles.grants(dealer, p("treasury", "CREATE_TREASURYDEAL_CHECKER", "TREASURYDEAL", "CREATE_CHECKER")));
        assertFalse(TreasuryRoles.grants(dealer, p("treasury", "SETTLE_TREASURYDEAL", "TREASURYDEAL", "SETTLE")));
    }

    @Test
    void theApproverChecksAndApprovesButMakesNothing() {
        final TreasuryRoles.Role approver = role("Treasury approver");
        assertTrue(TreasuryRoles.grants(approver, p("treasury", "CREATE_TREASURYDEAL_CHECKER", "TREASURYDEAL", "CREATE_CHECKER")));
        assertTrue(TreasuryRoles.grants(approver, p("treasury", "APPROVE_TREASURYTRANSFER", "TREASURYTRANSFER", "APPROVE")));
        assertFalse(TreasuryRoles.grants(approver, p("treasury", "CREATE_TREASURYDEAL", "TREASURYDEAL", "CREATE")));
    }

    @Test
    void everyRoleReadsTreasuryAndItsReportsOnly() {
        for (final TreasuryRoles.Role r : TreasuryRoles.ROLES) {
            assertTrue(TreasuryRoles.grants(r, p("treasury_risk", "READ_TREASURYRUN", "TREASURYRUN", "READ")));
            assertTrue(TreasuryRoles.grants(r, p("report", "READ_Treasury Maturity Ladder", "Treasury Maturity Ladder", "READ")));
            assertFalse(TreasuryRoles.grants(r, p("report", "READ_Loan Summary", "Loan Summary", "READ")));
            assertFalse(TreasuryRoles.grants(r, p("portfolio", "CREATE_LOAN", "LOAN", "CREATE")));
        }
        assertTrue(TreasuryRoles.grants(role("Treasury risk and compliance"),
                p("treasury_risk", "APPLYTEMPLATE_TREASURYSETUP", "TREASURYSETUP", "APPLYTEMPLATE")));
        assertTrue(role("Trust and reconciliation officer").pspOnly());
    }
}
