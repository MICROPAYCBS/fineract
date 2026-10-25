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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A deal as it would be captured, without saving it: its pricing, its cash flows and what it would do to the
 * counterparty's limit. A deal the capture would refuse (bad terms, inactive type or counterparty) is refused the same
 * way; one above the limit is shown with its outcome rather than refused.
 */
public record TreasuryDealPreviewData(String currencyCode, Long officeId, String dayCount, int frequency, BigDecimal consideration,
        BigDecimal accruedInterestPurchased, BigDecimal premium, BigDecimal discount, BigDecimal exposureAmount, List<Cashflow> cashflows,
        Limit limit) {

    public record Cashflow(int sequence, String flowType, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate, BigDecimal amount,
            BigDecimal principalOutstanding) {
    }

    /**
     * The counterparty limit check of an asset deal (null for a liability): OK, WARNING or EXCEEDED, with the exposure
     * after the deal; referralAllowed says whether a deal above the limit would be held for an over-limit approval
     * rather than refused.
     */
    public record Limit(String outcome, BigDecimal exposureAfter, BigDecimal utilisation, BigDecimal available, BigDecimal exposureLimit,
            BigDecimal warningPercent, boolean referralAllowed) {
    }
}
