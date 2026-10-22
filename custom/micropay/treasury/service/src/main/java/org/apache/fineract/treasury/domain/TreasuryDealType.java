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
package org.apache.fineract.treasury.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.treasury.deal.AmortisationMethod;
import org.apache.fineract.treasury.deal.DayCount;
import org.apache.fineract.treasury.deal.DealDirection;
import org.apache.fineract.treasury.deal.FeeMethod;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.deal.InterestMethod;

/**
 * A kind of deal with its conventions and GL accounts, for example "91-day T-bill" or "Interbank placement".
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_deal_type")
public class TreasuryDealType extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_kind", nullable = false, length = 40)
    private InstrumentKind instrumentKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 20)
    private DealDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", nullable = false, length = 20)
    private InterestMethod interestMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_day_count", nullable = false, length = 20)
    private DayCount defaultDayCount;

    /** Payments a year (1, 2, 4, 12), 0 for at maturity. */
    @Column(name = "default_frequency", nullable = false)
    private int defaultFrequency;

    @Column(name = "principal_gl_account_id", nullable = false)
    private Long principalGlAccountId;

    @Column(name = "interest_receivable_gl_account_id")
    private Long interestReceivableGlAccountId;

    @Column(name = "interest_payable_gl_account_id")
    private Long interestPayableGlAccountId;

    @Column(name = "interest_income_gl_account_id")
    private Long interestIncomeGlAccountId;

    @Column(name = "interest_expense_gl_account_id")
    private Long interestExpenseGlAccountId;

    @Column(name = "premium_gl_account_id")
    private Long premiumGlAccountId;

    @Column(name = "discount_gl_account_id")
    private Long discountGlAccountId;

    @Column(name = "fee_income_gl_account_id")
    private Long feeIncomeGlAccountId;

    @Column(name = "fee_expense_gl_account_id")
    private Long feeExpenseGlAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_method", nullable = false, length = 30)
    private FeeMethod feeMethod = FeeMethod.EXPENSE_IMMEDIATELY;

    /** How bond premiums and discounts, and deferred fees, are amortised. */
    @Enumerated(EnumType.STRING)
    @Column(name = "amortisation_method", nullable = false, length = 30)
    private AmortisationMethod amortisationMethod = AmortisationMethod.EFFECTIVE_INTEREST;

    /** Asset account holding fees deferred under {@link FeeMethod#AMORTISE}. */
    @Column(name = "deferred_fee_gl_account_id")
    private Long deferredFeeGlAccountId;

    @Column(name = "realised_gain_gl_account_id")
    private Long realisedGainGlAccountId;

    @Column(name = "realised_loss_gl_account_id")
    private Long realisedLossGlAccountId;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
