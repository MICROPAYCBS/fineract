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
import org.apache.fineract.treasury.deal.DealCalculator.FlowType;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.deal.InterestMethod;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;

/**
 * Journal lines of a settled deal's life after settlement: accruals, cash flows, the release of what is left at
 * maturity, and early termination. All lines are in the deal's office; amounts of either sign are accepted where a
 * reversal can occur and posted the other way round.
 */
public final class DealLifecyclePlanner {

    private DealLifecyclePlanner() {}

    public record Gl(Long settlement, Long principal, Long interestReceivable, Long interestPayable, Long interestIncome,
            Long interestExpense, Long premium, Long discount, Long deferredFee, Long feeExpense, Long realisedGain, Long realisedLoss) {
    }

    /**
     * Interest: asset deals Dr interest receivable / Cr interest income (bills: Dr principal, as the bill's carrying
     * amount grows to face); liability deals Dr interest expense / Cr interest payable. Bond amortisation: accreting a
     * discount Dr discount / Cr income, amortising a premium Dr income / Cr premium. Fees: Dr fee expense / Cr deferred
     * fee.
     */
    public static List<Leg> accrual(final InstrumentKind kind, final BigDecimal interest, final BigDecimal amortisation,
            final BigDecimal feeAmortisation, final boolean premiumBond, final Long officeId, final Gl gl) {
        final List<Leg> legs = new ArrayList<>();
        if (kind.isAsset()) {
            final Long debit = kind.interestMethod() == InterestMethod.DISCOUNT_YIELD ? gl.principal() : gl.interestReceivable();
            pair(legs, officeId, debit, gl.interestIncome(), interest);
        } else {
            pair(legs, officeId, gl.interestExpense(), gl.interestPayable(), interest);
        }
        if (amortisation.signum() != 0) {
            final Long account = premiumBond ? gl.premium() : gl.discount();
            pair(legs, officeId, account, gl.interestIncome(), amortisation);
        }
        pair(legs, officeId, gl.feeExpense(), gl.deferredFee(), feeAmortisation);
        return legs;
    }

    /**
     * Cash flow received (asset deals: Dr settlement account / Cr interest receivable or principal) or paid (liability
     * deals: Dr interest payable or principal / Cr settlement account). A bill's face is credited to its principal
     * account, which has grown to face by then.
     */
    public static List<Leg> cashflow(final InstrumentKind kind, final FlowType type, final BigDecimal amount, final Long officeId,
            final Gl gl) {
        final List<Leg> legs = new ArrayList<>();
        if (kind.isAsset()) {
            pair(legs, officeId, gl.settlement(), type == FlowType.PRINCIPAL ? gl.principal() : gl.interestReceivable(), amount);
        } else {
            pair(legs, officeId, type == FlowType.PRINCIPAL ? gl.principal() : gl.interestPayable(), gl.settlement(), amount);
        }
        return legs;
    }

    /**
     * At maturity whatever premium, discount or deferred fee is left (bonds at fair value through profit or loss, or
     * rounding) is released: discount Dr discount / Cr income; premium Dr income / Cr premium; fee Dr fee expense / Cr
     * deferred fee.
     */
    public static List<Leg> residual(final BigDecimal unamortisedPremium, final BigDecimal unamortisedDiscount, final BigDecimal fee,
            final Long officeId, final Gl gl) {
        final List<Leg> legs = new ArrayList<>();
        pair(legs, officeId, gl.discount(), gl.interestIncome(), unamortisedDiscount);
        pair(legs, officeId, gl.interestIncome(), gl.premium(), unamortisedPremium);
        pair(legs, officeId, gl.feeExpense(), gl.deferredFee(), fee);
        return legs;
    }

