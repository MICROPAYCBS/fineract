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
 * Currency positions on a date: per currency the GL assets and liabilities, the net position in the currency and in
 * base at MID, with transfers pending and unsettled deals as memo items; the single currency and aggregate (shorthand)
 * net open positions as a percentage of core capital.
 */
public record TreasuryFxPositionData(LocalDate asOfDate, String baseCurrency, Long officeId, BigDecimal coreCapital,
        LocalDate coreCapitalEffectiveDate, List<Currency> currencies, BigDecimal sumOfLongs, BigDecimal sumOfShorts,
        BigDecimal aggregateNop, BigDecimal aggregateNopPercent, String coreCapitalBasis, LocalDate coreCapitalAsOf) {

    public record Currency(String currencyCode, boolean base, BigDecimal assets, BigDecimal liabilities, BigDecimal netPosition,
            BigDecimal pendingIn, BigDecimal pendingOut, BigDecimal unsettledDeals, BigDecimal midRate, LocalDate rateDate,
            boolean rateStale, BigDecimal netBase, String side, BigDecimal nopPercent) {
    }
}
