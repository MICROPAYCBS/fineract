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
package org.apache.fineract.portfolio.savings.domain;

import java.util.HashMap;
import java.util.Map;

public enum SavingsPaymentChannelStatus {

    INVALID(0, "savingsPaymentChannelStatus.invalid"), //
    ACTIVE(100, "savingsPaymentChannelStatus.active"), //
    INACTIVE(200, "savingsPaymentChannelStatus.inactive");

    private final Integer value;
    private final String code;

    private static final Map<Integer, SavingsPaymentChannelStatus> intToEnumMap = new HashMap<>();

    static {
        for (final SavingsPaymentChannelStatus type : SavingsPaymentChannelStatus.values()) {
            intToEnumMap.put(type.value, type);
        }
    }

    SavingsPaymentChannelStatus(final Integer value, final String code) {
        this.value = value;
        this.code = code;
    }

    public Integer getValue() {
        return this.value;
    }

    public String getCode() {
        return this.code;
    }

    public static SavingsPaymentChannelStatus fromInt(final Integer statusValue) {
        if (statusValue == null) {
            return INVALID;
        }
        final SavingsPaymentChannelStatus type = intToEnumMap.get(statusValue);
        return type == null ? INVALID : type;
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isInactive() {
        return this == INACTIVE;
    }
}
