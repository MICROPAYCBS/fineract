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

public enum CheckDigitAlgorithm {

    NONE(0, "checkDigitAlgorithm.none"), //
    LUHN(1, "checkDigitAlgorithm.luhn"), //
    MOD10(2, "checkDigitAlgorithm.mod10"), //
    MOD11(3, "checkDigitAlgorithm.mod11"); //

    private final Integer value;
    private final String code;

    CheckDigitAlgorithm(final Integer value, final String code) {
        this.value = value;
        this.code = code;
    }

    public Integer getValue() {
        return this.value;
    }

    public String getCode() {
        return this.code;
    }

    private static final Map<Integer, CheckDigitAlgorithm> intToEnumMap = new HashMap<>();

    static {
        for (final CheckDigitAlgorithm algorithm : CheckDigitAlgorithm.values()) {
            intToEnumMap.put(algorithm.value, algorithm);
        }
    }

    public static CheckDigitAlgorithm fromInt(final Integer value) {
        if (value == null) {
            return NONE;
        }
        final CheckDigitAlgorithm algorithm = intToEnumMap.get(value);
        return algorithm != null ? algorithm : NONE;
    }
}
