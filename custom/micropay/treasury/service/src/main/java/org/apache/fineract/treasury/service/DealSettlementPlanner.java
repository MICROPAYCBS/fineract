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
import java.util.List;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.treasury.deal.DealPricer.Priced;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;

/**
 * Journal lines of a deal settlement, all in the settlement account's office.
 *
 * <ul>
 * <li>Asset deals: Dr principal (the consideration; face value for bonds), for bonds Dr premium or Cr discount and Dr
 * interest receivable for the accrued coupon bought; Cr the settlement account for the consideration.</li>
 * <li>Liability deals: Dr the settlement account / Cr principal.</li>
 * <li>Fees we pay: Dr fee expense / Cr the settlement account.</li>
 * </ul>
 */
public final class DealSettlementPlanner {

    private DealSettlementPlanner() {}

    public record DealGlAccounts(Long principal, Long interestReceivable, Long premium, Long discount, Long feeExpense) {
    }

    public static List<Leg> legs(final InstrumentKind kind, final Priced priced, final BigDecimal fees, final Long officeId,
            final Long settlementGlAccountId, final DealGlAccounts gl) {
        final List<Leg> legs = new ArrayList<>();
        if (kind.isAsset()) {
            legs.add(new Leg(officeId, gl.principal(), JournalEntryType.DEBIT, priced.principalAmount()));
            add(legs, officeId, gl.premium(), JournalEntryType.DEBIT, priced.premium());
            add(legs, officeId, gl.discount(), JournalEntryType.CREDIT, priced.discount());
            add(legs, officeId, gl.interestReceivable(), JournalEntryType.DEBIT, priced.accruedInterest());
            legs.add(new Leg(officeId, settlementGlAccountId, JournalEntryType.CREDIT, priced.consideration()));
        } else {
            legs.add(new Leg(officeId, settlementGlAccountId, JournalEntryType.DEBIT, priced.consideration()));
            legs.add(new Leg(officeId, gl.principal(), JournalEntryType.CREDIT, priced.consideration()));
        }
        if (fees != null && fees.signum() > 0) {
            legs.add(new Leg(officeId, gl.feeExpense(), JournalEntryType.DEBIT, fees));
            legs.add(new Leg(officeId, settlementGlAccountId, JournalEntryType.CREDIT, fees));
        }
        return legs;
    }

    /**
     * Cash leaving the settlement account at settlement: consideration plus fees for asset deals, fees only otherwise.
     */
    public static BigDecimal cashOut(final InstrumentKind kind, final Priced priced, final BigDecimal fees) {
        final BigDecimal fee = fees == null ? BigDecimal.ZERO : fees;
        return kind.isAsset() ? priced.consideration().add(fee) : fee;
    }

    private static void add(final List<Leg> legs, final Long officeId, final Long glAccountId, final JournalEntryType type,
            final BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            if (glAccountId == null) {
                throw new TreasuryWorkflowException("deal", "gl.account.missing",
                        "No GL account for a " + type + " of " + amount.toPlainString() + ": set it on the deal type", type.name(), amount);
            }
            legs.add(new Leg(officeId, glAccountId, type, amount));
        }
    }
}
