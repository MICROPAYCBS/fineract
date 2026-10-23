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
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;

/**
 * Journal lines of a holding's fair value adjustment and loss allowance. Changes of either sign are accepted and posted
 * the other way round when negative.
 *
 * <ul>
 * <li>FVTPL revaluation: a rise Dr fair value adjustment / Cr fair value gain; a fall Dr fair value loss / Cr fair
 * value adjustment.</li>
 * <li>FVOCI revaluation: Dr fair value adjustment / Cr OCI reserve (the other way round for a fall).</li>
 * <li>Expected credit loss: Dr impairment expense / Cr loss allowance (amortised cost) or Cr OCI reserve (FVOCI, whose
 * carrying amount stays at fair value, IFRS 9 5.5.2); a release the other way round.</li>
 * </ul>
 */
public final class DealValuationPlanner {

    private DealValuationPlanner() {}

    public record ValuationGl(Long adjustment, Long gain, Long loss, Long reserve, Long impairment, Long allowance, Long realisedGain,
            Long realisedLoss) {
    }

    public static List<Leg> revaluation(final DealClassification classification, final BigDecimal change, final Long officeId,
            final ValuationGl gl) {
        final List<Leg> legs = new ArrayList<>();
        if (change.signum() == 0) {
            return legs;
        }
        if (classification == DealClassification.FVTPL) {
            if (change.signum() > 0) {
                pair(legs, officeId, gl.adjustment(), gl.gain(), change);
            } else {
                pair(legs, officeId, gl.loss(), gl.adjustment(), change.negate());
            }
        } else if (classification == DealClassification.FVOCI) {
            pair(legs, officeId, gl.adjustment(), gl.reserve(), change);
        } else {
            throw new IllegalArgumentException(classification + " holdings are not revalued");
        }
        return legs;
    }

    public static List<Leg> impairment(final DealClassification classification, final BigDecimal change, final Long officeId,
            final ValuationGl gl) {
        final List<Leg> legs = new ArrayList<>();
        if (classification == DealClassification.FVTPL) {
            throw new IllegalArgumentException("FVTPL holdings carry no loss allowance");
        }
        pair(legs, officeId, gl.impairment(), classification == DealClassification.FVOCI ? gl.reserve() : gl.allowance(), change);
        return legs;
    }

    /**
     * The fair value adjustment taken off the books with the holding (termination or maturity). FVTPL: the adjustment,
     * already in profit or loss, is reversed against realised gain or loss, so that the realised result is measured
     * from fair value. FVOCI: the adjustment is reversed against the OCI reserve; the amount leaves OCI and is, in
     * effect, recycled to profit or loss through the realised result, which is measured from amortised cost.
     *
     * @param adjustment
     *            fair value less amortised cost as posted
     */
    public static Release release(final DealClassification classification, final BigDecimal adjustment, final Long officeId,
            final ValuationGl gl) {
        final List<Leg> legs = new ArrayList<>();
        if (adjustment == null || adjustment.signum() == 0) {
            return new Release(legs, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        if (classification == DealClassification.FVTPL) {
            if (adjustment.signum() > 0) {
                pair(legs, officeId, gl.realisedLoss(), gl.adjustment(), adjustment);
            } else {
                pair(legs, officeId, gl.adjustment(), gl.realisedGain(), adjustment.negate());
            }
            return new Release(legs, adjustment.negate(), BigDecimal.ZERO);
        }
        pair(legs, officeId, gl.reserve(), gl.adjustment(), adjustment);
        return new Release(legs, BigDecimal.ZERO, adjustment);
    }

    /**
     * @param realised
     *            change of the realised result: FVTPL minus the adjustment; FVOCI zero
     * @param ociRecycled
     *            FVOCI: the fair value gain (positive) or loss taken out of OCI
     */
    public record Release(List<Leg> legs, BigDecimal realised, BigDecimal ociRecycled) {
    }

    /** Dr {@code debit} / Cr {@code credit} for a positive amount, the other way round for a negative one. */
    private static void pair(final List<Leg> legs, final Long officeId, final Long debit, final Long credit, final BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            return;
        }
        final BigDecimal value = amount.abs();
        add(legs, officeId, amount.signum() > 0 ? debit : credit, JournalEntryType.DEBIT, value);
        add(legs, officeId, amount.signum() > 0 ? credit : debit, JournalEntryType.CREDIT, value);
    }

    private static void add(final List<Leg> legs, final Long officeId, final Long glAccountId, final JournalEntryType type,
            final BigDecimal amount) {
        if (glAccountId == null) {
            throw new TreasuryWorkflowException("deal", "gl.account.missing",
                    "No GL account for a " + type + " of " + amount.toPlainString() + ": set it on the deal type", type.name(), amount);
        }
        legs.add(new Leg(officeId, glAccountId, type, amount));
    }
}
