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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;

/**
 * Works out the journal lines of a treasury event. Fineract journal entries belong to one office each, so every office
 * in a plan must balance on its own.
 */
public final class TreasuryPostingPlanner {

    private TreasuryPostingPlanner() {}

    public record Leg(Long officeId, Long glAccountId, JournalEntryType type, BigDecimal amount) {
    }

    /**
     * Send: debit cash in transit and credit the source account, both in the source office.
     */
    public static List<Leg> transferSend(final Long sourceOfficeId, final Long sourceGlAccountId, final Long inTransitGlAccountId,
            final BigDecimal amount) {
        return List.of(new Leg(sourceOfficeId, inTransitGlAccountId, JournalEntryType.DEBIT, amount),
                new Leg(sourceOfficeId, sourceGlAccountId, JournalEntryType.CREDIT, amount));
    }

    /**
     * Receive: debit the destination account in its office and clear cash in transit in the source office, where the
     * send put it. Across offices, each office is balanced through the inter-branch clearing account.
     *
     * @param clearingGlAccountId
     *            needed only when the offices differ
     */
    public static List<Leg> transferReceive(final Long sourceOfficeId, final Long destinationOfficeId, final Long destinationGlAccountId,
            final Long inTransitGlAccountId, final Long clearingGlAccountId, final BigDecimal amount) {
        final List<Leg> legs = new ArrayList<>();
        legs.add(new Leg(destinationOfficeId, destinationGlAccountId, JournalEntryType.DEBIT, amount));
        if (!Objects.equals(sourceOfficeId, destinationOfficeId)) {
            Objects.requireNonNull(clearingGlAccountId, "clearingGlAccountId");
            legs.add(new Leg(destinationOfficeId, clearingGlAccountId, JournalEntryType.CREDIT, amount));
            legs.add(new Leg(sourceOfficeId, clearingGlAccountId, JournalEntryType.DEBIT, amount));
        }
        legs.add(new Leg(sourceOfficeId, inTransitGlAccountId, JournalEntryType.CREDIT, amount));
        return legs;
    }

    /**
     * Debits less credits per office; every value is zero for a valid plan.
     */
    public static Map<Long, BigDecimal> netPerOffice(final List<Leg> legs) {
        final Map<Long, BigDecimal> net = new LinkedHashMap<>();
        for (final Leg leg : legs) {
            final BigDecimal signed = leg.type() == JournalEntryType.DEBIT ? leg.amount() : leg.amount().negate();
            net.merge(leg.officeId(), signed, BigDecimal::add);
        }
        return net;
    }

    public static boolean isBalancedPerOffice(final List<Leg> legs) {
        return netPerOffice(legs).values().stream().allMatch(value -> value.compareTo(BigDecimal.ZERO) == 0);
    }
}
