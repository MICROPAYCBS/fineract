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

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The default treasury roles {@code apply-template} creates when absent, each with the permissions it gets: every role
 * reads all of treasury and its reports; the writes are split so that the one who makes a change is not the one who
 * approves it.
 */
public final class TreasuryRoles {

    /** The permission groupings of the module. */
    public static final List<String> GROUPINGS = List.of("treasury", "treasury_risk", "treasury_fx", "treasury_trust");

    private static final Set<String> DEAL_ACTIONS = Set.of("CREATE", "UPDATE", "CONFIRM", "CANCEL", "ROLLOVER", "TERMINATE", "PLEDGE",
            "RELEASEPLEDGE");
    private static final Set<String> OPERATIONS_ENTITIES = Set.of("TREASURYACCOUNT", "TREASURYTRANSFER", "TREASURYAGENTFLOAT",
            "TREASURYFLOATREQUEST", "TREASURYSTATEMENT", "TREASURYSTATEMENTFORMAT", "TREASURYSTATEMENTLINE", "TREASURYRECONMATCH",
            "TREASURYSETTING");

    /** A permission row as the roles see it. */
    public record Permission(String grouping, String code, String entity, String action) {

        boolean read() {
            return "READ".equals(this.action)
                    && (GROUPINGS.contains(this.grouping) || "report".equals(this.grouping) && this.code.startsWith("READ_Treasury "));
        }

        boolean write() {
            return GROUPINGS.contains(this.grouping) && !"READ".equals(this.action) && !this.action.endsWith("_CHECKER");
        }

        boolean checker() {
            return GROUPINGS.contains(this.grouping) && this.action.endsWith("_CHECKER");
        }
    }

    /** A default role. */
    public record Role(String name, String description, Predicate<Permission> grants, boolean pspOnly) {
    }

    public static final List<Role> ROLES = List.of(
            new Role("Treasury dealer", "Treasury: captures and manages deals, prices, FX rates and transfer requests",
                    p -> p.write() && ("treasury".equals(p.grouping()) && Set.of("TREASURYDEAL", "TREASURYPRICE").contains(p.entity())
                            && DEAL_ACTIONS.contains(p.action())
                            || Set.of("CREATE_TREASURYFXRATE", "CREATE_TREASURYTRANSFER").contains(p.code())),
                    false),
            new Role("Treasury approver", "Treasury: approves and checks what the others make (every _CHECKER, approvals and rejections)",
                    p -> p.checker() || p.write() && Set.of("APPROVE", "REJECT", "APPROVE_OVERLIMIT").contains(p.action()), false),
            new Role("Treasury operations",
                    "Treasury: accounts, transfers, agent floats, statements and reconciliation, settlement of deals and cash flows",
                    p -> p.write() && ("treasury".equals(p.grouping()) && OPERATIONS_ENTITIES.contains(p.entity())
                            && !Set.of("APPROVE", "REVERSE").contains(p.action())
                            || Set.of("SETTLE_TREASURYDEAL", "SETTLECASHFLOW_TREASURYDEAL").contains(p.code())),
                    false),
            new Role("Treasury risk and compliance",
                    "Treasury: rule sets, assumptions, runs and the return, limits and breaches, stress and the CFP, FX configuration, ECL, setup",
                    p -> p.write() && (Set.of("treasury_risk", "treasury_fx").contains(p.grouping())
                            || Set.of("UPDATE_TREASURYECLPARAMETER", "SETECLSTAGE_TREASURYDEAL", "RECALCULATEECL_TREASURYDEAL")
                                    .contains(p.code())),
                    false),
            new Role("Trust and reconciliation officer", "Treasury (PSP): the e-money trust accounts, reconciliation and the trust return",
                    p -> p.write() && "treasury_trust".equals(p.grouping()), true));

    private TreasuryRoles() {}

    /** Whether the role gets the permission: every role reads; the writes as the role grants them. */
    public static boolean grants(final Role role, final Permission permission) {
        return permission.read() || role.grants().test(permission);
    }
}
