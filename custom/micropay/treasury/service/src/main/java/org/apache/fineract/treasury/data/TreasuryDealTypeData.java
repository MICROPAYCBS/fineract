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
package org.apache.fineract.treasury.data;

import org.apache.fineract.treasury.domain.TreasuryDealType;

public record TreasuryDealTypeData(Long id, String code, String name, String instrumentKind, String direction, String interestMethod,
        String defaultDayCount, int defaultFrequency, Long principalGlAccountId, Long interestReceivableGlAccountId,
        Long interestPayableGlAccountId, Long interestIncomeGlAccountId, Long interestExpenseGlAccountId, Long premiumGlAccountId,
        Long discountGlAccountId, Long feeIncomeGlAccountId, Long feeExpenseGlAccountId, boolean active, String feeMethod,
        String amortisationMethod, Long deferredFeeGlAccountId, Long realisedGainGlAccountId, Long realisedLossGlAccountId) {

    public static TreasuryDealTypeData from(final TreasuryDealType type) {
        return new TreasuryDealTypeData(type.getId(), type.getCode(), type.getName(), type.getInstrumentKind().name(),
                type.getDirection().name(), type.getInterestMethod().name(), type.getDefaultDayCount().name(), type.getDefaultFrequency(),
                type.getPrincipalGlAccountId(), type.getInterestReceivableGlAccountId(), type.getInterestPayableGlAccountId(),
                type.getInterestIncomeGlAccountId(), type.getInterestExpenseGlAccountId(), type.getPremiumGlAccountId(),
                type.getDiscountGlAccountId(), type.getFeeIncomeGlAccountId(), type.getFeeExpenseGlAccountId(), type.isActive(),
                type.getFeeMethod().name(), type.getAmortisationMethod().name(), type.getDeferredFeeGlAccountId(),
                type.getRealisedGainGlAccountId(), type.getRealisedLossGlAccountId());
    }
}
