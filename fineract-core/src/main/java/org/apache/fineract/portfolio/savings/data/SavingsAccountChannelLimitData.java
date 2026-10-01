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
package org.apache.fineract.portfolio.savings.data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public final class SavingsAccountChannelLimitData implements Serializable {

    private final Long id;
    private final Long savingsAccountId;
    private final Long productPaymentChannelId;
    private final Long paymentTypeId;
    private final String direction;
    private final BigDecimal ceilingPerTxn;
    private final BigDecimal ceilingPerDay;
    private final BigDecimal ceilingPerMonth;
    private final Integer ceilingCountPerDay;
    private final Integer ceilingCountPerMonth;
    private final BigDecimal maxPerTxn;
    private final BigDecimal maxPerDay;
    private final BigDecimal maxPerMonth;
    private final Integer maxCountPerDay;
    private final Integer maxCountPerMonth;
    private final BigDecimal pendingMaxPerTxn;
    private final BigDecimal pendingMaxPerDay;
    private final BigDecimal pendingMaxPerMonth;
    private final Integer pendingMaxCountPerDay;
    private final Integer pendingMaxCountPerMonth;
    private final OffsetDateTime pendingEffectiveOn;
    private final BigDecimal effectivePerTxn;
    private final BigDecimal effectivePerDay;
    private final BigDecimal effectivePerMonth;
    private final Integer effectiveCountPerDay;
    private final Integer effectiveCountPerMonth;
    private final BigDecimal usedToday;
    private final Integer countToday;
    private final BigDecimal usedThisMonth;
    private final Integer countThisMonth;
    private final BigDecimal remainingToday;
    private final BigDecimal remainingThisMonth;
    private final Integer remainingCountToday;
    private final Integer remainingCountThisMonth;
}
