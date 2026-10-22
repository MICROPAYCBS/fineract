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
package org.apache.fineract.treasury.liquidity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A dated cash flow from a ladder source, signed: inflows positive, outflows negative. {@code date} null means on
 * demand (the first bucket); a date on or before the as-of date also falls in the first bucket. A flow is either
 * contractual or behavioural, the adjustment an assumption makes.
 */
public record LadderFlow(LadderSourceCode source, String lineCode, Long productId, String productName, LocalDate date, BigDecimal amount,
        boolean behavioural) {

    public static LadderFlow contractual(final LadderSourceCode source, final String lineCode, final Long productId,
            final String productName, final LocalDate date, final BigDecimal amount) {
        return new LadderFlow(source, lineCode, productId, productName, date, amount, false);
    }

    public static LadderFlow behavioural(final LadderSourceCode source, final String lineCode, final Long productId,
            final String productName, final LocalDate date, final BigDecimal amount) {
        return new LadderFlow(source, lineCode, productId, productName, date, amount, true);
    }
}
