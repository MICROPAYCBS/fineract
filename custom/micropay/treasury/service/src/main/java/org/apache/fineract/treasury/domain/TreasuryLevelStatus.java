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

import java.math.BigDecimal;

/**
 * A balance against the minimum and maximum set on its treasury account. Both bounds are inclusive and either may be
 * left out.
 */
public enum TreasuryLevelStatus {

    OK, //
    BELOW_MIN, //
    ABOVE_MAX; //

    public static TreasuryLevelStatus of(final BigDecimal balance, final BigDecimal minBalance, final BigDecimal maxBalance) {
        final BigDecimal value = balance == null ? BigDecimal.ZERO : balance;
        if (minBalance != null && value.compareTo(minBalance) < 0) {
            return BELOW_MIN;
        }
        if (maxBalance != null && value.compareTo(maxBalance) > 0) {
            return ABOVE_MAX;
        }
        return OK;
    }

    public boolean isBreach() {
        return this != OK;
    }
}
