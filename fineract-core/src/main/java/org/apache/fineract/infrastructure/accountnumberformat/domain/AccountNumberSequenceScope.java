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
package org.apache.fineract.infrastructure.accountnumberformat.domain;

import java.util.HashMap;
import java.util.Map;

public enum AccountNumberSequenceScope {

    GLOBAL(1, "accountNumberSequenceScope.global"), //
    OFFICE(2, "accountNumberSequenceScope.office"), //
    OFFICE_PRODUCT(3, "accountNumberSequenceScope.officeProduct"); //

    private final Integer value;
    private final String code;

    AccountNumberSequenceScope(final Integer value, final String code) {
        this.value = value;
        this.code = code;
    }

    public Integer getValue() {
        return this.value;
    }

    public String getCode() {
        return this.code;
    }

    private static final Map<Integer, AccountNumberSequenceScope> intToEnumMap = new HashMap<>();

    static {
        for (final AccountNumberSequenceScope scope : AccountNumberSequenceScope.values()) {
            intToEnumMap.put(scope.value, scope);
        }
    }

    public static AccountNumberSequenceScope fromInt(final Integer value) {
        if (value == null) {
            return null;
        }
        return intToEnumMap.get(value);
    }
}
