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
import org.apache.fineract.treasury.domain.TreasuryCounterparty;

public record TreasuryCounterpartyData(Long id, String name, String shortCode, String counterpartyType, String rating,
        Long ratingCodeValueId, BigDecimal exposureLimit, String limitCurrencyCode, BigDecimal warningPercent, String status) {

    public static TreasuryCounterpartyData from(final TreasuryCounterparty counterparty) {
        return new TreasuryCounterpartyData(counterparty.getId(), counterparty.getName(), counterparty.getShortCode(),
                counterparty.getCounterpartyType().name(), counterparty.getRating(), counterparty.getRatingCodeValueId(),
                counterparty.getExposureLimit(), counterparty.getLimitCurrencyCode(), counterparty.getWarningPercent(),
                counterparty.getStatus().name());
    }
}
