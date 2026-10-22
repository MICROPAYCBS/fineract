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
package org.apache.fineract.treasury.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.api.TreasuryDealApiConstants;
import org.apache.fineract.treasury.deal.AmortisationMethod;
import org.apache.fineract.treasury.deal.FeeMethod;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator.Request;
import org.junit.jupiter.api.Test;

class TreasuryDealDataValidatorTest {

    private final TreasuryDealDataValidator validator = new TreasuryDealDataValidator(new FromJsonHelper());

    @Test
    void dealTypeCarriesTheFeeAndTerminationAccounts() {
        final Request request = this.validator.validateDealType("""
                {"code": "mm", "name": "Placement", "instrumentKind": "money_market_placement", "principalGlAccountId": 1,
                 "interestReceivableGlAccountId": 2, "interestIncomeGlAccountId": 3, "feeExpenseGlAccountId": 4,
                 "feeMethod": "amortise", "amortisationMethod": "straight_line", "deferredFeeGlAccountId": 5,
                 "realisedGainGlAccountId": 6, "realisedLossGlAccountId": 7}
                """, true);

        assertEquals(5L, request.<Long>get(TreasuryDealApiConstants.DEFERRED_FEE_GL_PARAM));
        assertEquals(6L, request.<Long>get(TreasuryDealApiConstants.REALISED_GAIN_GL_PARAM));
        assertEquals(7L, request.<Long>get(TreasuryDealApiConstants.REALISED_LOSS_GL_PARAM));
        assertEquals(FeeMethod.AMORTISE, request.get(TreasuryDealApiConstants.FEE_METHOD_PARAM));
        assertEquals(AmortisationMethod.STRAIGHT_LINE, request.get(TreasuryDealApiConstants.AMORTISATION_METHOD_PARAM));
        assertTrue(request.has(TreasuryDealApiConstants.REALISED_LOSS_GL_PARAM));
    }

    @Test
    void dealMayBeCapturedPreApprovedForRollover() {
        final Request request = this.validator.validateDeal("""
                {"dealTypeId": 1, "counterpartyId": 2, "settlementAccountId": 3, "tradeDate": "2026-07-18",
                 "valueDate": "2026-07-18", "maturityDate": "2026-10-16", "amount": 500000000, "rate": 12,
                 "rolloverPreApproved": true, "dateFormat": "yyyy-MM-dd", "locale": "en"}
                """, true);

        assertEquals(Boolean.TRUE, request.get(TreasuryDealApiConstants.ROLLOVER_PRE_APPROVED_PARAM));
    }
}
