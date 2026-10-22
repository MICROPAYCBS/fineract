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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.treasury.deal.DealCalculator.BondPrice;
import org.apache.fineract.treasury.deal.DealCalculator.Flow;

/**
 * Consideration, settlement split and cash flow schedule of a deal from its terms.
 */
public final class DealPricer {

    private DealPricer() {}

    /**
     * @param amount
     *            principal for placements, deposits and borrowings; face value for bills and bonds
     * @param rate
     *            interest rate, discount yield (bills) or coupon rate (bonds), percent per year
     * @param cleanPrice
     *            bonds only, per 100 of face
     * @param frequency
     *            payments a year; 0 is at maturity (not for bonds)
     * @param issueDate
     *            bonds only, optional; a later issue date than the regular coupon date makes a short first coupon
     */
    public record Terms(InstrumentKind kind, BigDecimal amount, BigDecimal rate, BigDecimal cleanPrice, DayCount dayCount, int frequency,
            boolean amortising, LocalDate valueDate, LocalDate maturityDate, LocalDate issueDate, int scale) {
    }

    /**
     * @param consideration
     *            cash paid (asset deals) or received (liability deals) at settlement, before fees
     * @param principalAmount
     *            carried in the principal GL account: the consideration, but face value for bonds
     * @param premium
     *            bonds: clean amount above face
     * @param discount
     *            bonds: face above clean amount
     * @param accruedInterest
     *            bonds: coupon accrued before the value date, bought with the bond
     * @param exposureAmount
     *            what counts towards the counterparty limit: principal, or face for bills and bonds
     */
    public record Priced(BigDecimal consideration, BigDecimal principalAmount, BigDecimal premium, BigDecimal discount,
            BigDecimal accruedInterest, BigDecimal exposureAmount, LocalDate accrualStart, List<Flow> flows) {
    }

    public static Priced price(final Terms terms) {
        final int scale = terms.scale();
        final BigDecimal zero = BigDecimal.ZERO.setScale(scale);
        final BigDecimal amount = terms.amount().setScale(scale, RoundingMode.HALF_UP);
        return switch (terms.kind()) {
            case TREASURY_BILL -> {
                final BigDecimal consideration = DealCalculator.treasuryBillConsideration(terms.amount(), terms.rate(), terms.valueDate(),
                        terms.maturityDate(), terms.dayCount(), scale);
                yield new Priced(consideration, consideration, zero, zero, zero, amount, terms.valueDate(),
                        DealCalculator.treasuryBillSchedule(terms.amount(), terms.valueDate(), terms.maturityDate(), scale));
            }
            case GOVERNMENT_BOND -> {
                final BondPrice price = DealCalculator.bondPrice(terms.amount(), terms.cleanPrice(), terms.rate(), terms.frequency(),
                        terms.dayCount(), terms.valueDate(), terms.maturityDate(), terms.issueDate(), scale);
                final BigDecimal difference = price.cleanAmount().subtract(amount);
                yield new Priced(price.consideration(), amount, difference.signum() > 0 ? difference : zero,
                        difference.signum() < 0 ? difference.negate() : zero, price.accruedInterest(), amount, price.accrualStart(),
                        DealCalculator.bondSchedule(terms.amount(), terms.rate(), terms.frequency(), terms.dayCount(), terms.valueDate(),
                                terms.maturityDate(), terms.issueDate(), scale));
            }
            default -> new Priced(amount, amount, zero, zero, zero, amount, terms.valueDate(), DealCalculator.simpleSchedule(terms.amount(),
                    terms.rate(), terms.frequency(), terms.amortising(), terms.dayCount(), terms.valueDate(), terms.maturityDate(), scale));
        };
    }
}