    /** What a deal is carried at, split by account, when it is terminated. */
    public record Carrying(BigDecimal principal, BigDecimal unamortisedPremium, BigDecimal unamortisedDiscount, BigDecimal interest,
            BigDecimal deferredFee) {

        /** Asset deals: principal + premium - discount + accrued interest + deferred fee. */
        public BigDecimal assetTotal() {
            return this.principal.add(this.unamortisedPremium).subtract(this.unamortisedDiscount).add(this.interest).add(this.deferredFee);
        }

        /** Liability deals: principal + interest payable - deferred fee. */
        public BigDecimal liabilityTotal() {
            return this.principal.add(this.interest).subtract(this.deferredFee);
        }
    }

    /**
     * Early termination. Asset deals: Dr settlement account for the proceeds, Cr each part of the carrying amount, the
     * difference to realised gain (Cr) or loss (Dr). Liability deals (early repayment): Dr principal and interest
     * payable, Cr deferred fee, Cr settlement account for the amount paid, the difference to gain or loss. A break cost
     * is paid out on top: Dr realised loss / Cr settlement account.
     *
     * @return the legs; the realised result is gain positive, loss negative, break cost included
     */
    public static Termination termination(final InstrumentKind kind, final Carrying carrying, final BigDecimal proceeds,
            final BigDecimal breakCost, final Long officeId, final Gl gl) {
        final List<Leg> legs = new ArrayList<>();
        final BigDecimal result;
        if (kind.isAsset()) {
            add(legs, officeId, gl.settlement(), JournalEntryType.DEBIT, proceeds);
            add(legs, officeId, gl.principal(), JournalEntryType.CREDIT, carrying.principal());
            add(legs, officeId, gl.premium(), JournalEntryType.CREDIT, carrying.unamortisedPremium());
            add(legs, officeId, gl.discount(), JournalEntryType.DEBIT, carrying.unamortisedDiscount());
            add(legs, officeId, kind.interestMethod() == InterestMethod.DISCOUNT_YIELD ? gl.principal() : gl.interestReceivable(),
                    JournalEntryType.CREDIT, carrying.interest());
            add(legs, officeId, gl.deferredFee(), JournalEntryType.CREDIT, carrying.deferredFee());
            result = proceeds.subtract(carrying.assetTotal());
        } else {
            add(legs, officeId, gl.principal(), JournalEntryType.DEBIT, carrying.principal());
            add(legs, officeId, gl.interestPayable(), JournalEntryType.DEBIT, carrying.interest());
            add(legs, officeId, gl.deferredFee(), JournalEntryType.CREDIT, carrying.deferredFee());
            add(legs, officeId, gl.settlement(), JournalEntryType.CREDIT, proceeds);
            result = carrying.liabilityTotal().subtract(proceeds);
        }
        if (result.signum() > 0) {
            add(legs, officeId, gl.realisedGain(), JournalEntryType.CREDIT, result);
        } else if (result.signum() < 0) {
            add(legs, officeId, gl.realisedLoss(), JournalEntryType.DEBIT, result.negate());
        }
        final BigDecimal cost = breakCost == null ? BigDecimal.ZERO : breakCost;
        pair(legs, officeId, gl.realisedLoss(), gl.settlement(), cost);
        return new Termination(legs, result.subtract(cost));
    }

    public record Termination(List<Leg> legs, BigDecimal realisedResult) {
    }

    /** Dr {@code debit} / Cr {@code credit} for a positive amount, the other way round for a negative one. */
    private static void pair(final List<Leg> legs, final Long officeId, final Long debit, final Long credit, final BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            return;
        }
        if (amount.signum() > 0) {
            add(legs, officeId, debit, JournalEntryType.DEBIT, amount);
            add(legs, officeId, credit, JournalEntryType.CREDIT, amount);
        } else {
            add(legs, officeId, credit, JournalEntryType.DEBIT, amount.negate());
            add(legs, officeId, debit, JournalEntryType.CREDIT, amount.negate());
        }
    }

    private static void add(final List<Leg> legs, final Long officeId, final Long glAccountId, final JournalEntryType type,
            final BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            return;
        }
        if (glAccountId == null) {
            throw new IllegalStateException("No GL account for a " + type + " of " + amount.toPlainString());
        }
        legs.add(new Leg(officeId, glAccountId, type, amount));
    }
}
